package com.autojump.app.automation;

import android.annotation.TargetApi;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.domain.TouchPoint;
import com.autojump.app.ui.ClickActionEditActivity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Executes only persisted, user-authored click actions for their exact target package. */
public class AutoJumpAccessibilityService extends AccessibilityService {
    private static AutoJumpAccessibilityService connected;
    public static Context connectedContext() { return connected; }
    /** Hands a delayed recorded touch to Android before a cross-app launch can suspend us. */
    public static boolean armRecordedTouch(String sessionId, RuleAction action) {
        return connected != null && connected.armRecordedTouchInternal(sessionId, action);
    }
    public static void requestExecutionCheck(String sessionId) {
        if (connected != null) connected.schedule(sessionId, 1L);
    }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> scheduled = new HashMap<>();
    private String replayWindowPackage;
    private long replayWindowChangedAt;
    private final ClickTargetResolver resolver = new ClickTargetResolver();
    private final AccessibilityNodeFinder nodeFinder = new AccessibilityNodeFinder();
    private final ClickGesturePolicy gesturePolicy = new ClickGesturePolicy();
    private final ClickRecordingPolicy recordingPolicy = new ClickRecordingPolicy();
    private final TouchTracePolicy touchTracePolicy = new TouchTracePolicy();
    private final RecordedTouchResolver recordedTouchResolver = new RecordedTouchResolver();
    private final RecordedTouchSchedule recordedTouchSchedule = new RecordedTouchSchedule();
    private final RecordedTouchGesturePlan recordedTouchGesturePlan =
            new RecordedTouchGesturePlan();
    private final ForegroundPackageTracker foregroundTracker = new ForegroundPackageTracker();
    private PendingExecutionStore store;
    private AutomationOrchestrator orchestrator;
    private ExecutionLogStore logs;
    private ClickRecordingStore recordingStore;
    private RecordingOverlayController recordingOverlay;
    private Runnable recordingExpiry;
    private String foregroundPackage;
    private String recordingCandidateRequestId;
    private String recordingTargetCandidate;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        ensureStores();
        connected = this;
        android.util.Log.i("ToolkitClickService", "connected pid=" + android.os.Process.myPid());
        QueueCoordinator.pump(this);
        updateRecordingOverlay();
        processAllSessions();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        connected = this;
        ensureStores();
        String eventPackage = null;
        // Coordinate replay must not wait for the target WebView's node-tree IPC.
        // Root confirmation is only needed when binding a new recording target.
        ClickRecordingStore.State recording = recordingStore.get();
        String activeRootPackage = recording.getStatus() == ClickRecordingStore.Status.WAITING_START
                && recording.getTargetPackage() == null ? packageName(getRootInActiveWindow()) : null;
        if (event != null) {
            eventPackage = event.getPackageName() == null
                    ? null : event.getPackageName().toString();
            replayWindowPackage = foregroundTracker.replayWindow(replayWindowPackage, eventPackage,
                    event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                // Use event occurrence time, not delivery time: a late old launcher
                // event must not be mistaken for a user switching away after launch.
                replayWindowChangedAt = System.currentTimeMillis() - Math.max(0,
                        android.os.SystemClock.uptimeMillis() - event.getEventTime());
            }
            foregroundPackage = foregroundTracker.update(foregroundPackage, eventPackage,
                    activeRootPackage, getPackageName(),
                    event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }
        ensureStores();
        updateRecordingTargetCandidate(event, eventPackage, activeRootPackage);
        updateRecordingOverlay();
        processAllSessions();
    }

    @Override
    public void onInterrupt() {
        hideRecordingOverlay();
        cancelAllCallbacks();
    }

