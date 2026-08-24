package com.autojump.app.automation;

import com.autojump.app.domain.TouchPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of capturing and successfully replaying one user touch during recording. */
public final class RecordedTouch {
    private String requestId;
    private String targetPackage;
    private List<TouchPoint> trace;
    private int displayWidth;
    private int displayHeight;
    private int orientation;
    private String targetAppVersion;
    private long delayBeforeMs;
    private long capturedAt;

    public RecordedTouch() {}

    public RecordedTouch(String requestId, String targetPackage, List<TouchPoint> trace,
                         int displayWidth, int displayHeight, int orientation,
                         String targetAppVersion, long delayBeforeMs, long capturedAt) {
        this.requestId = requestId;
        this.targetPackage = targetPackage;
        this.trace = trace == null ? new ArrayList<>() : new ArrayList<>(trace);
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        this.orientation = orientation;
        this.targetAppVersion = targetAppVersion;
        this.delayBeforeMs = delayBeforeMs;
        this.capturedAt = capturedAt;
    }

    public String getRequestId() { return requestId; }
    public String getTargetPackage() { return targetPackage; }
    public List<TouchPoint> getTrace() {
        return trace == null ? Collections.emptyList() : Collections.unmodifiableList(trace);
    }
    public int getDisplayWidth() { return displayWidth; }
    public int getDisplayHeight() { return displayHeight; }
    public int getOrientation() { return orientation; }
    public String getTargetAppVersion() { return targetAppVersion; }
    public long getDelayBeforeMs() { return delayBeforeMs; }
    public long getCapturedAt() { return capturedAt; }
    public long getDurationMs() {
        List<TouchPoint> points = getTrace();
        return points.size() < 2 ? 0 : points.get(points.size() - 1).getElapsedMs();
    }
}
