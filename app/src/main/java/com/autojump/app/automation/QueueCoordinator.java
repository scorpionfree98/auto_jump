package com.autojump.app.automation;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.autojump.app.bean.Rule;
import com.autojump.app.data.RuleRepository;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleCondition;
import com.autojump.app.engine.ConditionEvaluator;
import com.autojump.app.engine.ConditionFailureText;
import com.autojump.app.engine.RuleConditionPolicy;
import java.util.Calendar;
import java.util.List;

/** All launcher, UI, and service entries share one foreground execution slot. */
public final class QueueCoordinator {
    private static final Object LOCK = new Object();
    private static final Handler WATCHDOG = new Handler(Looper.getMainLooper());
    private static Runnable tick;

    public static ExecutionSession enqueue(Context context, Rule rule) {
        return enqueue(context, rule, true);
    }
    static ExecutionSession enqueueDeferred(Context context, Rule rule) {
        return enqueue(context, rule, false);
    }
    private static ExecutionSession enqueue(Context context, Rule rule, boolean dispatch) {
        synchronized (LOCK) {
            PendingExecutionStore store = new PendingExecutionStore(context);
            long now = System.currentTimeMillis();
            for (ExecutionSession old : store.getAll()) if (expired(context, old)) {
                halt(context, old, "FAILED", "任务已超时，请重新运行");
            }
            List<ExecutionSession> queue = QueuePolicy.ordered(store.getAll());
            for (ExecutionSession old : queue) if (rule.getId().equals(old.getRuleId())) {
                Toast.makeText(context, "指令已在运行或排队，本次触发已合并", Toast.LENGTH_SHORT).show();
                return old;
            }
            if (queue.size() >= QueuePolicy.CAPACITY) {
                Toast.makeText(context, "队列已满（20 条），请取消任务后重试", Toast.LENGTH_LONG).show();
                return null;
            }
            ExecutionSession session = ExecutionSession.fromRule(rule, now);
            session.setSequence(store.nextSequence());
            deadline(context, session, now, QueuePolicy.QUEUE_TIMEOUT_MS);
            session.setStatus(ExecutionSession.Status.QUEUED);
            store.upsert(session);
            new ExecutionLogStore(context).append(session, "QUEUED", "指令已排队");
            if (dispatch) pump(context);
            return session;
        }
    }
    public static void recover(Context context) {
        synchronized (LOCK) {
            for (ExecutionSession s : new PendingExecutionStore(context).getAll()) {
                if (QueuePolicy.live(s) && s.getStatus() != ExecutionSession.Status.QUEUED) {
                    terminal(context, s, "FAILED", "执行进程已中断，请重试；不会自动重放点击");
                }
            }
            pump(context);
        }
    }
    public static void pump(Context caller) {
        synchronized (LOCK) {
            Context context = caller instanceof android.app.Activity ? caller : AutoJumpAccessibilityService.connectedContext();
            if (context == null) context = caller;
            PendingExecutionStore store = new PendingExecutionStore(context);
            long now = System.currentTimeMillis();
            for (ExecutionSession s : QueuePolicy.ordered(store.getAll())) {
                if (s.isCancelRequested()) {
                    if (s.getCancellationElapsedAt() > 0 && android.os.SystemClock.elapsedRealtime()-s.getCancellationElapsedAt() >= 70_000L)
                        terminal(context,s,s.getCancellationStatus(),s.getCancellationReason()+"（手势回调超时）");
                    continue;
                }
                if (expired(context, s)) halt(context, s, "FAILED", "任务已超时，请重试");
                else if (s.getUnlockDeadline() > 0 && locked(context) && android.os.SystemClock.elapsedRealtime() >= s.getUnlockElapsedDeadline()) {
                    terminal(context, s, "FAILED", "等待解锁超过 2 分钟");
                }
            }
            List<ExecutionSession> ordered = QueuePolicy.ordered(store.getAll());
            ExecutionSession active = null;
            for (ExecutionSession s : ordered) if (s.getStatus() != ExecutionSession.Status.QUEUED) {
                active = s; break;
            }
            if (active == null && !ordered.isEmpty()) {
                ExecutionSession next = ordered.get(0);
                Rule latest = null;
                for (Rule rule : new RuleRepository(context).getRules()) {
                    if (next.getRuleId().equals(rule.getId())) { latest = rule; break; }
                }
                if (latest == null && AccessibilityClickSelfTest.RULE_ID.equals(next.getRuleId())) {
                    latest = new Rule(next.getRuleId(), next.getRuleName());
                    latest.setActions(next.getActions());
                }
                if (latest == null || !latest.isEnabled()) terminal(context, next, "SKIPPED", "指令已删除或停用");
                else {
                    String reason = conditionFailure(context, latest);
                    if (reason != null) terminal(context, next, "SKIPPED", reason);
                    else {
                        next.startFrom(latest, now);
                        deadline(context, next, now, QueuePolicy.executionBudget(latest.getActions()));
                        if (locked(context)) {
                            next.setUnlockDeadline(now + QueuePolicy.UNLOCK_TIMEOUT_MS);
                            next.setUnlockElapsedDeadline(android.os.SystemClock.elapsedRealtime() + QueuePolicy.UNLOCK_TIMEOUT_MS);
                        }
                        store.upsert(next);
                        new ExecutionLogStore(context).append(next, "STARTED", "轮到执行，条件检查通过");
                        new AutomationOrchestrator(context).startSession(context, next);
                        active = store.findById(next.getId());
                    }
                }
            }
            if (active != null && active.getStatus() != ExecutionSession.Status.RUNNING && !locked(context)
                    && active.getNotBefore() <= now) {
                ActionType type = active.currentAction() == null ? null : active.currentAction().getType();
                if (type == null || type == ActionType.DEEPLINK || type == ActionType.WAIT) {
                    new AutomationOrchestrator(context).continueUntilExternalEvent(context, active, now);
                } else if (AutoJumpAccessibilityService.connectedContext() == null) {
                    // System rebind after package update/cold start is asynchronous.
                    // Do not fail a newly dequeued click before onServiceConnected arrives.
                    if (!AccessibilityServiceState.isEnabled(context) || now - active.getUpdatedAt() >= 10_000L) {
                        terminal(context, active, "FAILED", "自动点击服务未连接，请开启后重新运行");
                    }
                } else {
                    // A warm target page may emit no new accessibility event.
                    // The execution queue must actively wake the click scheduler.
                    AutoJumpAccessibilityService.requestExecutionCheck(active.getId());
                }
            }
            if (tick != null) WATCHDOG.removeCallbacks(tick);
            if (!QueuePolicy.ordered(store.getAll()).isEmpty()) {
                Context app = caller.getApplicationContext();
                tick = () -> pump(app);
                WATCHDOG.postDelayed(tick, 500L);
            }
        }
    }
    public static void cancel(Context context, String id) {
        synchronized (LOCK) {
            ExecutionSession s = new PendingExecutionStore(context).findById(id);
            if (s != null && s.getStatus() == ExecutionSession.Status.RUNNING) {
                halt(context,s,"CANCELLED","用户取消了任务");
            } else if (s != null) terminal(context, s, "CANCELLED", "用户取消了任务");
            pump(context);
        }
    }
    public static void stopAll(Context context) {
        AccessibilityAwareRuleRunner.cancelWaiting();
        synchronized (LOCK) {
            for (ExecutionSession s : QueuePolicy.ordered(new PendingExecutionStore(context).getAll())) {
                if (s.getStatus() == ExecutionSession.Status.RUNNING) {
                    halt(context,s,"CANCELLED","用户停止了全部任务");
                } else terminal(context, s, "CANCELLED", "用户停止了全部任务");
            }
            if (tick != null) WATCHDOG.removeCallbacks(tick);
            pump(context);
        }
    }
    private static void halt(Context c, ExecutionSession s, String status, String message) {
        if (s.getStatus() != ExecutionSession.Status.RUNNING) { terminal(c,s,status,message); return; }
        if (!s.isCancelRequested()) {
            s.requestCancel(status,message,android.os.SystemClock.elapsedRealtime());
            new PendingExecutionStore(c).upsert(s);
            new ExecutionLogStore(c).append(s,"CANCELLING",message+"；等待当前手势结束，不发送后续点击");
        }
    }
    private static void terminal(Context c, ExecutionSession s, String status, String message) {
        new ExecutionLogStore(c).append(s, status, message);
        new PendingExecutionStore(c).remove(s.getId());
    }
    private static int boot(Context c) {
        return android.provider.Settings.Global.getInt(c.getContentResolver(), "boot_count", -1);
    }
    public static boolean expired(Context c, ExecutionSession s) {
        return QueuePolicy.expired(s, System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime(), boot(c));
    }
    private static void deadline(Context c, ExecutionSession s, long now, long duration) {
        s.setExpiresAt(now + duration);
        s.setElapsedDeadline(android.os.SystemClock.elapsedRealtime() + duration, boot(c));
    }
    private static boolean locked(Context c) {
        KeyguardManager manager = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        return manager != null && manager.isKeyguardLocked();
    }
    /** Shared by foreground preflight and the authoritative check when a queued rule starts. */
    public static String conditionFailure(Context c, Rule rule) {
        ConditionEvaluator evaluator = new ConditionEvaluator();
        for (RuleCondition condition : rule.getConditions()) {
            String reason = evaluator.unavailableReason(c, condition);
            if (reason != null) return "条件无法检查：" + reason;
        }
        if (RuleConditionPolicy.matches(rule.getConditions(), condition -> evaluator.matches(c, condition))) {
            return null;
        }
        for (RuleCondition condition : rule.getConditions()) {
            if (condition.getType() != com.autojump.app.domain.ConditionType.TIME
                    && !evaluator.matches(c, condition)) {
                return "条件不满足：\n" + evaluator.mismatchReason(c, condition)
                        + "\n因此本次未执行。";
            }
        }
        return ConditionFailureText.timeWindows(rule.getConditions(), Calendar.getInstance());
    }
}
