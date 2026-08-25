package com.autojump.app.automation;

/** Pure policy for the short, bounded wake lock used before system authentication. */
final class ScreenWakePolicy {
    static final long WAKE_TIMEOUT_MS = 30_000L;

    private ScreenWakePolicy() {}

    static boolean shouldAcquireWakeLock(boolean displayInteractive) {
        return !displayInteractive;
    }
}
