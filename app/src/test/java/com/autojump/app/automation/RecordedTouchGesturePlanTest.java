package com.autojump.app.automation;

import com.autojump.app.domain.TouchPoint;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class RecordedTouchGesturePlanTest {
    private final RecordedTouchGesturePlan plan = new RecordedTouchGesturePlan();

    @Test
    public void preservesPerSegmentTimingAndStationaryMoves() {
        List<RecordedTouchGesturePlan.Segment> segments = plan.segments(Arrays.asList(
                new TouchPoint(0.5, 0.7, 0),
                new TouchPoint(0.5, 0.7, 42),
                new TouchPoint(0.503, 0.701, 91),
                new TouchPoint(0.5, 0.7, 164)));

        assertEquals(3, segments.size());
        assertEquals(42L, segments.get(0).getDurationMs());
        assertEquals(49L, segments.get(1).getDurationMs());
        assertEquals(73L, segments.get(2).getDurationMs());
        assertEquals(segments.get(0).getEnd(), segments.get(1).getStart());
        assertEquals(0.5, segments.get(0).getStart().getRelativeX(), 0.0001);
        assertEquals(0.5, segments.get(0).getEnd().getRelativeX(), 0.0001);
    }

    @Test
    public void usesMinimumPositiveDurationForSameTimestampSamples() {
        List<RecordedTouchGesturePlan.Segment> segments = plan.segments(Arrays.asList(
                new TouchPoint(0.5, 0.7, 0),
                new TouchPoint(0.501, 0.7, 0),
                new TouchPoint(0.5, 0.7, 10)));

        assertEquals(1L, segments.get(0).getDurationMs());
        assertEquals(10L, segments.get(1).getDurationMs());
    }
}
