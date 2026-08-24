package com.autojump.app.automation;

import com.autojump.app.domain.TouchPoint;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class TouchTracePolicyTest {
    private final TouchTracePolicy policy = new TouchTracePolicy();

    @Test
    public void normalizesCoordinatesAndTimeFromDown() {
        List<TouchPoint> trace = policy.normalize(Arrays.asList(
                new TouchTracePolicy.RawPoint(540, 1800, 1000),
                new TouchTracePolicy.RawPoint(544, 1802, 1080),
                new TouchTracePolicy.RawPoint(542, 1800, 1164)), 1080, 2400);

        assertEquals(3, trace.size());
        assertEquals(0d, trace.get(0).getElapsedMs(), 0d);
        assertEquals(164d, trace.get(2).getElapsedMs(), 0d);
        assertEquals(0.5, trace.get(0).getRelativeX(), 0.0001);
        assertEquals(0.75, trace.get(0).getRelativeY(), 0.0001);
    }

    @Test
    public void preservesStationaryMoveSamplesAndReleaseTiming() {
        List<TouchPoint> trace = policy.normalize(Arrays.asList(
                new TouchTracePolicy.RawPoint(10, 20, 100),
                new TouchTracePolicy.RawPoint(10, 20, 120),
                new TouchTracePolicy.RawPoint(10, 20, 140)), 100, 100);

        assertEquals(3, trace.size());
        assertEquals(0L, trace.get(0).getElapsedMs());
        assertEquals(20L, trace.get(1).getElapsedMs());
        assertEquals(40L, trace.get(2).getElapsedMs());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroDurationTrace() {
        policy.normalize(Arrays.asList(
                new TouchTracePolicy.RawPoint(10, 20, 100),
                new TouchTracePolicy.RawPoint(10, 20, 100)), 100, 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfScreenPoint() {
        policy.normalize(Arrays.asList(
                new TouchTracePolicy.RawPoint(10, 20, 100),
                new TouchTracePolicy.RawPoint(101, 20, 140)), 100, 100);
    }
}
