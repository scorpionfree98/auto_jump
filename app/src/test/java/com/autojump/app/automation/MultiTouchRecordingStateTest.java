package com.autojump.app.automation;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MultiTouchRecordingStateTest {
    private final Gson gson = new Gson();

    @Test public void legacySingleTouchRemainsReadableAsOneItem() {
        ClickRecordingStore.State state = gson.fromJson(
                "{\"recordedTouch\":{\"requestId\":\"r1\",\"targetPackage\":\"target\"}}",
                ClickRecordingStore.State.class);

        assertEquals(1, state.getRecordedTouches().size());
        assertEquals("r1", state.getRecordedTouch().getRequestId());
    }

    @Test public void multipleTouchesPreserveRecordingOrder() {
        ClickRecordingStore.State state = gson.fromJson(
                "{\"recordedTouches\":["
                        + "{\"requestId\":\"first\",\"targetPackage\":\"target\"},"
                        + "{\"requestId\":\"second\",\"targetPackage\":\"target\"}]}",
                ClickRecordingStore.State.class);

        assertEquals(2, state.getRecordedTouches().size());
        assertEquals("first", state.getRecordedTouches().get(0).getRequestId());
        assertEquals("second", state.getRecordedTouches().get(1).getRequestId());
    }
}
