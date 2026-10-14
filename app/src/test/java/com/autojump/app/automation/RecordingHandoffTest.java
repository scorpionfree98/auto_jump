package com.autojump.app.automation;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecordingHandoffTest {
    private static final class FakeScheduler implements RecordingHandoff.Scheduler {
        Runnable queued;
        Runnable removed;
        long delay;
        @Override public void post(Runnable action, long delayMs) {
            queued = action;
            delay = delayMs;
        }
        @Override public void remove(Runnable action) { removed = action; }
    }

    @Test public void replayDoesNotRunInsideTheCaptureCallback() {
        FakeScheduler scheduler = new FakeScheduler();
        RecordingHandoff gate = new RecordingHandoff(scheduler);
        int[] calls = {0};
        gate.defer(RecordingHandoff.BEFORE_REPLAY_MS, () -> calls[0]++);
        assertEquals(0, calls[0]);
        assertEquals(250L, scheduler.delay);
        scheduler.queued.run();
        scheduler.queued.run();
        assertEquals(1, calls[0]);
    }

    @Test public void hidingOrDisconnectingPreventsStaleReplay() {
        FakeScheduler scheduler = new FakeScheduler();
        RecordingHandoff gate = new RecordingHandoff(scheduler);
        int[] calls = {0};
        gate.defer(250L, () -> calls[0]++);
        Runnable old = scheduler.queued;
        gate.cancel();
        assertSame(old, scheduler.removed);
        old.run();
        assertEquals(0, calls[0]);
    }

    @Test public void newCaptureInvalidatesPreviousCapture() {
        FakeScheduler scheduler = new FakeScheduler();
        RecordingHandoff gate = new RecordingHandoff(scheduler);
        int[] calls = {0};
        gate.defer(250L, () -> calls[0] += 100);
        Runnable old = scheduler.queued;
        gate.defer(400L, () -> calls[0]++);
        old.run();
        scheduler.queued.run();
        assertEquals(1, calls[0]);
    }

    @Test public void confirmationLeavesTimeForTheTargetToHandleUp() {
        assertTrue(RecordingHandoff.BEFORE_CONFIRMATION_MS >= 300L);
        assertTrue(RecordingHandoff.BEFORE_CONFIRMATION_MS <= 1000L);
    }
}
