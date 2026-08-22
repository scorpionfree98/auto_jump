package com.autojump.app.automation;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.*;

public class ClickRecordingPolicyTest {
    private final ClickRecordingPolicy policy = new ClickRecordingPolicy();

    @Test public void inactiveStateDoesNothing() {
        assertEquals(ClickRecordingPolicy.Decision.INACTIVE,
                policy.evaluate(new ClickRecordingStore.State(), "com.example", false, false, 10));
    }

    @Test public void eligibleAppClickCanBeCaptured() {
        ClickRecordingStore.State state = armed(100, 1000);
        assertEquals(ClickRecordingPolicy.Decision.ELIGIBLE,
                policy.evaluate(state, "com.example", false, false, 500));
    }

    @Test public void expiredRequestIsRejected() {
        ClickRecordingStore.State state = armed(100, 1000);
        assertEquals(ClickRecordingPolicy.Decision.EXPIRED,
                policy.evaluate(state, "com.example", false, false, 1000));
    }

    @Test public void passwordAndSystemUiAreRejected() {
        ClickRecordingStore.State state = armed(100, 1000);
        assertEquals(ClickRecordingPolicy.Decision.PASSWORD,
                policy.evaluate(state, "com.example", true, false, 500));
        assertEquals(ClickRecordingPolicy.Decision.DISALLOWED,
                policy.evaluate(state, "com.android.systemui", false, false, 500));
    }

    @Test public void editableOrLongTextIsNotStored() {
        assertNull(policy.safeShortText("account@example.com", true));
        assertNull(policy.safeShortText(repeat("x", 81), false));
        assertEquals("提交", policy.safeShortText(" 提交 ", false));
    }

    private ClickRecordingStore.State armed(long createdAt, long expiresAt) {
        String json = "{\"status\":\"ARMED\",\"requestId\":\"request\"," +
                "\"createdAt\":" + createdAt + ",\"expiresAt\":" + expiresAt + "}";
        return new Gson().fromJson(json, ClickRecordingStore.State.class);
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
