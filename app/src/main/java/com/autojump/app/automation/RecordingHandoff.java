package com.autojump.app.automation;

/** Defers input-window transitions; cancellation invalidates even already queued callbacks. */
final class RecordingHandoff {
    static final long BEFORE_REPLAY_MS = 250L;
    static final long BEFORE_CONFIRMATION_MS = 400L;

    interface Scheduler {
        void post(Runnable action, long delayMs);
        void remove(Runnable action);
    }

    private final Scheduler scheduler;
    private Runnable pending;
    private long generation;

    RecordingHandoff(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    void defer(long delayMs, Runnable action) {
        cancel();
        final long expected = generation;
        pending = () -> {
            if (expected != generation) return;
            pending = null;
            generation++;
            action.run();
        };
        scheduler.post(pending, delayMs);
    }

    void cancel() {
        generation++;
        if (pending != null) scheduler.remove(pending);
        pending = null;
    }
}
