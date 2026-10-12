package com.autojump.app.automation;

/** Preserve a legacy recording's exact pixel space across the API35 metrics change. */
public final class RecordedViewportPolicy {
    public static int height(int recordedWidth, int recordedHeight, int currentWidth,
                             int currentHeight, int statusBarHeight) {
        if (recordedWidth == currentWidth && recordedHeight > 0 && statusBarHeight > 0
                && currentHeight - recordedHeight == statusBarHeight) return recordedHeight;
        return currentHeight;
    }
}
