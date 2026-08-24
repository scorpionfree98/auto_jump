package com.autojump.app.automation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClickGesturePolicyTest {
    @Test
    public void durationMatchesSuccessfulDeviceTouch() {
        assertEquals(160L, new ClickGesturePolicy().durationMs());
    }
}
