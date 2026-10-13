package com.autojump.app.automation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.autojump.app.bean.Rule;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.shizuku.ShizukuAccessibilityRecovery;
import com.google.gson.Gson;

/** Android adapter for the execution gate, holding only the application context. */
public final class AccessibilityAwareRuleRunner {
    private static AccessibilityExecutionGate gate;
    public interface Callback { void onResult(boolean queued, String message); }
    private AccessibilityAwareRuleRunner() {}

    public static void cancelWaiting() {
        Runnable cancel = () -> { if (gate != null) gate.cancelAll(); };
        if (Looper.myLooper() == Looper.getMainLooper()) cancel.run();
        else new Handler(Looper.getMainLooper()).post(cancel);
    }

    public static void enqueue(Context context, Rule rule, Callback callback) {
        Context app = context.getApplicationContext();
        Gson gson = new Gson();
        Rule snapshot = gson.fromJson(gson.toJson(rule), Rule.class);
        new Handler(Looper.getMainLooper()).post(() -> {
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
                            ShizukuAccessibilityRecovery.recover(app, result::complete);
                        }
                        @Override public boolean enqueue(Rule selected) {
                            // Persist every recovered waiter before any external launch can
                            // suspend this process. Dispatch on the next main-loop turn.
                            ExecutionSession session = QueueCoordinator.enqueueDeferred(app, selected);
                            new Handler(Looper.getMainLooper()).post(() -> QueueCoordinator.pump(app));
                            return session != null;
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
