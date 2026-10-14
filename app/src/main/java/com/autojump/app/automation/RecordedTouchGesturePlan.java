package com.autojump.app.automation;

import com.autojump.app.domain.TouchPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Converts recorded samples into continuous gesture segments without losing stationary MOVE timing. */
public final class RecordedTouchGesturePlan {
    public static final class Segment {
        private final TouchPoint start;
        private final TouchPoint end;
        private final long durationMs;

        Segment(TouchPoint start, TouchPoint end, long durationMs) {
            this.start = start;
            this.end = end;
            this.durationMs = durationMs;
        }

        public TouchPoint getStart() { return start; }
        public TouchPoint getEnd() { return end; }
        public long getDurationMs() { return durationMs; }
    }

    public List<Segment> segments(List<TouchPoint> trace) {
        if (trace == null || trace.size() < 2) {
            throw new IllegalArgumentException("真实触摸轨迹至少需要两个样本");
        }
        TouchPoint first = trace.get(0);
        boolean stationary = first != null;
        for (TouchPoint point : trace) {
            if (point == null) throw new IllegalArgumentException("真实触摸轨迹不能包含空样本");
            stationary &= first.getRelativeX() == point.getRelativeX()
                    && first.getRelativeY() == point.getRelativeY();
        }
        // A stationary continuation with neither DOWN nor UP produces no
        // MotionEvents in Android's injector and is reported as cancelled.
        // Keep the entire press duration in one stroke; moving traces retain
        // their sample timing and historical behavior.
        if (stationary) {
            TouchPoint last = trace.get(trace.size() - 1);
            return Collections.singletonList(new Segment(first, last,
                    Math.max(1, last.getElapsedMs() - first.getElapsedMs())));
        }
        List<Segment> result = new ArrayList<>(trace.size() - 1);
        for (int index = 1; index < trace.size(); index++) {
            TouchPoint start = trace.get(index - 1);
            TouchPoint end = trace.get(index);
            if (start == null || end == null) {
                throw new IllegalArgumentException("真实触摸轨迹不能包含空样本");
            }
            long duration = Math.max(1, end.getElapsedMs() - start.getElapsedMs());
            result.add(new Segment(start, end, duration));
        }
        return Collections.unmodifiableList(result);
    }
}
