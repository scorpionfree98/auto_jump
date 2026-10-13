package com.autojump.app.automation;

import com.autojump.app.bean.Rule;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class AccessibilityExecutionGateTest {
    private static class Fake implements AccessibilityExecutionGate.Environment {
        boolean connected;
        boolean accept = true;
        AccessibilityExecutionGate.Availability available = AccessibilityExecutionGate.Availability.READY;
        final ArrayDeque<Runnable> timers = new ArrayDeque<>();
        final List<String> queued = new ArrayList<>();
        final List<Boolean> results = new ArrayList<>();
        AccessibilityExecutionGate.Result restoration;
        int restores;
        @Override public boolean connected() { return connected; }
        @Override public AccessibilityExecutionGate.Availability availability() { return available; }
        @Override public void later(Runnable task, long delay) { assertEquals(300L, delay); timers.add(task); }
        @Override public void restore(AccessibilityExecutionGate.Result result) { restores++; restoration = result; }
        @Override public boolean enqueue(Rule rule) { if (accept) queued.add(rule.getId()); return accept; }
        void result(boolean success, String message) { results.add(success); }
        void drain() { int count = 0; while (!timers.isEmpty()) { assertTrue(++count <= 10); timers.remove().run(); } }
    }
    private Rule rule(String id) { return new Rule(id, id); }

    @Test public void pureLinkRunsWithoutServiceOrShizuku() {
        Fake f = new Fake(); f.available = AccessibilityExecutionGate.Availability.NOT_RUNNING;
        new AccessibilityExecutionGate(f).run(rule("a"), false, f::result);
        assertEquals(List.of("a"), f.queued); assertTrue(f.timers.isEmpty()); assertEquals(0, f.restores);
    }
    @Test public void connectedServiceRunsImmediately() {
        Fake f = new Fake(); f.connected = true;
        new AccessibilityExecutionGate(f).run(rule("a"), true, f::result);
        assertEquals(List.of("a"), f.queued); assertEquals(0, f.restores);
    }
    @Test public void rebindCompletesWithoutRecovery() {
        Fake f = new Fake(); AccessibilityExecutionGate g = new AccessibilityExecutionGate(f);
        g.run(rule("a"), true, f::result); assertTrue(f.queued.isEmpty());
        f.connected = true; f.drain(); assertEquals(List.of("a"), f.queued); assertEquals(0, f.restores);
    }
    @Test public void disconnectedServiceNeverQueuesEvenWhenRestoreReportsSuccess() {
        Fake f = new Fake(); new AccessibilityExecutionGate(f).run(rule("a"), true, f::result);
        f.drain(); f.restoration.complete(true, "settings updated");
        assertTrue(f.queued.isEmpty()); assertEquals(List.of(false), f.results);
    }
    @Test public void successfulRecoveryQueuesInOrder() {
        Fake f = new Fake(); AccessibilityExecutionGate g = new AccessibilityExecutionGate(f);
        g.run(rule("a"), true, f::result); g.run(rule("b"), true, f::result);
        f.drain(); assertEquals(1, f.restores); assertTrue(f.queued.isEmpty());
        f.connected = true; f.restoration.complete(true, "restored");
        assertEquals(List.of("a", "b"), f.queued); assertEquals(List.of(true, true), f.results);
    }
    @Test public void failureDoesNotQueueAndCanRetry() {
        Fake f = new Fake(); AccessibilityExecutionGate g = new AccessibilityExecutionGate(f);
        g.run(rule("a"), true, f::result); f.drain(); f.restoration.complete(false, "timeout");
        assertTrue(f.queued.isEmpty()); g.run(rule("a"), true, f::result); f.drain();
        assertEquals(2, f.restores);
    }
    @Test public void unavailableShizukuFailsAfterBoundedWait() {
        for (AccessibilityExecutionGate.Availability a : List.of(
                AccessibilityExecutionGate.Availability.NOT_RUNNING,
                AccessibilityExecutionGate.Availability.PERMISSION_REQUIRED)) {
            Fake f = new Fake(); f.available = a;
            new AccessibilityExecutionGate(f).run(rule("a"), true, f::result); f.drain();
            assertEquals(List.of(false), f.results); assertTrue(f.queued.isEmpty()); assertEquals(0, f.restores);
        }
    }
    @Test public void duplicateTriggersMergeWhileWaiting() {
        Fake f = new Fake(); AccessibilityExecutionGate g = new AccessibilityExecutionGate(f);
        g.run(rule("a"), true, f::result); g.run(rule("a"), true, f::result);
        f.drain(); f.connected = true; f.restoration.complete(true, "restored");
        assertEquals(List.of("a"), f.queued); assertEquals(List.of(false, true), f.results);
    }
    @Test public void duplicateLateCallbackCannotFinishNextRequest() {
        Fake f = new Fake(); AccessibilityExecutionGate g = new AccessibilityExecutionGate(f);
        g.run(rule("a"), true, f::result); f.drain();
        AccessibilityExecutionGate.Result old = f.restoration;
        old.complete(false, "failed"); g.run(rule("b"), true, f::result); f.drain();
        f.connected = true; old.complete(true, "late"); assertTrue(f.queued.isEmpty());
        f.restoration.complete(true, "current"); assertEquals(List.of("b"), f.queued);
    }
    @Test public void waitingCapacityIsBounded() {
        Fake f = new Fake(); AccessibilityExecutionGate g = new AccessibilityExecutionGate(f);
        for (int i = 0; i < 21; i++) g.run(rule("r" + i), true, f::result);
        assertEquals(List.of(false), f.results); f.drain(); f.connected = true;
        f.restoration.complete(true, "restored"); assertEquals(20, f.queued.size());
    }
    @Test public void enqueueFailureIsReported() {
        Fake f = new Fake(); f.connected = true; f.accept = false;
        new AccessibilityExecutionGate(f).run(rule("a"), true, f::result);
        assertEquals(List.of(false), f.results);
    }
    @Test public void cancelDuringRecoveryRejectsLateSuccess() {
        Fake f = new Fake(); AccessibilityExecutionGate g = new AccessibilityExecutionGate(f);
        g.run(rule("a"), true, f::result); f.drain();
        AccessibilityExecutionGate.Result old = f.restoration;
        g.cancelAll(); f.connected = true; old.complete(true, "late success");
        assertTrue(f.queued.isEmpty()); assertEquals(List.of(false), f.results);
        g.run(rule("b"), true, f::result); assertEquals(List.of("b"), f.queued);
    }
    @Test public void cancelDuringRebindWaitInvalidatesTimer() {
        Fake f = new Fake(); AccessibilityExecutionGate g = new AccessibilityExecutionGate(f);
        g.run(rule("a"), true, f::result); g.cancelAll(); f.connected = true; f.drain();
        assertTrue(f.queued.isEmpty()); assertEquals(0, f.restores);
    }
}
