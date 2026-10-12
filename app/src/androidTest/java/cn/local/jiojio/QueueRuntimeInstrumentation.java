package cn.local.jiojio;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import com.autojump.app.automation.*;
import com.autojump.app.bean.Rule;
import com.autojump.app.data.RuleRepository;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.domain.RuleCondition;
import java.util.*;

/** Isolated emulator tests of actual persistence + coordinator; never submits attendance. */
public final class QueueRuntimeInstrumentation extends Instrumentation {
    private Context context;
    private RuleRepository rules;
    private PendingExecutionStore sessions;
    private ExecutionLogStore logs;
    private int checks;
    private int skipped;
    private final StringBuilder report = new StringBuilder();
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_OK;
        context = getTargetContext(); rules = new RuleRepository(context);
        sessions = new PendingExecutionStore(context); logs = new ExecutionLogStore(context);
        boolean ownsQueue=false;
        android.content.SharedPreferences runtime=context.getSharedPreferences("automation_execution_sp",Context.MODE_PRIVATE);
        String previousLogs=runtime.getString("execution_logs",null);
        long previousSequence=runtime.getLong("queue_sequence",0);
        android.content.SharedPreferences config=context.getSharedPreferences("jump_config_sp",Context.MODE_PRIVATE);
        String previousRules=config.getString("rule_list",null);
        try {
            check((context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0,
                    "test runner requires debug target; never run against production APK");
            List<ExecutionSession> leftovers=sessions.getAll();
            if(!leftovers.isEmpty() && leftovers.stream().allMatch(s -> s.getRuleId()!=null
                    && s.getRuleId().startsWith("runtime-test-"))) {
                main(() -> { QueueCoordinator.stopAll(context); sessions.clear(); });
                report.append("Cleaned only test-owned sessions from interrupted prior run\n");
            }
            check(sessions.getAll().isEmpty(), "requires isolated empty runtime queue");
            ownsQueue=true;
            fifoAndDedup(); cancellation(); failureContinues(); latestOnDequeue();
            latestConditionOnDequeue(); capacityAndStopAll();
            conditionSkip(); unavailableWifi(); interruptedRecovery(); expiry();
            result.putString("stream", "PASS " + checks + " checks\n" + report);
        } catch (Throwable error) {
            code = Activity.RESULT_CANCELED;
            result.putString("stream", "FAIL after " + checks + " checks\n" + report
                    + android.util.Log.getStackTraceString(error));
        } finally {
            if(ownsQueue) {
                main(() -> { QueueCoordinator.stopAll(context); sessions.clear();
                    android.content.SharedPreferences.Editor edit=runtime.edit().putLong("queue_sequence",previousSequence);
                    if(previousLogs==null)edit.remove("execution_logs");else edit.putString("execution_logs",previousLogs);
                    edit.commit();
                    android.content.SharedPreferences.Editor restore=config.edit();
                    if(previousRules==null)restore.remove("rule_list");else restore.putString("rule_list",previousRules);
                    restore.commit();
                });
            }
        }
        result.putInt("checks_passed", checks);result.putInt("checks_skipped",skipped); finish(code, result);
    }
    private Rule waitRule(String id, long delay) {
        Rule r = new Rule("runtime-test-" + id, id); r.getActions().add(RuleAction.waitFor(delay)); return r;
    }
    private void setup(Rule... list) {
        main(() -> { QueueCoordinator.stopAll(context); sessions.clear(); logs.clear(); rules.saveRules(Arrays.asList(list)); });
    }
    private ExecutionSession enqueue(Rule r) {
        ExecutionSession[] value = new ExecutionSession[1];
        main(() -> value[0] = QueueCoordinator.enqueue(context,r)); return value[0];
    }
    private void fifoAndDedup() {
        // Software-rendered emulators can spend seconds in initial class loading
        // and persistence. Hold A explicitly until all dedup assertions finish.
        Rule a=waitRule("a",60_000), b=waitRule("b",500), c=waitRule("c",100); setup(a,b,c);
        ExecutionSession sa=enqueue(a), sb=enqueue(b), sc=enqueue(c);
        check(enqueue(a).getId().equals(sa.getId()), "running ID dedup");
        check(enqueue(b).getId().equals(sb.getId()), "queued ID dedup");
        check(sessions.findById(sb.getId()).getStatus()==ExecutionSession.Status.QUEUED, "B queued while A waiting");
        main(() -> {
            ExecutionSession held=sessions.findById(sa.getId());
            held.setNotBefore(System.currentTimeMillis()); sessions.upsert(held); QueueCoordinator.pump(context);
        });
        drained();
        List<String> start = new ArrayList<>();
        for (ExecutionLogStore.Entry e : logs.getAll()) if ("STARTED".equals(e.getStatus())) start.add(e.getRuleId());
        Collections.reverse(start); check(start.equals(Arrays.asList(a.getId(),b.getId(),c.getId())), "FIFO starts A B C");
        check(has(sc,"COMPLETED"), "final WAIT completes and releases slot");
    }
    private void cancellation() {
        Rule a=waitRule("cancel-a",4000), b=waitRule("cancel-b",100), c=waitRule("cancel-c",100); setup(a,b,c);
        ExecutionSession sa=enqueue(a), sb=enqueue(b), sc=enqueue(c);
        main(() -> QueueCoordinator.cancel(context,sb.getId()));
        check(has(sb,"CANCELLED") && !has(sb,"STARTED"), "queued cancellation never starts B");
        main(() -> QueueCoordinator.cancel(context,sa.getId())); drained();
        check(has(sc,"COMPLETED"), "active WAIT cancellation releases C");
    }
    private void failureContinues() {
        Rule a=waitRule("missing-handler",100), b=waitRule("after-failure",100);
        a.getActions().clear(); a.getActions().add(RuleAction.deeplink("toolkit-test-no-handler-92641://missing"));
        setup(a,b); ExecutionSession sa=enqueue(a), sb=enqueue(b); drained();
        check(has(sa,"FAILED"), "missing deeplink handler fails");
        check(has(sb,"COMPLETED"), "failure does not block next task");
    }
    private void latestOnDequeue() {
        Rule a=waitRule("edit-a",900), b=waitRule("edit-b",100); setup(a,b);
        enqueue(a); ExecutionSession sb=enqueue(b); b.setEnabled(false); main(() -> rules.saveRules(Arrays.asList(a,b)));
        drained(); check(has(sb,"SKIPPED") && !has(sb,"STARTED"), "disabled while queued skips");
    }
    private void conditionSkip() {
        Rule a=waitRule("condition",100), b=waitRule("after-condition",100);
        int today=Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        int iso=today==Calendar.SUNDAY?7:today-1;
        a.getConditions().add(RuleCondition.time(new int[]{iso%7+1},0,0,23,59));
        setup(a,b); ExecutionSession sa=enqueue(a), sb=enqueue(b); drained();
        check(has(sa,"SKIPPED") && !has(sa,"STARTED"), "unmet time condition skips without actions");
        check(has(sb,"COMPLETED"), "condition skip advances queue");
    }
    private void unavailableWifi() {
        if(context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED) {
            skipped++;report.append("SKIP missing-WiFi-permission case: permission already granted\n");return;
        }
        Rule a=waitRule("wifi",100); a.getConditions().add(RuleCondition.wifi("runtime-test-nonexistent-ssid"));
        setup(a); ExecutionSession sa=enqueue(a); drained();
        check(has(sa,"SKIPPED") && logs.getAll().get(0).getMessage().startsWith("条件无法检查"), "missing WiFi permission distinguished from mismatch");
    }
    private void interruptedRecovery() {
        Rule a=waitRule("recovery-a",4000), b=waitRule("recovery-b",100); setup(a,b);
        ExecutionSession sa=enqueue(a), sb=enqueue(b); main(() -> QueueCoordinator.recover(context)); drained();
        check(has(sa,"FAILED") && !has(sa,"COMPLETED"), "interrupted active execution not replayed");
        check(has(sb,"COMPLETED"), "unstarted queue restored");
    }
    private void latestConditionOnDequeue() {
        Rule a=waitRule("change-condition-a",900), b=waitRule("change-condition-b",100),c=waitRule("change-condition-c",100);
        setup(a,b,c);enqueue(a);ExecutionSession sb=enqueue(b),sc=enqueue(c);
        int today=Calendar.getInstance().get(Calendar.DAY_OF_WEEK),iso=today==Calendar.SUNDAY?7:today-1;
        b.getConditions().add(RuleCondition.time(new int[]{iso%7+1},0,0,23,59));
        main(() -> rules.saveRules(Arrays.asList(a,b,c)));drained();
        check(has(sb,"SKIPPED") && !has(sb,"STARTED"),"queued task checks newly changed condition at dequeue");
        check(has(sc,"COMPLETED"),"changed condition does not block following task");
    }
    private void capacityAndStopAll() {
        Rule[] list=new Rule[21];for(int i=0;i<list.length;i++)list[i]=waitRule("capacity-"+i,60_000);
        setup(list);java.util.ArrayList<ExecutionSession> accepted=new java.util.ArrayList<>();
        main(() -> {
            for(int i=0;i<20;i++)accepted.add(QueueCoordinator.enqueue(context,list[i]));
            check(QueueCoordinator.enqueue(context,list[20])==null,"capacity 20 rejects excess without deleting tasks");
            check(QueuePolicy.ordered(sessions.getAll()).size()==20,"capacity preserves accepted queue");
            QueueCoordinator.stopAll(context);
        });
        drained();check(has(accepted.get(0),"CANCELLED") && has(accepted.get(19),"CANCELLED"),"stop all cancels active WAIT and queued tasks");
    }
    private void expiry() {
        Rule a=waitRule("expiry",4000), b=waitRule("after-expiry",100); setup(a,b);
        ExecutionSession sa=enqueue(a), sb=enqueue(b);
        main(() -> { ExecutionSession fresh=sessions.findById(sa.getId());
            fresh.setElapsedDeadline(SystemClock.elapsedRealtime()-1, fresh.getDeadlineBoot());
            fresh.setExpiresAt(System.currentTimeMillis()-1); sessions.upsert(fresh); QueueCoordinator.pump(context); });
        drained(); check(has(sa,"FAILED") && has(sb,"COMPLETED"), "timeout releases next task");
    }
    private boolean has(ExecutionSession s,String status) {
        for (ExecutionLogStore.Entry e:logs.getAll()) if (s.getId().equals(e.getSessionId()) && status.equals(e.getStatus())) return true;
        return false;
    }
    private void drained() {
        long until=SystemClock.elapsedRealtime()+8000;
        while (!QueuePolicy.ordered(sessions.getAll()).isEmpty() && SystemClock.elapsedRealtime()<until) SystemClock.sleep(50);
        check(QueuePolicy.ordered(sessions.getAll()).isEmpty(), "queue drained");
    }
    private void main(Runnable r) {
        // Assertions on Android's main thread must reach the runner's catch/finally,
        // not kill the process before test-owned configuration is restored.
        Throwable[] failure=new Throwable[1];
        runOnMainSync(() -> { try { r.run(); } catch(Throwable error) { failure[0]=error; } });
        if(failure[0]!=null) throw new AssertionError("main-thread test failed",failure[0]);
    }
    private void check(boolean ok,String name) {
        if (!ok) throw new AssertionError(name); checks++; report.append(name).append("\n");
    }
}
