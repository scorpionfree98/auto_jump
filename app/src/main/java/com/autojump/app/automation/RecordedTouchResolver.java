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
        long readyAt = safeAdd(actionStartedAtMs, Math.max(0, action.getDelayBeforeMs()));
        if (nowMs < readyAt) return new Result(Decision.WAIT_FOR_DELAY, readyAt, null, null);
        if (displayWidth <= 0 || displayHeight <= 0) return fail("当前屏幕尺寸无效");
        if (orientation != action.getRecordedOrientation()) return fail("屏幕方向与录制时不同，请重新录制");
        double recordedRatio = action.getRecordedDisplayWidth()
                / (double) action.getRecordedDisplayHeight();
        double currentRatio = displayWidth / (double) displayHeight;
        if (Math.abs(recordedRatio - currentRatio) / Math.max(0.0001, recordedRatio) > 0.03) {
            return fail("当前屏幕比例与录制时不同，请重新录制");
        }
        if (action.getTouchTrace().size() < 2 || action.getTouchDurationMs() <= 0) {
            return fail("录制触摸轨迹无效");
        }
        return new Result(Decision.REPLAY, 0, action.getTouchTrace(), null);
    }

    private Result fail(String message) {
        return new Result(Decision.FAIL, 0, null, message);
    }

    private long safeAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
