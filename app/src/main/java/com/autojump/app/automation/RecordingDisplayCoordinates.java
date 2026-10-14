package com.autojump.app.automation;

/** Capture windows can start below status bars; replay always uses full-display coordinates. */
final class RecordingDisplayCoordinates {
    private RecordingDisplayCoordinates() {}

    static TouchTracePolicy.RawPoint point(float localX, float localY,
                                           float originX, float originY, long timeMs) {
        return new TouchTracePolicy.RawPoint(localX + originX, localY + originY, timeMs);
    }
}
