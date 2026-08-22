package com.autojump.app.automation;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

/** Reads the exact enabled state of this app's accessibility service. */
public final class AccessibilityServiceState {
    private AccessibilityServiceState() {}

    public static boolean isEnabled(Context context) {
        String enabledServices = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) return false;
        ComponentName expected = new ComponentName(context, AutoJumpAccessibilityService.class);
        for (String value : enabledServices.split(":")) {
            ComponentName current = ComponentName.unflattenFromString(value);
            if (expected.equals(current)) return true;
        }
        return false;
    }
}
