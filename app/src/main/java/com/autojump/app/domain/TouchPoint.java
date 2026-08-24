package com.autojump.app.domain;

import java.io.Serializable;

/** One normalized sample in a user-recorded single-finger touch trace. */
public final class TouchPoint implements Serializable {
    private double relativeX;
    private double relativeY;
    private long elapsedMs;

    public TouchPoint() {}

    public TouchPoint(double relativeX, double relativeY, long elapsedMs) {
        if (relativeX < 0d || relativeX > 1d || relativeY < 0d || relativeY > 1d) {
            throw new IllegalArgumentException("触摸坐标必须在 0 到 1 之间");
        }
        if (elapsedMs < 0) throw new IllegalArgumentException("触摸时间不能为负数");
        this.relativeX = relativeX;
        this.relativeY = relativeY;
        this.elapsedMs = elapsedMs;
    }

    public double getRelativeX() { return relativeX; }
    public double getRelativeY() { return relativeY; }
    public long getElapsedMs() { return elapsedMs; }
}
