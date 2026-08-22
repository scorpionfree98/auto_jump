package com.autojump.app.automation;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import com.autojump.app.bean.Rule;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleAction;

public class AutomationOrchestrator {
    private static final String TAG = "AutomationOrchestrator";
    private final PendingExecutionStore store;

    public AutomationOrchestrator(Context context) { store = new PendingExecutionStore(context); }

    public ExecutionSession enqueue(Context context, Rule rule) {
        long now = System.currentTimeMillis();
        ExecutionSession session = ExecutionSession.fromRule(rule, now);
        store.upsert(session);
        continueUntilExternalEvent(context, session, now);
        return session;
    }

    public void continueUntilExternalEvent(Context context, ExecutionSession session, long now) {
        RuleAction action = session.currentAction();
        if (action == null) {
            session.setStatus(ExecutionSession.Status.COMPLETED);
            store.remove(session.getId());
            return;
        }
        if (action.getDelayBeforeMs() > 0) {
            session.setNotBefore(now + action.getDelayBeforeMs());
            session.setStatus(ExecutionSession.Status.WAITING);
            store.upsert(session);
            return;
        }
        if (action.getType() == ActionType.WAIT) {
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
                session.advance(now);
                if (session.getStatus() == ExecutionSession.Status.COMPLETED) store.remove(session.getId());
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
}
