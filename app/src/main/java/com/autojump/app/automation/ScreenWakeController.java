package com.autojump.app.automation;

import android.content.Context;
import android.os.PowerManager;

/** Wakes a sleeping display long enough for Android's trusted keyguard authentication. */
final class ScreenWakeController {
    private static final String WAKE_LOCK_TAG = "AutoJump:SystemUnlock";

    @SuppressWarnings("deprecation")
    boolean wakeForSystemUnlock(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager == null
                || !ScreenWakePolicy.shouldAcquireWakeLock(powerManager.isInteractive())) {
            return false;
        }

        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE,
                WAKE_LOCK_TAG);
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(ScreenWakePolicy.WAKE_TIMEOUT_MS);
        return true;
    }
}
