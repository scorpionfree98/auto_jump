package com.autojump.app.automation;

/** Keeps transient accessibility events from replacing the actual foreground app. */
final class ForegroundPackageTracker {
    String update(String previousPackage, String eventPackage, String activeRootPackage,
                  String servicePackage, boolean windowStateChanged) {
        String active = normalizedExternal(activeRootPackage, servicePackage);
        if (active != null) return active;

        String event = normalizedExternal(eventPackage, servicePackage);
        if (windowStateChanged && event != null) return event;
        return previousPackage;
    }

    private String normalizedExternal(String packageName, String servicePackage) {
        if (packageName == null) return null;
        String normalized = packageName.trim();
        if (normalized.isEmpty() || normalized.equals(servicePackage)) return null;
        return normalized;
    }
}
