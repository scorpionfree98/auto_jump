package com.autojump.app.automation;

import android.annotation.TargetApi;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.ui.ClickActionEditActivity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Executes only persisted, user-authored click actions for their exact target package. */
public class AutoJumpAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> scheduled = new HashMap<>();
    private final ClickTargetResolver resolver = new ClickTargetResolver();
    private final AccessibilityNodeFinder nodeFinder = new AccessibilityNodeFinder();
    private final ClickRecordingPolicy recordingPolicy = new ClickRecordingPolicy();
    private final ForegroundPackageTracker foregroundTracker = new ForegroundPackageTracker();
    private PendingExecutionStore store;
    private AutomationOrchestrator orchestrator;
    private ExecutionLogStore logs;
    private ClickRecordingStore recordingStore;
    private RecordingOverlayController recordingOverlay;
    private Runnable recordingExpiry;
    private String foregroundPackage;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        ensureStores();
        updateRecordingOverlay();
        processAllSessions();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null) {
            String eventPackage = event.getPackageName() == null
                    ? null : event.getPackageName().toString();
            foregroundPackage = foregroundTracker.update(foregroundPackage, eventPackage,
                    packageName(getRootInActiveWindow()), getPackageName(),
                    event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }
        ensureStores();
        updateRecordingOverlay();
        captureRecordedClick(event);
        processAllSessions();
    }

    @Override
    public void onInterrupt() {
        hideRecordingOverlay();
        cancelAllCallbacks();
    }

    @Override
    public void onDestroy() {
        hideRecordingOverlay();
        cancelAllCallbacks();
        super.onDestroy();
    }

    private void ensureStores() {
        if (store == null) store = new PendingExecutionStore(this);
        if (orchestrator == null) orchestrator = new AutomationOrchestrator(this);
        if (logs == null) logs = new ExecutionLogStore(this);
        if (recordingStore == null) recordingStore = new ClickRecordingStore(this);
        if (recordingOverlay == null) recordingOverlay = new RecordingOverlayController(this);
    }

    private void updateRecordingOverlay() {
        ClickRecordingStore.State state = recordingStore.get();
        if (state.getStatus() == ClickRecordingStore.Status.WAITING_START) {
            ClickRecordingPolicy.StartDecision decision = recordingPolicy.evaluateStart(
                    state, foregroundPackage, getPackageName(),
                    isLauncherPackage(foregroundPackage), System.currentTimeMillis());
            if (decision == ClickRecordingPolicy.StartDecision.EXPIRED) {
                recordingStore.reject(state.getRequestId(), ClickRecordingStore.Status.EXPIRED,
                        "录制请求已过期");
                hideRecordingOverlay();
                return;
            }
            scheduleRecordingExpiry(state);
            if (decision != ClickRecordingPolicy.StartDecision.ELIGIBLE) {
                if (recordingOverlay != null && state.getTargetPackage() != null
                        && !state.getTargetPackage().equals(foregroundPackage)) {
                    recordingOverlay.hide();
                }
                return;
            }
            if (state.getTargetPackage() == null) {
                state = recordingStore.bindTargetPackage(
                        state.getRequestId(), foregroundPackage);
            }
            showRecordingOverlay(state);
            return;
        }
        if (state.getStatus() == ClickRecordingStore.Status.ARMED) {
            if (recordingOverlay != null) recordingOverlay.hide();
            scheduleRecordingExpiry(state);
            return;
        }
        hideRecordingOverlay();
    }

    private void showRecordingOverlay(ClickRecordingStore.State state) {
        if (recordingOverlay == null || !recordingOverlay.isSupported()) {
            recordingStore.reject(state.getRequestId(), ClickRecordingStore.Status.REJECTED,
                    "当前系统不支持录制悬浮窗");
            hideRecordingOverlay();
            return;
        }
        try {
            recordingOverlay.show(state.getRequestId(), state.getTargetPackage(),
                    new RecordingOverlayController.Listener() {
                        @Override public void onStart(String requestId, String targetPackage) {
                            ClickRecordingStore.State started = recordingStore.start(
                                    requestId, targetPackage, System.currentTimeMillis());
                            updateRecordingOverlay();
                            if (started.getStatus() == ClickRecordingStore.Status.ARMED) {
                                Toast.makeText(AutoJumpAccessibilityService.this,
                                        "录制已开始，请点击目标控件", Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override public void onCancel(String requestId) {
                            recordingStore.reject(requestId, ClickRecordingStore.Status.REJECTED,
                                    "用户取消了点击录制");
                            updateRecordingOverlay();
                            Toast.makeText(AutoJumpAccessibilityService.this,
                                    "已取消点击录制", Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (RuntimeException error) {
            recordingStore.reject(state.getRequestId(), ClickRecordingStore.Status.REJECTED,
                    "无法显示录制悬浮窗");
            hideRecordingOverlay();
        }
    }

    private void scheduleRecordingExpiry(ClickRecordingStore.State state) {
        cancelRecordingExpiry();
        long delay = Math.max(1, state.getExpiresAt() - System.currentTimeMillis());
        String requestId = state.getRequestId();
        recordingExpiry = () -> {
            ClickRecordingStore.State fresh = recordingStore.get();
            if (requestId != null && requestId.equals(fresh.getRequestId())
                    && (fresh.getStatus() == ClickRecordingStore.Status.WAITING_START
                    || fresh.getStatus() == ClickRecordingStore.Status.ARMED)) {
                recordingStore.reject(requestId, ClickRecordingStore.Status.EXPIRED,
                        "录制请求已过期");
            }
            updateRecordingOverlay();
        };
        handler.postDelayed(recordingExpiry, delay);
    }

    private void hideRecordingOverlay() {
        if (recordingOverlay != null) recordingOverlay.hide();
        cancelRecordingExpiry();
    }

    private void cancelRecordingExpiry() {
        if (recordingExpiry != null) handler.removeCallbacks(recordingExpiry);
        recordingExpiry = null;
    }

    private void processAllSessions() {
        for (ExecutionSession session : store.getAll()) processSession(session.getId());
    }

    private void processSession(String sessionId) {
        Runnable old = scheduled.remove(sessionId);
        if (old != null) handler.removeCallbacks(old);

        ExecutionSession session = store.findById(sessionId);
        if (session == null || session.getStatus() == ExecutionSession.Status.FAILED
                || session.getStatus() == ExecutionSession.Status.CANCELLED
                || session.getStatus() == ExecutionSession.Status.RUNNING) return;

        RuleAction action = session.currentAction();
        if (action == null) {
            store.remove(sessionId);
            return;
        }

        if (isKeyguardLocked()) {
            schedule(sessionId, 1000);
            return;
        }

        long now = System.currentTimeMillis();
        if (session.getNotBefore() > now) {
            schedule(sessionId, session.getNotBefore() - now);
            return;
        }

        if (action.getType() != ActionType.CLICK) {
            orchestrator.continueUntilExternalEvent(this, session, now);
            scheduleIfPending(sessionId, 100);
            return;
        }

        if (isDisallowedPackage(action.getTargetPackage())) {
            fail(session, "不允许在系统或锁屏界面执行点击", now);
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        String activePackage = packageName(root);
        if (activePackage == null) activePackage = foregroundPackage;
        long actionStartedAt = Math.max(session.getUpdatedAt(), session.getNotBefore());
        List<AccessibilityNodeFinder.NodeMatch> matches = nodeFinder.find(root, action);
        ClickResolution resolution = resolver.resolve(action, activePackage,
                nodeFinder.candidates(matches), actionStartedAt, now);
        handleResolution(session, action, matches, resolution, actionStartedAt, now);
    }

    private void handleResolution(ExecutionSession session, RuleAction action,
                                  List<AccessibilityNodeFinder.NodeMatch> matches,
                                  ClickResolution resolution, long actionStartedAt, long now) {
        switch (resolution.getDecision()) {
            case WAIT:
            case RETRY:
                schedule(session.getId(), Math.max(1, resolution.getNextCheckAtMs() - now));
                break;
            case IGNORE:
                long deadline = actionStartedAt + Math.max(0, action.getDelayBeforeMs())
                        + Math.max(1, action.getTimeoutMs());
                if (now >= deadline) fail(session, "目标应用未在超时前显示", now);
                else schedule(session.getId(), Math.min(Math.max(1, action.getRetryIntervalMs()),
                        Math.max(1, deadline - now)));
                break;
            case NODE:
                AccessibilityNodeInfo node = nodeFinder.nodeFor(
                        resolution.getCandidate().getKey(), matches);
                clickNode(session, node, now);
                break;
            case GESTURE:
                clickCoordinate(session, action, now);
                break;
            case FAIL:
                fail(session, resolution.getMessage(), now);
                break;
        }
    }

    private void clickNode(ExecutionSession session, AccessibilityNodeInfo node, long now) {
        if (node == null || !node.isEnabled() || !node.isClickable() || node.isPassword()) {
            fail(session, "点击控件已失效", now);
            return;
        }
        session.setStatus(ExecutionSession.Status.RUNNING);
        store.upsert(session);
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) completeAction(session.getId());
        else fail(store.findById(session.getId()), "控件拒绝点击动作", System.currentTimeMillis());
    }

    @TargetApi(Build.VERSION_CODES.N)
    private void clickCoordinate(ExecutionSession session, RuleAction action, long now) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            fail(session, "Android 7.0 以下不支持坐标手势", now);
            return;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float x = (float) (action.getRelativeX() * metrics.widthPixels);
        float y = (float) (action.getRelativeY() * metrics.heightPixels);
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80))
                .build();
        session.setStatus(ExecutionSession.Status.RUNNING);
        store.upsert(session);
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                completeAction(session.getId());
            }

            @Override public void onCancelled(GestureDescription gestureDescription) {
                ExecutionSession fresh = store.findById(session.getId());
                if (fresh != null) fail(fresh, "坐标点击被系统取消", System.currentTimeMillis());
            }
        }, handler);
        if (!accepted) fail(session, "系统未接受坐标点击", now);
    }

    private void completeAction(String sessionId) {
        ExecutionSession session = store.findById(sessionId);
        if (session == null || session.getStatus() != ExecutionSession.Status.RUNNING) return;
        long now = System.currentTimeMillis();
        logs.append(session, "SUCCESS", "点击动作执行成功");
        session.advance(now);
        if (session.getStatus() == ExecutionSession.Status.COMPLETED) {
            logs.append(session, "COMPLETED", "规则动作全部完成");
            store.remove(sessionId);
            return;
        }
        store.upsert(session);
        orchestrator.continueUntilExternalEvent(this, session, now);
        scheduleIfPending(sessionId, 50);
    }

    private void fail(ExecutionSession session, String message, long now) {
        if (session == null) return;
        RuleAction action = session.currentAction();
        String safeMessage = message == null || message.trim().isEmpty() ? "点击动作失败" : message;
        logs.append(session, "FAILED", safeMessage);
        if (action != null && action.isContinueOnFailure()) {
            session.advance(now);
            store.upsert(session);
            orchestrator.continueUntilExternalEvent(this, session, now);
            scheduleIfPending(session.getId(), 50);
        } else {
            session.setStatus(ExecutionSession.Status.FAILED);
            session.setLastError(safeMessage);
            session.setUpdatedAt(now);
            store.upsert(session);
        }
    }

    private void scheduleIfPending(String sessionId, long delayMs) {
        if (store.findById(sessionId) != null) schedule(sessionId, delayMs);
    }

    private void schedule(String sessionId, long delayMs) {
        Runnable old = scheduled.remove(sessionId);
        if (old != null) handler.removeCallbacks(old);
        Runnable callback = () -> processSession(sessionId);
        scheduled.put(sessionId, callback);
        handler.postDelayed(callback, Math.max(1, delayMs));
    }

    private void cancelAllCallbacks() {
        for (Runnable callback : scheduled.values()) handler.removeCallbacks(callback);
        scheduled.clear();
        cancelRecordingExpiry();
    }

    private boolean isKeyguardLocked() {
        KeyguardManager manager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return manager != null && manager.isKeyguardLocked();
    }

    private String packageName(AccessibilityNodeInfo root) {
        if (root == null || root.getPackageName() == null) return null;
        return root.getPackageName().toString();
    }

    private boolean isDisallowedPackage(String packageName) {
        if (packageName == null) return true;
        if ("com.android.systemui".equals(packageName)
                || "com.android.permissioncontroller".equals(packageName)
                || "com.google.android.permissioncontroller".equals(packageName)
                || "com.android.packageinstaller".equals(packageName)) return true;
        return isLauncherPackage(packageName);
    }

    private void captureRecordedClick(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_VIEW_CLICKED) return;
        ClickRecordingStore.State state = recordingStore.get();
        if (state.getStatus() != ClickRecordingStore.Status.ARMED) return;
        String packageName = event.getPackageName() == null ? null : event.getPackageName().toString();
        if (getPackageName().equals(packageName)) return;
        AccessibilityNodeInfo source = event.getSource();
        long now = System.currentTimeMillis();
        ClickRecordingPolicy.Decision decision = recordingPolicy.evaluate(state, packageName,
                source != null && source.isPassword(), isLauncherPackage(packageName),
                now);
        if (decision == ClickRecordingPolicy.Decision.INACTIVE) return;
        if (decision == ClickRecordingPolicy.Decision.EXPIRED) {
            recordingStore.reject(state.getRequestId(), ClickRecordingStore.Status.EXPIRED,
                    "录制请求已过期");
            return;
        }
        if (decision == ClickRecordingPolicy.Decision.PASSWORD) {
            recordingStore.reject(state.getRequestId(), ClickRecordingStore.Status.REJECTED,
                    "密码输入控件不会被录制");
            return;
        }
        if (decision == ClickRecordingPolicy.Decision.DISALLOWED) {
            recordingStore.reject(state.getRequestId(), ClickRecordingStore.Status.REJECTED,
                    "系统、权限、锁屏或桌面界面不会被录制");
            return;
        }
        if (source == null) {
            recordingStore.reject(state.getRequestId(), ClickRecordingStore.Status.REJECTED,
                    "系统没有提供该点击的控件信息");
            return;
        }

        Rect bounds = new Rect();
        source.getBoundsInScreen(bounds);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        double relativeX = clamp(bounds.exactCenterX() / Math.max(1d, metrics.widthPixels));
        double relativeY = clamp(bounds.exactCenterY() / Math.max(1d, metrics.heightPixels));
        String text = recordingPolicy.safeShortText(source.getText(), source.isEditable());
        String description = recordingPolicy.safeShortText(source.getContentDescription(), false);
        recordingStore.capture(new RecordedClick(state.getRequestId(), packageName,
                source.getViewIdResourceName(), text, description, relativeX, relativeY,
                recordingPolicy.elapsedSinceStart(state, now), now));
        if (recordingStore.get().getStatus() == ClickRecordingStore.Status.CAPTURED) {
            updateRecordingOverlay();
            returnToClickEditor();
        }
    }

    private void returnToClickEditor() {
        Intent intent = new Intent(this, ClickActionEditActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(intent);
        } catch (RuntimeException ignored) {
            // The captured result remains available when the user returns manually.
        }
    }

    private boolean isLauncherPackage(String packageName) {
        if (packageName == null) return false;
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager().resolveActivity(home, 0);
        return info != null && info.activityInfo != null
                && packageName.equals(info.activityInfo.packageName);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
