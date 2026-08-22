package com.autojump.app.automation;

public class ClickRecordingPolicy {
    public enum Decision { INACTIVE, ELIGIBLE, EXPIRED, PASSWORD, DISALLOWED }
    public enum StartDecision { INACTIVE, WAIT, ELIGIBLE, EXPIRED }

    public StartDecision evaluateStart(ClickRecordingStore.State state, String packageName,
                                       String servicePackage, boolean launcher, long now) {
        if (state == null || state.getStatus() != ClickRecordingStore.Status.WAITING_START) {
            return StartDecision.INACTIVE;
        }
        if (now >= state.getExpiresAt()) return StartDecision.EXPIRED;
        if (launcher || isSystemPackage(packageName) || same(packageName, servicePackage)) {
            return StartDecision.WAIT;
        }
        String boundPackage = state.getTargetPackage();
        if (boundPackage != null && !boundPackage.equals(packageName)) return StartDecision.WAIT;
        return packageName == null || packageName.trim().isEmpty()
                ? StartDecision.WAIT : StartDecision.ELIGIBLE;
    }

    public Decision evaluate(ClickRecordingStore.State state, String packageName,
                             boolean password, boolean launcher, long now) {
        if (state == null || state.getStatus() != ClickRecordingStore.Status.ARMED) {
            return Decision.INACTIVE;
        }
        if (now >= state.getExpiresAt()) return Decision.EXPIRED;
        if (password) return Decision.PASSWORD;
        if (launcher || isSystemPackage(packageName)) return Decision.DISALLOWED;
        if (state.getTargetPackage() != null
                && !state.getTargetPackage().equals(packageName)) return Decision.DISALLOWED;
        return packageName == null || packageName.trim().isEmpty()
                ? Decision.DISALLOWED : Decision.ELIGIBLE;
    }

    public String safeShortText(CharSequence value, boolean editable) {
        if (value == null || editable) return null;
        String text = value.toString().trim();
        if (text.isEmpty() || text.length() > 80) return null;
        return text;
    }

    public long elapsedSinceStart(ClickRecordingStore.State state, long now) {
        if (state == null || state.getStartedAt() <= 0) return 0;
        return Math.max(0, now - state.getStartedAt());
    }

    private boolean isSystemPackage(String packageName) {
        return "com.android.systemui".equals(packageName)
                || "com.android.permissioncontroller".equals(packageName)
                || "com.google.android.permissioncontroller".equals(packageName)
                || "com.android.packageinstaller".equals(packageName)
                || "com.android.intentresolver".equals(packageName)
                || "android".equals(packageName);
    }

    private boolean same(String left, String right) {
        return left != null && left.equals(right);
    }
}
