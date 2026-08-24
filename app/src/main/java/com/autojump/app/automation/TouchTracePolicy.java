package com.autojump.app.automation;

import com.autojump.app.domain.TouchPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure validation and normalization for one captured single-finger touch trace. */
public final class TouchTracePolicy {
    public static final long MAX_DURATION_MS = 10_000L;
    public static final int MAX_POINTS = 256;

    public static final class RawPoint {
        private final float x;
        private final float y;
        private final long eventTimeMs;

        public RawPoint(float x, float y, long eventTimeMs) {
            this.x = x;
            this.y = y;
            this.eventTimeMs = eventTimeMs;
        }

        public float getX() { return x; }
        public float getY() { return y; }
        public long getEventTimeMs() { return eventTimeMs; }
    }

    public List<TouchPoint> normalize(List<RawPoint> samples, int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("录制屏幕尺寸无效");
        if (samples == null || samples.size() < 2) {
            throw new IllegalArgumentException("没有捕获完整的按下和抬起动作");
        }
        long start = samples.get(0).getEventTimeMs();
        long end = samples.get(samples.size() - 1).getEventTimeMs();
        if (end <= start) throw new IllegalArgumentException("触摸时间过短，请重新录制");
        if (end - start > MAX_DURATION_MS) throw new IllegalArgumentException("触摸时间过长，请重新录制");

        List<RawPoint> compact = compact(samples);
        List<TouchPoint> result = new ArrayList<>(compact.size());
        long previousTime = start;
        for (RawPoint sample : compact) {
            if (sample.getX() < 0 || sample.getX() >= width
                    || sample.getY() < 0 || sample.getY() >= height) {
                throw new IllegalArgumentException("触摸轨迹超出屏幕范围");
            }
            if (sample.getEventTimeMs() < previousTime) {
                throw new IllegalArgumentException("触摸事件时间顺序无效");
            }
            previousTime = sample.getEventTimeMs();
            result.add(new TouchPoint(sample.getX() / width, sample.getY() / height,
                    sample.getEventTimeMs() - start));
        }
        return Collections.unmodifiableList(result);
    }

    private List<RawPoint> compact(List<RawPoint> samples) {
        if (samples.size() <= MAX_POINTS) return new ArrayList<>(samples);
        List<RawPoint> result = new ArrayList<>(MAX_POINTS);
        result.add(samples.get(0));
        int lastIndex = samples.size() - 1;
        int stride = Math.max(1, (samples.size() - 2 + MAX_POINTS - 3) / (MAX_POINTS - 2));
        for (int index = 1; index < lastIndex && result.size() < MAX_POINTS - 1;
             index += stride) {
            result.add(samples.get(index));
        }
        result.add(samples.get(lastIndex));
        return result;
    }
}
