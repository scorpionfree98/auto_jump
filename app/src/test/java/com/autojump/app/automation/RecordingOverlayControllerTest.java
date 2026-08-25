package com.autojump.app.automation;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class RecordingOverlayControllerTest {
    @Test public void captureWindowAllowsTimeForHumanInteraction() {
        assertTrue(RecordingOverlayController.CAPTURE_TIMEOUT_MS >= 120_000L);
        assertTrue(RecordingOverlayController.CAPTURE_TIMEOUT_MS
                <= ClickRecordingStore.DEFAULT_TTL_MS);
    }
}
