package com.autojump.app.automation;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ForegroundQueueDispatchTest {
    @Test public void dispatchesSynchronouslyAfterPersistence() {
        List<String> events = new ArrayList<>();
        boolean accepted = ForegroundQueueDispatch.run(() -> {
            events.add("persisted");
            return true;
        }, () -> events.add("dispatched"));
        events.add("callback");
        assertTrue(accepted);
        assertEquals(List.of("persisted", "dispatched", "callback"), events);
    }

    @Test public void rejectedPersistenceDoesNotDispatch() {
        List<String> events = new ArrayList<>();
        assertFalse(ForegroundQueueDispatch.run(() -> false,
                () -> events.add("dispatched")));
        assertTrue(events.isEmpty());
    }
}
