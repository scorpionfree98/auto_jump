package com.autojump.app.shizuku;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecoveryAttemptTest {
    @Test public void duplicateConnectionsAreRejectedBeforeCompletion() {
        RecoveryAttempt a = new RecoveryAttempt();
        assertTrue(a.claimConnection()); assertFalse(a.claimConnection());
        assertTrue(a.finish()); assertFalse(a.finish()); assertFalse(a.claimConnection());
    }
    @Test public void timeoutBeforeConnectionRejectsLateBinder() {
        RecoveryAttempt a = new RecoveryAttempt();
        assertTrue(a.finish()); assertFalse(a.claimConnection()); assertFalse(a.finish());
    }
    @Test public void concurrentConnectionEventsClaimOnlyOnce() throws Exception {
        RecoveryAttempt a = new RecoveryAttempt();
        AtomicInteger claims = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            Thread t = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
                if (a.claimConnection()) claims.incrementAndGet();
            });
            threads.add(t); t.start();
        }
        start.countDown(); for (Thread t : threads) t.join(2000L);
        for (Thread t : threads) assertFalse(t.isAlive());
        assertEquals(1, claims.get());
    }
    @Test public void competingTerminalEventsFinishOnlyOnce() throws Exception {
        RecoveryAttempt a = new RecoveryAttempt();
        AtomicInteger completed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            Thread t = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
                if (a.finish()) completed.incrementAndGet();
            });
            threads.add(t); t.start();
        }
        start.countDown(); for (Thread t : threads) t.join(2000L);
        for (Thread t : threads) assertFalse(t.isAlive());
        assertEquals(1, completed.get()); assertFalse(a.claimConnection());
    }
}
