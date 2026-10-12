package com.autojump.app.automation;

/** Timed replay assumes the requested link opened; fresh contrary events stop it. */
public final class RecordedReplayWindowPolicy {
    public static boolean switchedAway(String target, String observed, long eventAt, long startedAt) {
        return observed != null && eventAt >= startedAt && !observed.equals(target);
    }
}