    @Override
    public void onDestroy() {
        if (connected == this) connected = null;
        android.util.Log.i("ToolkitClickService", "destroyed pid=" + android.os.Process.myPid());
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
            syncRecordingCandidateRequest(state);
            String evaluatedPackage = state.getTargetPackage() == null
                    ? recordingTargetCandidate : foregroundPackage;
            ClickRecordingPolicy.StartDecision decision = recordingPolicy.evaluateStart(
                    state, evaluatedPackage, getPackageName(),
                    isLauncherPackage(evaluatedPackage), System.currentTimeMillis());
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
                        state.getRequestId(), recordingTargetCandidate);
                clearRecordingTargetCandidate();
            }
            showRecordingOverlay(state);
            return;
        }
        if (state.getStatus() == ClickRecordingStore.Status.ARMED
                || state.getStatus() == ClickRecordingStore.Status.REPLAYING
                || state.getStatus() == ClickRecordingStore.Status.CONFIRMING) {
            scheduleRecordingExpiry(state);
            return;
        }
        clearRecordingTargetCandidate();
        hideRecordingOverlay();
    }

    private void updateRecordingTargetCandidate(AccessibilityEvent event, String eventPackage,
                                                String activeRootPackage) {
        ClickRecordingStore.State state = recordingStore.get();
        if (state.getStatus() != ClickRecordingStore.Status.WAITING_START
                || state.getTargetPackage() != null) {
            if (state.getStatus() != ClickRecordingStore.Status.WAITING_START) {
                clearRecordingTargetCandidate();
            }
            return;
        }
        syncRecordingCandidateRequest(state);
        if (event == null) return;
        String candidate = foregroundTracker.trustedWindowPackage(eventPackage,
                activeRootPackage, getPackageName(),
                event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        if (candidate == null || isEnabledAccessibilityServicePackage(candidate)) return;
        ClickRecordingPolicy.StartDecision decision = recordingPolicy.evaluateStart(
                state, candidate, getPackageName(), isLauncherPackage(candidate),
                System.currentTimeMillis());
        if (decision == ClickRecordingPolicy.StartDecision.ELIGIBLE) {
            recordingTargetCandidate = candidate;
        }
    }

    private void syncRecordingCandidateRequest(ClickRecordingStore.State state) {
        String requestId = state == null ? null : state.getRequestId();
        if (requestId == null || !requestId.equals(recordingCandidateRequestId)) {
            recordingCandidateRequestId = requestId;
            recordingTargetCandidate = null;
        }
    }

    private void clearRecordingTargetCandidate() {
        recordingCandidateRequestId = null;
        recordingTargetCandidate = null;
    }

    private boolean isEnabledAccessibilityServicePackage(String packageName) {
        AccessibilityManager manager = (AccessibilityManager)
                getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null || packageName == null) return false;
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            ResolveInfo resolveInfo = info == null ? null : info.getResolveInfo();
            if (resolveInfo != null && resolveInfo.serviceInfo != null
                    && packageName.equals(resolveInfo.serviceInfo.packageName)) return true;
        }
        return false;
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
                            if (started.getStatus() == ClickRecordingStore.Status.ARMED) {
                                Toast.makeText(AutoJumpAccessibilityService.this,
                                        "录制已开始，请按平时方式点击目标按钮", Toast.LENGTH_LONG).show();
                                startTouchCapture(started);
                            } else {
                                updateRecordingOverlay();
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

    private void startTouchCapture(ClickRecordingStore.State state) {
        recordingOverlay.captureNextGesture(state.getRequestId(), state.getTargetPackage(),
                new RecordingOverlayController.CaptureListener() {
                    @Override public void onCaptured(List<TouchTracePolicy.RawPoint> samples,
                                                     int width, int height, int orientation) {
                        handleCapturedTrace(state.getRequestId(), samples, width, height,
                                orientation);
                    }

                    @Override public void onRejected(String reason) {
                        recordingStore.reject(state.getRequestId(),
                                ClickRecordingStore.Status.REJECTED, reason);
                        updateRecordingOverlay();
                        Toast.makeText(AutoJumpAccessibilityService.this, reason,
                                Toast.LENGTH_LONG).show();
                        returnToClickEditor();
                    }
                });
    }

    private void handleCapturedTrace(String requestId,
                                     List<TouchTracePolicy.RawPoint> samples,
                                     int width, int height, int orientation) {
        ClickRecordingStore.State state = recordingStore.get();
        if (state.getStatus() != ClickRecordingStore.Status.ARMED
                || requestId == null || !requestId.equals(state.getRequestId())) return;
        String activePackage = packageName(getRootInActiveWindow());
        if (activePackage == null) activePackage = foregroundPackage;
        if (!state.getTargetPackage().equals(activePackage)) {
            recordingStore.reject(requestId, ClickRecordingStore.Status.REJECTED,
                    "录制期间目标应用发生变化，请重新录制");
            returnToClickEditor();
            return;
        }
        final List<TouchPoint> trace;
        try {
            trace = touchTracePolicy.normalize(samples, width, height);
        } catch (IllegalArgumentException error) {
            recordingStore.reject(requestId, ClickRecordingStore.Status.REJECTED,
                    error.getMessage());
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            returnToClickEditor();
            return;
        }
        long now = System.currentTimeMillis();
        long duration = trace.get(trace.size() - 1).getElapsedMs();
        long delayBeforeMs = Math.max(0, now - state.getStartedAt() - duration);
        RecordedTouch touch = new RecordedTouch(requestId, state.getTargetPackage(), trace,
                width, height, orientation, packageVersion(state.getTargetPackage()),
                delayBeforeMs, now);
        if (recordingStore.beginReplay(requestId).getStatus()
                != ClickRecordingStore.Status.REPLAYING) return;
        dispatchRecordedTouch(trace, width, height, new GestureDispatchListener() {
            @Override public void onCompleted() {
                if (recordingStore.beginConfirmation(touch).getStatus()
                        != ClickRecordingStore.Status.CONFIRMING) return;
                recordingOverlay.showConfirmation(requestId, touch.getTargetPackage(),
                        new RecordingOverlayController.ConfirmationListener() {
                            @Override public void onContinue(String continuedRequestId) {
                                ClickRecordingStore.State next = recordingStore.continueCapture(
                                        continuedRequestId, System.currentTimeMillis());
                                if (next.getStatus() == ClickRecordingStore.Status.ARMED) {
                                    Toast.makeText(AutoJumpAccessibilityService.this,
                                            "请执行下一个点击或滑动，完成后可继续录制",
                                            Toast.LENGTH_LONG).show();
                                    startTouchCapture(next);
                                } else if (next.getStatus()
                                        == ClickRecordingStore.Status.CAPTURED) {
                                    Toast.makeText(AutoJumpAccessibilityService.this,
                                            "已达到 "
                                                    + ClickRecordingStore.MAX_RECORDED_TOUCHES
                                                    + " 个动作上限并自动完成",
                                            Toast.LENGTH_LONG).show();
                                    returnToClickEditor();
                                } else {
                                    Toast.makeText(AutoJumpAccessibilityService.this,
                                            "一次最多录制 "
                                                    + ClickRecordingStore.MAX_RECORDED_TOUCHES
                                                    + " 个动作，请点完成保存",
                                            Toast.LENGTH_LONG).show();
                                    recordingOverlay.showConfirmation(continuedRequestId,
                                            touch.getTargetPackage(), this);
                                }
                            }

                            @Override public void onFinish(String confirmedRequestId) {
                                ClickRecordingStore.State finished =
                                        recordingStore.finishCapture(confirmedRequestId);
                                if (finished.getStatus()
                                        == ClickRecordingStore.Status.CAPTURED) {
                                    Toast.makeText(AutoJumpAccessibilityService.this,
                                            "已保存 " + finished.getRecordedTouches().size()
                                                    + " 个触摸动作", Toast.LENGTH_SHORT).show();
                                }
                                returnToClickEditor();
                            }

                            @Override public void onRetry(String retryRequestId) {
                                ClickRecordingStore.State retry = recordingStore.retryCapture(
                                        retryRequestId, System.currentTimeMillis());
                                if (retry.getStatus() == ClickRecordingStore.Status.ARMED) {
                                    Toast.makeText(AutoJumpAccessibilityService.this,
                                            "请再次按平时方式点击目标按钮", Toast.LENGTH_LONG).show();
                                    startTouchCapture(retry);
                                }
                            }

                            @Override public void onCancel(String cancelledRequestId) {
                                recordingStore.reject(cancelledRequestId,
                                        ClickRecordingStore.Status.REJECTED, "用户取消了触摸录制");
                                returnToClickEditor();
                            }
                        });
            }

            @Override public void onFailed(String reason) {
                recordingStore.reject(requestId, ClickRecordingStore.Status.REJECTED, reason);
                Toast.makeText(AutoJumpAccessibilityService.this, reason, Toast.LENGTH_LONG).show();
                returnToClickEditor();
            }
        });
    }

    private void scheduleRecordingExpiry(ClickRecordingStore.State state) {
        cancelRecordingExpiry();
        long delay = Math.max(1, state.getExpiresAt() - System.currentTimeMillis());
        String requestId = state.getRequestId();
        recordingExpiry = () -> {
            ClickRecordingStore.State fresh = recordingStore.get();
            if (requestId != null && requestId.equals(fresh.getRequestId())
                    && (fresh.getStatus() == ClickRecordingStore.Status.WAITING_START
                    || fresh.getStatus() == ClickRecordingStore.Status.ARMED
                    || fresh.getStatus() == ClickRecordingStore.Status.REPLAYING
                    || fresh.getStatus() == ClickRecordingStore.Status.CONFIRMING)) {
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
        QueueCoordinator.pump(this);
        for (ExecutionSession session : store.getAll()) processSession(session.getId());
    }

    private void processSession(String sessionId) {
        Runnable old = scheduled.remove(sessionId);
        if (old != null) handler.removeCallbacks(old);

        ExecutionSession session = store.findById(sessionId);
        if (QueueCoordinator.expired(this, session)) {
            if (session.getStatus() == ExecutionSession.Status.RUNNING) QueueCoordinator.pump(this);
            else orchestrator.cancelSession(session, "任务等待已超时，请重新运行");
            return;
        }
        if (session == null || session.getStatus() == ExecutionSession.Status.FAILED
                || session.getStatus() == ExecutionSession.Status.QUEUED
                || session.getStatus() == ExecutionSession.Status.SKIPPED
                || session.getStatus() == ExecutionSession.Status.CANCELLED
                || session.getStatus() == ExecutionSession.Status.RUNNING) return;

        RuleAction action = session.currentAction();
        if (action == null) {
            if (session.getNotBefore() <= System.currentTimeMillis()) {
                orchestrator.continueUntilExternalEvent(this, session, System.currentTimeMillis());
            } else schedule(sessionId, session.getNotBefore() - System.currentTimeMillis());
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

        if (action.getType() != ActionType.CLICK
                && action.getType() != ActionType.RECORDED_TOUCH) {
            orchestrator.continueUntilExternalEvent(this, session, now);
            scheduleIfPending(sessionId, 100);
            return;
        }

        if (isDisallowedPackage(action.getTargetPackage())) {
            fail(session, "不允许在系统或锁屏界面执行点击", now);
            return;
        }

        AccessibilityNodeInfo root = action.getType() == ActionType.RECORDED_TOUCH ? null : getRootInActiveWindow();
        String activePackage = action.getType() == ActionType.RECORDED_TOUCH ? replayWindowPackage : packageName(root);
        if (activePackage == null && action.getType() != ActionType.RECORDED_TOUCH) activePackage = foregroundPackage;
        long actionStartedAt = Math.max(session.getUpdatedAt(), session.getNotBefore());
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        if (action.getType() == ActionType.RECORDED_TOUCH) {
            metrics = recordedMetrics(action);
            long deadline = actionStartedAt + action.getDelayBeforeMs() + action.getTimeoutMs();
            if (now >= deadline) {
                fail(session, "目标页面等待超时，未继续派发点击", now);
                return;
            }
            if (RecordedReplayWindowPolicy.switchedAway(action.getTargetPackage(), replayWindowPackage,
                    replayWindowChangedAt, actionStartedAt)) {
                fail(session, "检测到跳转后已切换应用，未派发录制点击", now);
                return;
            }
            RecordedTouchResolver.Result result = recordedTouchResolver.resolveAfterDelay(action,
                    actionStartedAt, now, metrics.widthPixels,
                    metrics.heightPixels, getResources().getConfiguration().orientation);
            handleRecordedTouchResolution(session, action, result, now);
            return;
        }
        List<AccessibilityNodeFinder.NodeMatch> matches = nodeFinder.find(
                root, action, metrics.widthPixels, metrics.heightPixels);
        ClickResolution resolution = resolver.resolve(action, activePackage,
                nodeFinder.candidates(matches), actionStartedAt, now);
        handleResolution(session, action, matches, resolution, actionStartedAt, now);
    }

    private void handleRecordedTouchResolution(ExecutionSession session, RuleAction action,
                                               RecordedTouchResolver.Result result, long now) {
        switch (result.getDecision()) {
            case WAIT_FOR_PACKAGE:
                long deadline = Math.max(session.getUpdatedAt(), session.getNotBefore())
                        + Math.max(0, action.getDelayBeforeMs())
                        + Math.max(1, action.getTimeoutMs());
                if (now >= deadline) fail(session, "目标应用未在超时前显示", now);
                else schedule(session.getId(), Math.min(200, Math.max(1, deadline - now)));
                break;
            case WAIT_FOR_DELAY:
                schedule(session.getId(), Math.max(1, result.getNextCheckAtMs() - now));
                break;
            case FAIL:
                fail(session, result.getMessage(), now);
                break;
            case REPLAY:
                DisplayMetrics metrics = recordedMetrics(action);
                com.autojump.app.domain.TouchPoint first = result.getTrace().get(0);
                logs.append(session,"REPLAY_POSITION", "回放落点 " + Math.round(first.getRelativeX()*metrics.widthPixels)
                        + "," + Math.round(first.getRelativeY()*metrics.heightPixels) + " px；容差 "
                        + action.getScreenTolerancePercent() + "%；偏移 " + action.getReplayOffsetXPx() + "," + action.getReplayOffsetYPx());
                session.setStatus(ExecutionSession.Status.RUNNING);
                store.upsert(session);
                dispatchRecordedTouchRepeated(session.getId(), result.getTrace(), metrics.widthPixels,
                        metrics.heightPixels, action.getRepeatCount(),
                        action.getRepeatIntervalMs(), 1, new GestureDispatchListener() {
                            @Override public void onCompleted() {
                                completeAction(session.getId(), ClickDispatchResult.recordedTouch(
                                        action.getTouchDurationMs(), action.getTouchTrace().size(),
                                        action.getRepeatCount()));
                            }

                            @Override public void onFailed(String reason) {
                                ExecutionSession fresh = store.findById(session.getId());
                                if (fresh != null) fail(fresh, reason, System.currentTimeMillis());
                            }
                        });
                break;
        }
    }

    private DisplayMetrics recordedMetrics(RuleAction action) {
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setTo(getResources().getDisplayMetrics());
        int resource = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int statusBar = resource == 0 ? 0 : getResources().getDimensionPixelSize(resource);
        metrics.heightPixels = RecordedViewportPolicy.height(action.getRecordedDisplayWidth(),
                action.getRecordedDisplayHeight(), metrics.widthPixels, metrics.heightPixels, statusBar);
        return metrics;
    }

    @TargetApi(Build.VERSION_CODES.N)
    private boolean armRecordedTouchInternal(String sessionId, RuleAction action) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || sessionId == null || action == null) {
            return false;
        }
        ExecutionSession session = store.findById(sessionId);
        if (session == null || session.currentAction() == null
                || session.currentAction().getType() != ActionType.RECORDED_TOUCH
                || session.isCancelRequested() || isKeyguardLocked()) {
            return false;
        }
        GestureDescription.Builder builder = new GestureDescription.Builder();
        List<RuleAction> preparedActions = new ArrayList<>();
        int startIndex = session.getCurrentActionIndex();
        long cursor = 0L;
        int strokeCount = 0;
        TouchPoint first = null;
        DisplayMetrics firstMetrics = null;
        try {
            for (int actionIndex = startIndex; actionIndex < session.getActions().size(); actionIndex++) {
                RuleAction candidate = session.getActions().get(actionIndex);
                if (candidate.getType() != ActionType.RECORDED_TOUCH
                        || !action.getTargetPackage().equals(candidate.getTargetPackage())) break;
                if (preparedActions.size() >= ClickRecordingStore.MAX_RECORDED_TOUCHES) {
                    throw new IllegalArgumentException("连续录制动作超过一次可提交上限");
                }
                if (strokeCount + candidate.getRepeatCount()
                        > GestureDescription.getMaxStrokeCount()) {
                    throw new IllegalArgumentException("连续录制笔画超过系统上限");
                }
                DisplayMetrics metrics = recordedMetrics(candidate);
                RecordedTouchResolver.Result resolved = recordedTouchResolver.resolveAfterDelay(
                        candidate, 0L, Math.max(0L, candidate.getDelayBeforeMs()),
                        metrics.widthPixels, metrics.heightPixels,
                        getResources().getConfiguration().orientation);
                if (resolved.getDecision() != RecordedTouchResolver.Decision.REPLAY) {
                    throw new IllegalArgumentException(resolved.getMessage() == null
                            ? "无法准备连续录制动作" : resolved.getMessage());
                }
                List<TouchPoint> trace = resolved.getTrace();
                Path path = recordedPath(trace, metrics.widthPixels, metrics.heightPixels);
                if (first == null) {
                    first = trace.get(0);
                    firstMetrics = metrics;
                }
                long duration = Math.max(1L, candidate.getTouchDurationMs());
                long baseStart = Math.addExact(cursor,
                        Math.max(0L, candidate.getDelayBeforeMs()));
                RecordedTouchSchedule.Result timing = recordedTouchSchedule.calculate(
                        baseStart, duration, candidate.getRepeatCount(),
                        candidate.getRepeatIntervalMs(),
                        GestureDescription.getMaxGestureDuration());
                for (long strokeStart : timing.getStarts()) {
                    builder.addStroke(new GestureDescription.StrokeDescription(
                            path, strokeStart, duration));
                }
                cursor = timing.getEnd();
                strokeCount += candidate.getRepeatCount();
                preparedActions.add(candidate);
            }
        } catch (ArithmeticException | IllegalArgumentException error) {
            fail(session, error.getMessage() == null ? "连续录制时间参数无效"
                    : error.getMessage(), System.currentTimeMillis());
            return false;
        }
        if (preparedActions.isEmpty() || first == null || firstMetrics == null) {
            fail(session, "没有可提交的录制动作", System.currentTimeMillis());
            return false;
        }
        GestureDescription gesture = builder.build();
        logs.append(session, "REPLAY_POSITION", "已预提交回放落点 "
                + Math.round(first.getRelativeX() * firstMetrics.widthPixels) + ","
                + Math.round(first.getRelativeY() * firstMetrics.heightPixels) + " px；连续动作 "
                + preparedActions.size() + " 个；总笔画 " + strokeCount);
        session.setStatus(ExecutionSession.Status.RUNNING);
        store.upsert(session);
        int preparedActionCount = preparedActions.size();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                completeRecordedActions(sessionId, preparedActionCount);
            }

            @Override public void onCancelled(GestureDescription gestureDescription) {
                ExecutionSession fresh = store.findById(sessionId);
                if (fresh != null) fail(fresh, "预提交的真实触摸回放被系统取消",
                        System.currentTimeMillis());
            }
        }, handler);
        if (!accepted) {
            ExecutionSession fresh = store.findById(sessionId);
            if (fresh != null) fail(fresh, "系统未接受预提交的真实触摸回放",
                    System.currentTimeMillis());
        }
        return accepted;
    }

    private Path recordedPath(List<TouchPoint> trace, int width, int height) {
        Path path = new Path();
        TouchPoint first = trace.get(0);
        path.moveTo((float) (first.getRelativeX() * width),
                (float) (first.getRelativeY() * height));
        for (int index = 1; index < trace.size(); index++) {
            TouchPoint point = trace.get(index);
            path.lineTo((float) (point.getRelativeX() * width),
                    (float) (point.getRelativeY() * height));
        }
        return path;
    }

    private void completeRecordedActions(String sessionId, int actionCount) {
        ExecutionSession session = store.findById(sessionId);
        if (session == null || session.getStatus() != ExecutionSession.Status.RUNNING) return;
        if (session.isCancelRequested()) {
            logs.append(session, session.getCancellationStatus(),
                    session.getCancellationReason() + "；未发送后续动作");
            store.remove(sessionId);
            return;
        }
        long now = System.currentTimeMillis();
        for (int index = 0; index < actionCount; index++) {
            RuleAction completed = session.currentAction();
            if (completed == null || completed.getType() != ActionType.RECORDED_TOUCH) {
                fail(session, "连续录制动作状态已变化", now);
                return;
            }
            logs.append(session, "GESTURE_DISPATCHED", "已完成连续录制动作 "
                    + (index + 1) + "/" + actionCount);
            session.advance(now);
        }
        if (session.getStatus() == ExecutionSession.Status.COMPLETED) {
            logs.append(session, "COMPLETED", "规则动作已全部派发");
            store.remove(sessionId);
            return;
        }
        store.upsert(session);
        orchestrator.continueUntilExternalEvent(this, session, now);
        scheduleIfPending(sessionId, 50);
    }

    private interface GestureDispatchListener {
        void onCompleted();
        void onFailed(String reason);
    }

    private void dispatchRecordedTouchRepeated(String sessionId, List<TouchPoint> trace, int width, int height,
                                               int repeatCount, long repeatIntervalMs,
                                               int currentRepeat,
                                               GestureDispatchListener listener) {
        ExecutionSession current = store.findById(sessionId);
        if (current == null || current.isCancelRequested()) {
            listener.onCompleted(); return;
        }
        if (isKeyguardLocked() || current.currentAction() == null
                || RecordedReplayWindowPolicy.switchedAway(current.currentAction().getTargetPackage(),
                    replayWindowPackage, replayWindowChangedAt, current.getUpdatedAt())) {
            listener.onFailed("回放时目标应用已切换或锁屏，已停止后续点击"); return;
        }
        dispatchRecordedTouch(sessionId, trace, width, height, new GestureDispatchListener() {
            @Override public void onCompleted() {
                if (currentRepeat >= repeatCount) {
                    listener.onCompleted();
                    return;
                }
                handler.postDelayed(() -> dispatchRecordedTouchRepeated(sessionId, trace, width, height,
                        repeatCount, repeatIntervalMs, currentRepeat + 1, listener),
                        repeatIntervalMs);
            }

            @Override public void onFailed(String reason) {
                listener.onFailed(reason);
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.N)
    private void dispatchRecordedTouch(List<TouchPoint> trace, int width, int height,
                                       GestureDispatchListener listener) {
        dispatchRecordedTouch(null,trace,width,height,listener);
    }

    @TargetApi(Build.VERSION_CODES.N)
    private void dispatchRecordedTouch(String executionId, List<TouchPoint> trace, int width, int height,
                                       GestureDispatchListener listener) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            listener.onFailed("Android 7.0 以下不支持真实触摸回放");
            return;
        }
        if (trace == null || trace.size() < 2) {
            listener.onFailed("真实触摸轨迹无效");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final List<RecordedTouchGesturePlan.Segment> segments;
            try {
                segments = recordedTouchGesturePlan.segments(trace);
            } catch (IllegalArgumentException error) {
                listener.onFailed(error.getMessage());
                return;
            }
            dispatchContinuedSegment(executionId,segments, 0, null, width, height, listener);
            return;
        }
        Path path = new Path();
        TouchPoint first = trace.get(0);
        path.moveTo((float) (first.getRelativeX() * width),
                (float) (first.getRelativeY() * height));
        for (int index = 1; index < trace.size(); index++) {
            TouchPoint point = trace.get(index);
            path.lineTo((float) (point.getRelativeX() * width),
                    (float) (point.getRelativeY() * height));
        }
        long duration = Math.max(1, trace.get(trace.size() - 1).getElapsedMs());
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                listener.onCompleted();
            }

            @Override public void onCancelled(GestureDescription gestureDescription) {
                listener.onFailed("真实触摸回放被系统取消");
            }
        }, handler);
        if (!accepted) listener.onFailed("系统未接受真实触摸回放");
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void dispatchContinuedSegment(String executionId,List<RecordedTouchGesturePlan.Segment> segments,
                                          int index,
                                          GestureDescription.StrokeDescription previousStroke,
                                          int width, int height,
                                          GestureDispatchListener listener) {
        if (executionId != null) {
            ExecutionSession fresh=store.findById(executionId);
            if(fresh==null || fresh.getStatus()!=ExecutionSession.Status.RUNNING || fresh.isCancelRequested()) {
                listener.onCompleted();return;
            }
        }
        if (index < 0 || index >= segments.size()) {
            listener.onCompleted();
            return;
        }
        RecordedTouchGesturePlan.Segment segment = segments.get(index);
        Path path = new Path();
        path.moveTo((float) (segment.getStart().getRelativeX() * width),
                (float) (segment.getStart().getRelativeY() * height));
        path.lineTo((float) (segment.getEnd().getRelativeX() * width),
                (float) (segment.getEnd().getRelativeY() * height));
        boolean willContinue = index < segments.size() - 1;
        GestureDescription.StrokeDescription stroke = previousStroke == null
                ? new GestureDescription.StrokeDescription(path, 0,
                        segment.getDurationMs(), willContinue)
                : previousStroke.continueStroke(path, 0,
                        segment.getDurationMs(), willContinue);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                if (willContinue) {
                    dispatchContinuedSegment(executionId,segments, index + 1, stroke,
                            width, height, listener);
                } else {
                    listener.onCompleted();
                }
            }

            @Override public void onCancelled(GestureDescription gestureDescription) {
                listener.onFailed("真实触摸分段回放被系统取消");
            }
        }, handler);
        if (!accepted) listener.onFailed("系统未接受真实触摸分段回放");
    }

    private String packageVersion(String packageName) {
        try {
            return getPackageManager().getPackageInfo(packageName, 0).versionName;
        } catch (Exception error) {
            return null;
        }
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
                clickCoordinate(session, action, resolution.getCandidate(), now);
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
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            completeAction(session.getId(), ClickDispatchResult.node());
        } else {
            fail(store.findById(session.getId()), "控件拒绝点击动作",
                    System.currentTimeMillis());
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private void clickCoordinate(ExecutionSession session, RuleAction action,
                                 ClickTargetResolver.NodeCandidate liveCandidate, long now) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            fail(session, "Android 7.0 以下不支持坐标手势", now);
            return;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        double relativeX = action.getRelativeX();
        double relativeY = action.getRelativeY();
        if (hasValidCandidateCoordinates(liveCandidate)) {
            relativeX = liveCandidate.getRelativeX();
            relativeY = liveCandidate.getRelativeY();
        }
        float x = (float) (relativeX * metrics.widthPixels);
        float y = (float) (relativeY * metrics.heightPixels);
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(
                        path, 0, gesturePolicy.durationMs()))
                .build();
        session.setStatus(ExecutionSession.Status.RUNNING);
        store.upsert(session);
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                completeAction(session.getId(), ClickDispatchResult.gesture());
            }

            @Override public void onCancelled(GestureDescription gestureDescription) {
                ExecutionSession fresh = store.findById(session.getId());
                if (fresh != null) fail(fresh, "坐标点击被系统取消", System.currentTimeMillis());
            }
        }, handler);
        if (!accepted) fail(session, "系统未接受坐标点击", now);
    }

    private boolean hasValidCandidateCoordinates(ClickTargetResolver.NodeCandidate candidate) {
        if (candidate == null || candidate.getRelativeX() == null
                || candidate.getRelativeY() == null) return false;
        return candidate.getRelativeX() >= 0d && candidate.getRelativeX() <= 1d
                && candidate.getRelativeY() >= 0d && candidate.getRelativeY() <= 1d;
    }

    private void completeAction(String sessionId, ClickDispatchResult result) {
        ExecutionSession session = store.findById(sessionId);
        if (session == null || session.getStatus() != ExecutionSession.Status.RUNNING) return;
        if (session.isCancelRequested()) {
            logs.append(session, session.getCancellationStatus(), session.getCancellationReason()+"；未发送后续动作");
            store.remove(sessionId); return;
        }
        long now = System.currentTimeMillis();
        logs.append(session, result.getStatus(), result.getMessage());
        session.advance(now);
        if (session.getStatus() == ExecutionSession.Status.COMPLETED) {
            logs.append(session, "COMPLETED", "规则动作已全部派发");
            store.remove(sessionId);
            return;
        }
        store.upsert(session);
        orchestrator.continueUntilExternalEvent(this, session, now);
        scheduleIfPending(sessionId, 50);
    }

    private void fail(ExecutionSession session, String message, long now) {
        ExecutionSession current = store.findById(session.getId());
        if (current == null || !QueuePolicy.live(current)) return;
        if (current.isCancelRequested()) {
            logs.append(current, current.getCancellationStatus(), current.getCancellationReason());
            store.remove(current.getId()); return;
        }
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

}
