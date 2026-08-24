package com.autojump.app.automation;

/** Decides whether semantic node clicking is reliable enough for a matched target. */
public final class ClickTargetPrecisionPolicy {
    private static final double MAX_ANCESTOR_AREA_RATIO = 0.35d;
    private static final long GESTURE_DURATION_MS = 160L;

    public boolean isPrecise(boolean sourceIsClickableNode, double clickableAreaRatio,
                             boolean insideWebView) {
        if (insideWebView) return false;
        return sourceIsClickableNode || clickableAreaRatio <= MAX_ANCESTOR_AREA_RATIO;
    }

    public long gestureDurationMs() {
        return GESTURE_DURATION_MS;
    }
}
