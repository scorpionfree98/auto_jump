package com.autojump.app.automation;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.app.KeyguardManager;
import android.net.Uri;
import android.util.Log;
import com.autojump.app.bean.Rule;
import com.autojump.app.UnlockGateActivity;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleAction;

public class AutomationOrchestrator {
    private static final String TAG = "AutomationOrchestrator";
    private final PendingExecutionStore store;
    private final ExecutionLogStore logs;
    private final ExecutionSessionPolicy sessionPolicy = new ExecutionSessionPolicy();

    public AutomationOrchestrator(Context context) {
        store = new PendingExecutionStore(context);
        logs = new ExecutionLogStore(context);
    }

    public ExecutionSession enqueue(Context context, Rule rule) {
        ExecutionSession active = sessionPolicy.findActiveForRule(store.getAll(), rule.getId());
        if (active != null) {
            if (active.isRequestUnlockBeforeRun() && isKeyguardLocked(context)) {
                launchUnlock(context, active);
            }
            return active;
        }
        long now = System.currentTimeMillis();
        ExecutionSession session = ExecutionSession.fromRule(rule, now);
        store.upsert(session);
        logs.append(session, "ENQUEUED", "规则已加入执行队列");
        if (session.isRequestUnlockBeforeRun() && isKeyguardLocked(context)) {
            session.setStatus(ExecutionSession.Status.WAITING);
            store.upsert(session);
            logs.append(session, "WAITING", "等待系统解锁");
            launchUnlock(context, session);
            return session;
        }
        continueUntilExternalEvent(context, session, now);
        return session;
    }

    public boolean resumeAfterUnlock(Context context, String sessionId) {
        ExecutionSession session = store.findById(sessionId);
        if (session == null || isKeyguardLocked(context)) return false;
        long now = System.currentTimeMillis();
        session.setStatus(ExecutionSession.Status.PENDING);
        session.setUpdatedAt(now);
        session.setLastError(null);
        store.upsert(session);
        logs.append(session, "RESUMED", "系统解锁完成，继续执行");
        continueUntilExternalEvent(context, session, now);
        return true;
    }

    public void continueUntilExternalEvent(Context context, ExecutionSession session, long now) {
        RuleAction action = session.currentAction();
        if (action == null) {
            session.setStatus(ExecutionSession.Status.COMPLETED);
            logs.append(session, "COMPLETED", "规则动作全部完成");
            store.remove(session.getId());
            return;
        }
        if (action.getType() != ActionType.CLICK && action.getDelayBeforeMs() > 0) {
            if (session.getNotBefore() == 0) {
                session.setNotBefore(now + action.getDelayBeforeMs());
                session.setStatus(ExecutionSession.Status.WAITING);
                store.upsert(session);
                return;
            }
            if (now < session.getNotBefore()) {
                session.setStatus(ExecutionSession.Status.WAITING);
                store.upsert(session);
                return;
            }
            session.setNotBefore(0);
        }
        if (action.getType() == ActionType.WAIT) {
            logs.append(session, "WAITING", "等待 " + action.getWaitDurationMs() + "ms");
            session.advance(now);
            session.setNotBefore(now + action.getWaitDurationMs());
            session.setStatus(ExecutionSession.Status.WAITING);
            store.upsert(session);
            return;
        }
        if (action.getType() == ActionType.CLICK) {
            session.setStatus(ExecutionSession.Status.WAITING);
            store.upsert(session);
            return;
        }
        if (action.getType() == ActionType.DEEPLINK) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(action.getDeeplink()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                logs.append(session, "SUCCESS", "已打开 deeplink");
                session.advance(now);
                if (session.getStatus() == ExecutionSession.Status.COMPLETED) {
                    logs.append(session, "COMPLETED", "规则动作全部完成");
                    store.remove(session.getId());
                }
                else { session.setStatus(ExecutionSession.Status.WAITING); store.upsert(session); }
            } catch (ActivityNotFoundException error) {
                failOrContinue(context, session, action, "没有应用可以处理该链接", now);
            } catch (RuntimeException error) {
                failOrContinue(context, session, action, error.getMessage(), now);
            }
        }
    }

    private void failOrContinue(Context context, ExecutionSession session, RuleAction action,
                                String message, long now) {
        Log.e(TAG, "动作执行失败: " + message);
        logs.append(session, "FAILED", message == null ? "动作执行失败" : message);
        if (action.isContinueOnFailure()) {
            session.advance(now);
            store.upsert(session);
            continueUntilExternalEvent(context, session, now);
        } else {
            session.setStatus(ExecutionSession.Status.FAILED);
            session.setLastError(message == null ? "动作执行失败" : message);
            session.setUpdatedAt(now);
            store.upsert(session);
        }
    }

    private boolean isKeyguardLocked(Context context) {
        KeyguardManager manager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return manager != null && manager.isKeyguardLocked();
    }

    private void launchUnlock(Context context, ExecutionSession session) {
        Intent unlock = new Intent(context, UnlockGateActivity.class);
        unlock.putExtra(UnlockGateActivity.EXTRA_SESSION_ID, session.getId());
        unlock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(unlock);
        } catch (RuntimeException error) {
            logs.append(session, "WAITING", "系统限制了解锁界面启动，任务将在解锁后自动继续");
        }
    }
}
