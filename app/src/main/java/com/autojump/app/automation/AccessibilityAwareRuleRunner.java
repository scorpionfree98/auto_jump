package com.autojump.app.automation;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.autojump.app.bean.Rule;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.shizuku.ShizukuAccessibilityRecovery;
import com.google.gson.Gson;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/** Android adapter for the execution gate, holding only the application context. */
public final class AccessibilityAwareRuleRunner {
    private static AccessibilityExecutionGate gate;
    private static final Map<String, WeakReference<Activity>> foregroundOwners = new HashMap<>();
    public interface Callback { void onResult(boolean queued, String message); }
    private AccessibilityAwareRuleRunner() {}

    public static void cancelWaiting() {
        Runnable cancel = () -> { if (gate != null) gate.cancelAll(); };
        if (Looper.myLooper() == Looper.getMainLooper()) cancel.run();
        else new Handler(Looper.getMainLooper()).post(cancel);
    }

    public static void enqueue(Context context, Rule rule, Callback callback) {
        Context app = context.getApplicationContext();
        Activity foreground = context instanceof Activity ? (Activity) context : null;
        Gson gson = new Gson();
        Rule snapshot = gson.fromJson(gson.toJson(rule), Rule.class);
        new Handler(Looper.getMainLooper()).post(() -> {
            String conditionFailure = QueueCoordinator.conditionFailure(app, snapshot);
            if (conditionFailure != null) {
                new ExecutionLogStore(app).append(
                        ExecutionSession.fromRule(snapshot, System.currentTimeMillis()),
                        "SKIPPED", conditionFailure);
                callback.onResult(false, conditionFailure);
                return;
            }
            if (foreground != null && !foreground.isFinishing() && !foreground.isDestroyed()) {
                foregroundOwners.put(snapshot.getId(), new WeakReference<>(foreground));
            }
            android.util.Log.i("ToolkitAccessibilityGate", "request needsAccessibility="
                    + requiresAccessibility(snapshot) + " enabled="
                    + AccessibilityServiceState.isEnabled(app) + " connected="
                    + (AutoJumpAccessibilityService.connectedContext() != null));
            if (gate == null) gate = new AccessibilityExecutionGate(
                    new AccessibilityExecutionGate.Environment() {
                        @Override public boolean connected() {
                            return AccessibilityServiceState.isEnabled(app)
                                    && AutoJumpAccessibilityService.connectedContext() != null;
                        }
                        @Override public AccessibilityExecutionGate.Availability availability() {
                            return AccessibilityExecutionGate.Availability.valueOf(
                                    ShizukuAccessibilityRecovery.availability().name());
                        }
                        @Override public void later(Runnable task, long delayMs) {
                            new Handler(Looper.getMainLooper()).postDelayed(task, delayMs);
                        }
                        @Override public void restore(AccessibilityExecutionGate.Result result) {
                            android.util.Log.i("ToolkitAccessibilityGate", "starting Shizuku recovery");
                            ShizukuAccessibilityRecovery.recover(app, (success, message) -> {
                                android.util.Log.i("ToolkitAccessibilityGate", "recovery result=" + success);
                                result.complete(success, message);
                            });
                        }
                        @Override public boolean enqueue(Rule selected) {
                            // Persist every recovered waiter before any external launch can
                            // suspend this process. Dispatch before the launcher callback
                            // closes its transparent foreground activity; HyperOS may block
                            // the same external launch immediately afterwards.
                            WeakReference<Activity> reference = foregroundOwners.remove(selected.getId());
                            Activity owner = reference == null ? null : reference.get();
                            Context dispatch = owner != null && !owner.isFinishing() && !owner.isDestroyed()
                                    ? owner : app;
                            Context launchContext = dispatch;
                            return ForegroundQueueDispatch.run(
                                    () -> QueueCoordinator.enqueueDeferred(app, selected) != null,
                                    () -> QueueCoordinator.pump(launchContext));
                        }
                    });
            gate.run(snapshot, requiresAccessibility(snapshot), callback::onResult);
        });
    }

    static boolean requiresAccessibility(Rule rule) {
        if (rule == null) return false;
        for (RuleAction action : rule.getActions()) {
            if (action != null && (action.getType() == ActionType.CLICK
                    || action.getType() == ActionType.RECORDED_TOUCH)) return true;
        }
        return false;
    }
}
