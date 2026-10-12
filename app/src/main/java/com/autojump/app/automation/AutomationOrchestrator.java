package com.autojump.app.automation;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
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
    private final ScreenWakeController screenWakeController = new ScreenWakeController();

    public AutomationOrchestrator(Context context) {
        store = new PendingExecutionStore(context);
        logs = new ExecutionLogStore(context);
    }

    public ExecutionSession enqueue(Context context, Rule rule) {
        return QueueCoordinator.enqueue(context, rule);
    }

    public void startSession(Context context, ExecutionSession session) {
        long now = System.currentTimeMillis();
        if (isKeyguardLocked(context)) {
            session.setStatus(ExecutionSession.Status.WAITING);
            store.upsert(session);
            logs.append(session, "WAITING", "等待系统解锁（最多 2 分钟）");
            if (session.isRequestUnlockBeforeRun()) launchUnlock(context, session);
            return;
        }
        continueUntilExternalEvent(context, session, now);
    }

    public boolean resumeAfterUnlock(Context context, String sessionId) {
        ExecutionSession session = store.findById(sessionId);
        if (session == null || !QueuePolicy.live(session)
                || session.getStatus() == ExecutionSession.Status.QUEUED
                || session.getStatus() == ExecutionSession.Status.RUNNING
                || isKeyguardLocked(context)) return false;
        long now = System.currentTimeMillis();
        if (QueueCoordinator.expired(context, session)) {
            cancelSession(session, "任务等待已超过 10 分钟，请重新运行");
            return false;
        }
        session.setStatus(ExecutionSession.Status.PENDING);
        session.setUpdatedAt(now);
        session.setLastError(null);
        store.upsert(session);
        logs.append(session, "RESUMED", "系统解锁完成，继续执行");
        continueUntilExternalEvent(context, session, now);
        return true;
    }

    public void continueUntilExternalEvent(Context context, ExecutionSession session, long now) {
        if (QueueCoordinator.expired(context, session)) {
            cancelSession(session, "任务等待已超过 10 分钟，请重新运行");
            return;
        }
        RuleAction action = session.currentAction();
        if (action == null) {
            session.setStatus(ExecutionSession.Status.COMPLETED);
            logs.append(session, "COMPLETED", "规则动作全部完成");
            store.remove(session.getId());
            return;
        }
        boolean externallyDispatched = action.getType() == ActionType.CLICK
                || action.getType() == ActionType.RECORDED_TOUCH;
        if (!externallyDispatched && action.getDelayBeforeMs() > 0) {
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
        if (externallyDispatched) {
            session.setStatus(ExecutionSession.Status.WAITING);
            store.upsert(session);
            return;
        }
        if (action.getType() == ActionType.DEEPLINK) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(action.getDeeplink()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                RuleAction next = session.getCurrentActionIndex() + 1 < session.getActions().size()
                        ? session.getActions().get(session.getCurrentActionIndex() + 1) : null;
                ResolveInfo resolved = context.getPackageManager().resolveActivity(intent, 0);
                boolean matchingRecordedTouch = next != null
                        && next.getType() == ActionType.RECORDED_TOUCH
                        && resolved != null && resolved.activityInfo != null
                        && next.getTargetPackage().equals(resolved.activityInfo.packageName)
                        && AutoJumpAccessibilityService.connectedContext() != null;
                if (matchingRecordedTouch) {
                    // Persist and hand the delayed gesture to Android before opening the target.
                    // Xiaomi may suspend our process as soon as the other app is foreground.
                    session.advance(now);
                    session.setStatus(ExecutionSession.Status.WAITING);
                    store.upsert(session);
                    if (!AutoJumpAccessibilityService.armRecordedTouch(session.getId(), next)) {
                        return;
                    }
                    context.startActivity(intent);
                    logs.append(session, "LINK_REQUESTED",
                            "已请求打开目标链接；录制点击已提前交给系统等待执行");
                    return;
                }
                context.startActivity(intent);
                logs.append(session, "LINK_REQUESTED", "已请求打开目标链接，等待目标应用显示");
                session.advance(now);
                if (session.getStatus() == ExecutionSession.Status.COMPLETED) {
                    logs.append(session, "COMPLETED", "规则动作全部完成");
                    store.remove(session.getId());
                }
                else {
                    session.setStatus(ExecutionSession.Status.WAITING);
                    store.upsert(session);
                    if (!AccessibilityServiceState.isEnabled(context)
                            && (session.currentAction().getType() == ActionType.CLICK
                            || session.currentAction().getType() == ActionType.RECORDED_TOUCH)) {
                        failOrContinue(context, session, session.currentAction(),
                                "已打开目标页面，但自动点击服务未开启，请开启后重新运行", now);
                    }
                }
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

    public void cancelSession(ExecutionSession session, String reason) {
        logs.append(session, "CANCELLED", reason);
        store.remove(session.getId());
    }

    private void launchUnlock(Context context, ExecutionSession session) {
        if (screenWakeController.wakeForSystemUnlock(context)) {
            logs.append(session, "WAITING", "已唤醒屏幕，等待系统解锁");
        }
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
