package com.autojump.app.automation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ForegroundPackageTrackerTest {
    private final ForegroundPackageTracker tracker = new ForegroundPackageTracker();
    private static final String SERVICE = "com.autojump.app";

    @Test public void activeTargetSurvivesOverlaySelfEvent() {
        assertEquals("com.target", tracker.update("com.target", SERVICE,
                "com.target", SERVICE, false));
    }

    @Test public void transientContentEventCannotReplaceForegroundWithoutActiveRoot() {
        assertEquals("com.target", tracker.update("com.target", "com.keyboard",
                null, SERVICE, false));
    }

    @Test public void trustedWindowStateEventCanReplaceForegroundWhenRootIsUnavailable() {
        assertEquals("com.other", tracker.update("com.target", "com.other",
                null, SERVICE, true));
    }

    @Test public void activeRootWinsOverStaleWindowEvent() {
        assertEquals("com.target", tracker.update("com.previous", "com.previous",
                "com.target", SERVICE, true));
    }

    @Test public void realSystemWindowBecomesForeground() {
        assertEquals("com.android.systemui", tracker.update("com.target", "com.target",
                "com.android.systemui", SERVICE, false));
    }

    @Test public void selfEventDoesNotInventForegroundPackage() {
        assertNull(tracker.update(null, SERVICE, SERVICE, SERVICE, true));
    }

    @Test public void matchingWindowEventAndRootAreTrustedForRecording() {
        assertEquals("com.target", tracker.trustedWindowPackage(
                "com.target", "com.target", SERVICE, true));
    }

    @Test public void mismatchedWindowEventAndRootAreNotTrustedForRecording() {
        assertNull(tracker.trustedWindowPackage(
                "com.interference", "com.target", SERVICE, true));
    }

    @Test public void contentChangeIsNotTrustedForRecordingBinding() {
        assertNull(tracker.trustedWindowPackage(
                "com.target", "com.target", SERVICE, false));
    }

    @Test public void serviceWindowIsNotTrustedForRecordingBinding() {
        assertNull(tracker.trustedWindowPackage(SERVICE, SERVICE, SERVICE, true));
    }
}
