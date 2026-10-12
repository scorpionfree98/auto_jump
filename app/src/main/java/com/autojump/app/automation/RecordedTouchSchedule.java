package com.autojump.app.automation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure timing plan for a recorded gesture submitted before a cross-app launch. */
public final class RecordedTouchSchedule {
    public static final class Result {
        private final List<Long> starts;
        private final long end;

        private Result(List<Long> starts, long end) {
            this.starts = Collections.unmodifiableList(starts);
            this.end = end;
        }

        public List<Long> getStarts() { return starts; }
        public long getEnd() { return end; }
    }

    public Result calculate(long delayMs, long durationMs, int repeatCount,
                            long repeatIntervalMs, long maximumDurationMs) {
        if (delayMs < 0 || durationMs <= 0 || repeatCount < 1 || repeatCount > 5
                || repeatIntervalMs < 0 || maximumDurationMs <= 0) {
            throw new IllegalArgumentException("录制回放时间参数无效");
        }
        List<Long> starts = new ArrayList<>(repeatCount);
        try {
            long stride = Math.addExact(durationMs, repeatIntervalMs);
            for (int repeat = 0; repeat < repeatCount; repeat++) {
                starts.add(Math.addExact(delayMs, Math.multiplyExact((long) repeat, stride)));
            }
            long end = Math.addExact(starts.get(starts.size() - 1), durationMs);
            if (end > maximumDurationMs) {
                throw new IllegalArgumentException("延迟与重复点击总时长超过系统限制");
            }
            return new Result(starts, end);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("录制回放时间参数过大");
        }
    }
}
