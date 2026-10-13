package com.autojump.app.automation;

import android.content.Context;
import com.autojump.app.bean.Rule;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.shizuku.ShizukuAccessibilityRecovery;
import java.util.ArrayList;
import java.util.List;

/** Restores this app's accessibility service before queueing rules that need it. */
public final class AccessibilityAwareRuleRunner {
    private static final Object LOCK = new Object();
    private static final List<PendingRun> PENDING = new ArrayList<>();
    private static boolean recoveryRunning;

    public interface Callback {
        void onResult(boolean queued, String message);
    }

    private AccessibilityAwareRuleRunner() {}

    public static void enqueue(Context context, Rule rule, Callback callback) {
        Context app = context.getApplicationContext();
        if (!requiresAccessibility(rule) || AccessibilityServiceState.isEnabled(app)) {
            enqueueNow(app, rule, callback);
            return;
        }

        ShizukuAccessibilityRecovery.Availability availability =
                ShizukuAccessibilityRecovery.availability();
        if (availability == ShizukuAccessibilityRecovery.Availability.PERMISSION_REQUIRED) {
            callback.onResult(false, "自动点击未开启；请先在权限页确认一次 Shizuku 授权");
            return;
        }
        if (availability != ShizukuAccessibilityRecovery.Availability.READY) {
            callback.onResult(false, "自动点击未开启，且 Shizuku 当前不可用");
            return;
        }

        synchronized (LOCK) {
            PENDING.add(new PendingRun(app, rule, callback));
            if (recoveryRunning) return;
            recoveryRunning = true;
        }
        ShizukuAccessibilityRecovery.recover(app,
                AccessibilityAwareRuleRunner::finishRecovery);
    }

    static boolean requiresAccessibility(Rule rule) {
        if (rule == null) return false;
        for (RuleAction action : rule.getActions()) {
            if (action != null && (action.getType() == ActionType.CLICK
                    || action.getType() == ActionType.RECORDED_TOUCH)) return true;
        }
        return false;
    }

    private static void finishRecovery(boolean success, String message) {
        List<PendingRun> pending;
        synchronized (LOCK) {
            pending = new ArrayList<>(PENDING);
            PENDING.clear();
            recoveryRunning = false;
        }
        for (PendingRun run : pending) {
            if (success) enqueueNow(run.context, run.rule, run.callback);
            else run.callback.onResult(false, message);
        }
    }

    private static void enqueueNow(Context context, Rule rule, Callback callback) {
        ExecutionSession session = new AutomationOrchestrator(context).enqueue(context, rule);
        callback.onResult(session != null,
                session == null ? "指令未能加入执行队列" : "指令已加入执行队列");
    }

    private static final class PendingRun {
        final Context context;
        final Rule rule;
        final Callback callback;

        PendingRun(Context context, Rule rule, Callback callback) {
            this.context = context;
            this.rule = rule;
            this.callback = callback;
        }
    }
}
