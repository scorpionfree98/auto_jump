package com.autojump.app.automation;

/** Keeps transient accessibility events from replacing the actual foreground app. */
final class ForegroundPackageTracker {
    String replayWindow(String previous, String eventPackage, boolean windowStateChanged) {
        if (!windowStateChanged || eventPackage == null || eventPackage.trim().isEmpty()) return previous;
        // Include our own management page and system windows: never retain a stale
        // target package when the user has switched away from it.
        return eventPackage.trim();
    }
    String update(String previousPackage, String eventPackage, String activeRootPackage,
                  String servicePackage, boolean windowStateChanged) {
        String active = normalizedExternal(activeRootPackage, servicePackage);
        if (active != null) return active;

        String event = normalizedExternal(eventPackage, servicePackage);
        if (windowStateChanged && event != null) return event;
        return previousPackage;
    }

    String trustedWindowPackage(String eventPackage, String activeRootPackage,
                                String servicePackage, boolean windowStateChanged) {
        if (!windowStateChanged) return null;
        String event = normalizedExternal(eventPackage, servicePackage);
        String active = normalizedExternal(activeRootPackage, servicePackage);
        return event != null && event.equals(active) ? event : null;
    }

    private String normalizedExternal(String packageName, String servicePackage) {
        if (packageName == null) return null;
        String normalized = packageName.trim();
        if (normalized.isEmpty() || normalized.equals(servicePackage)) return null;
        return normalized;
    }
}
