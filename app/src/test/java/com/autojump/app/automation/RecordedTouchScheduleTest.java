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

    @Test public void consecutiveActionsStartAfterPreviousActionEnds() {
        RecordedTouchSchedule.Result first = schedule.calculate(
                1000, 100, 1, 800, 60_000);
        RecordedTouchSchedule.Result second = schedule.calculate(
                first.getEnd() + 500, 200, 2, 300, 60_000);

        assertEquals(Arrays.asList(1000L), first.getStarts());
        assertEquals(Arrays.asList(1600L, 2100L), second.getStarts());
        assertEquals(2300L, second.getEnd());
    }
}
