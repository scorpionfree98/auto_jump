package com.autojump.app.automation;

import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.domain.TouchPoint;
import java.util.Collections;
import java.util.List;

/** Determines when a recorded trace can be replayed without consulting accessibility nodes. */
public final class RecordedTouchResolver {
    public enum Decision { WAIT_FOR_PACKAGE, WAIT_FOR_DELAY, REPLAY, FAIL }

    public static final class Result {
        private final Decision decision;
        private final long nextCheckAtMs;
        private final List<TouchPoint> trace;
        private final String message;

        private Result(Decision decision, long nextCheckAtMs, List<TouchPoint> trace,
                       String message) {
            this.decision = decision;
            this.nextCheckAtMs = nextCheckAtMs;
            this.trace = trace == null ? Collections.emptyList() : trace;
            this.message = message;
        }

        public Decision getDecision() { return decision; }
        public long getNextCheckAtMs() { return nextCheckAtMs; }
        public List<TouchPoint> getTrace() { return trace; }
        public String getMessage() { return message; }
    }

    public Result resolve(RuleAction action, String foregroundPackage, long actionStartedAtMs,
                          long nowMs, int displayWidth, int displayHeight, int orientation) {
        if (action == null || action.getType() != ActionType.RECORDED_TOUCH) {
            return fail("当前动作不是真实触摸动作");
        }
        if (!action.getTargetPackage().equals(foregroundPackage)) {
            return new Result(Decision.WAIT_FOR_PACKAGE, nowMs + 200, null, null);
        }
        return resolveAfterDelay(action, actionStartedAtMs, nowMs, displayWidth, displayHeight, orientation);
    }

    /** User-selected timed replay: missing window events do not prevent dispatch. */
    public Result resolveAfterDelay(RuleAction action, long actionStartedAtMs, long nowMs,
                                    int displayWidth, int displayHeight, int orientation) {
        if (action == null || action.getType() != ActionType.RECORDED_TOUCH)
            return fail("当前动作不是真实触摸动作");
        long readyAt = safeAdd(actionStartedAtMs, Math.max(0, action.getDelayBeforeMs()));
        if (nowMs < readyAt) return new Result(Decision.WAIT_FOR_DELAY, readyAt, null, null);
        if (displayWidth <= 0 || displayHeight <= 0) return fail("当前屏幕尺寸无效");
        if (orientation != action.getRecordedOrientation()) return fail("屏幕方向与录制时不同，请重新录制");
        double recordedRatio = action.getRecordedDisplayWidth()
                / (double) action.getRecordedDisplayHeight();
        double currentRatio = displayWidth / (double) displayHeight;
        if (!Double.isFinite(recordedRatio) || recordedRatio <= 0
                || Math.abs(recordedRatio - currentRatio) / recordedRatio > action.getScreenTolerancePercent() / 100d + 1e-9) {
            return fail("屏幕比例变化超过 " + action.getScreenTolerancePercent() + "% 容差，可调整容差或重新录制");
        }
        if (action.getTouchTrace().size() < 2 || action.getTouchTrace().size() > 256
                || action.getTouchDurationMs() <= 0 || action.getTouchDurationMs() > 10_000L) {
            return fail("录制触摸轨迹无效");
        }
        try {
            return new Result(Decision.REPLAY, 0, mappedTrace(action, displayWidth, displayHeight), null);
        } catch (IllegalArgumentException error) { return fail(error.getMessage()); }
    }

    public static List<TouchPoint> mappedTrace(RuleAction action, int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("屏幕尺寸无效");
        java.util.ArrayList<TouchPoint> mapped = new java.util.ArrayList<>();
        for (TouchPoint point : action.getTouchTrace()) {
            double x = point.getRelativeX() + action.getReplayOffsetXPx() / width;
            double y = point.getRelativeY() + action.getReplayOffsetYPx() / height;
            if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1)
                throw new IllegalArgumentException("校准后落点超出屏幕，请减小 X/Y 偏移");
            mapped.add(new TouchPoint(x,y,point.getElapsedMs()));
        }
        return mapped;
    }

    private Result fail(String message) {
        return new Result(Decision.FAIL, 0, null, message);
    }

    private long safeAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
