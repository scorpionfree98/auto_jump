package com.autojump.app.automation;

public class ClickRecordingPolicy {
    public enum Decision { INACTIVE, ELIGIBLE, EXPIRED, PASSWORD, DISALLOWED }

    public Decision evaluate(ClickRecordingStore.State state, String packageName,
                             boolean password, boolean launcher, long now) {
        if (state == null || state.getStatus() != ClickRecordingStore.Status.ARMED) {
            return Decision.INACTIVE;
        }
        if (now >= state.getExpiresAt()) return Decision.EXPIRED;
        if (password) return Decision.PASSWORD;
        if (launcher || isSystemPackage(packageName)) return Decision.DISALLOWED;
        return packageName == null || packageName.trim().isEmpty()
                ? Decision.DISALLOWED : Decision.ELIGIBLE;
    }

    public String safeShortText(CharSequence value, boolean editable) {
        if (value == null || editable) return null;
        String text = value.toString().trim();
        if (text.isEmpty() || text.length() > 80) return null;
        return text;
    }

    private boolean isSystemPackage(String packageName) {
        return "com.android.systemui".equals(packageName)
                || "com.android.permissioncontroller".equals(packageName)
                || "com.google.android.permissioncontroller".equals(packageName)
                || "com.android.packageinstaller".equals(packageName);
    }
}
