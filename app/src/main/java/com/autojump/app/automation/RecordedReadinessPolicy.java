package com.autojump.app.automation;

/** Time-based readiness only; does not claim that a button or business result was recognized. */
public final class RecordedReadinessPolicy {
    public static long readyAt(long targetShownAt, long lastContentChangeAt, long delay, long settle) {
        long base = targetShownAt + Math.max(delay, settle);
        // Clocks/animations can emit content changes forever. They may extend the
        // recorded delay briefly, but must not starve an otherwise valid replay.
        long quietAt = lastContentChangeAt + Math.min(settle, 300L);
        long latest = base + Math.min(settle, 1000L);
        return Math.max(base, Math.min(quietAt, latest));
    }
}
