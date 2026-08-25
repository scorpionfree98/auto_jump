package com.autojump.app.automation;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScreenWakePolicyTest {
    @Test public void wakesWhenDisplayIsNotInteractive() {
        assertTrue(ScreenWakePolicy.shouldAcquireWakeLock(false));
    }

    @Test public void skipsWakeLockWhenDisplayIsAlreadyInteractive() {
        assertFalse(ScreenWakePolicy.shouldAcquireWakeLock(true));
    }

    @Test public void timeoutIsLongEnoughForAuthenticationButStillBounded() {
        assertTrue(ScreenWakePolicy.WAKE_TIMEOUT_MS >= 15_000L);
        assertTrue(ScreenWakePolicy.WAKE_TIMEOUT_MS <= 60_000L);
    }
}
