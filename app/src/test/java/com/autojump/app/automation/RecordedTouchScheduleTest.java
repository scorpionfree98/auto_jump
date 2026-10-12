package com.autojump.app.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import org.junit.Test;

public class RecordedTouchScheduleTest {
    private final RecordedTouchSchedule schedule = new RecordedTouchSchedule();

    @Test public void preservesConfiguredDelayAndRepeatInterval() {
        RecordedTouchSchedule.Result result = schedule.calculate(3060, 67, 2, 800, 60_000);
        assertEquals(Arrays.asList(3060L, 3927L), result.getStarts());
        assertEquals(3994L, result.getEnd());
    }

    @Test public void rejectsPlanBeyondSystemLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> schedule.calculate(59_990, 20, 1, 800, 60_000));
    }

    @Test public void rejectsOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> schedule.calculate(Long.MAX_VALUE, 20, 2, 800, 60_000));
    }
}
