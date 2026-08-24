package com.autojump.app.automation;

/** Defines the touch shape used to replay a user-recorded coordinate click. */
public final class ClickGesturePolicy {
    private static final long DURATION_MS = 160L;

    public long durationMs() {
        return DURATION_MS;
    }
}
