package com.autojump.app.domain;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.*;

public class RecordedTouchActionTest {
    @Test
    public void acceptsNormalizedMonotonicTouchTrace() {
        RuleAction action = RuleAction.recordedTouch("com.example", 500, 5000,
                1080, 2400, 1, "1.2.3", Arrays.asList(
                        new TouchPoint(0.50, 0.70, 0),
                        new TouchPoint(0.503, 0.701, 80),
                        new TouchPoint(0.50, 0.70, 164)));

        assertEquals(ActionType.RECORDED_TOUCH, action.getType());
        assertEquals(3, action.getTouchTrace().size());
        assertEquals(164L, action.getTouchDurationMs());
        assertEquals(1080, action.getRecordedDisplayWidth());
        assertEquals("1.2.3", action.getTargetAppVersion());
        assertEquals(1, action.getRepeatCount());
        assertEquals(800L, action.getRepeatIntervalMs());
    }

    @Test public void configuresBoundedRepeatedReplay() {
        RuleAction action = RuleAction.recordedTouch("com.example", 500, 5000,
                1080, 2400, 1, "1.2.3", Arrays.asList(
                        new TouchPoint(0.50, 0.70, 0),
                        new TouchPoint(0.50, 0.70, 67)));

        action.setRepeatCount(2);
        action.setRepeatIntervalMs(800);

        assertEquals(2, action.getRepeatCount());
        assertEquals(800L, action.getRepeatIntervalMs());
        assertTrue(action.getDescription().contains("回放2次"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsExcessiveRepeatCount() {
        RuleAction action = RuleAction.recordedTouch("com.example", 0, 5000,
                1080, 2400, 1, null, Arrays.asList(
                        new TouchPoint(0.5, 0.7, 0), new TouchPoint(0.5, 0.7, 67)));
        action.setRepeatCount(6);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTraceWithOnePoint() {
        RuleAction.recordedTouch("com.example", 0, 5000, 1080, 2400, 1, null,
                Arrays.asList(new TouchPoint(0.5, 0.7, 0)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonMonotonicTrace() {
        RuleAction.recordedTouch("com.example", 0, 5000, 1080, 2400, 1, null,
                Arrays.asList(new TouchPoint(0.5, 0.7, 20),
                        new TouchPoint(0.5, 0.7, 10)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfBoundsPoint() {
        new TouchPoint(1.01, 0.5, 0);
    }
}
