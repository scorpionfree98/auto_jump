package com.autojump.app.automation;

import com.autojump.app.domain.TouchPoint;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class RecordingDisplayCoordinatesTest {
    @Test public void statusBarOffsetDoesNotMoveRecordedButton() {
        List<TouchPoint> trace = new TouchTracePolicy().normalize(Arrays.asList(
                RecordingDisplayCoordinates.point(879, 2516, 0, 168, 1000),
                RecordingDisplayCoordinates.point(879, 2516, 0, 168, 1160)), 1440, 3200);
        assertEquals(879, trace.get(0).getRelativeX() * 1440, 0.001);
        assertEquals(2684, trace.get(0).getRelativeY() * 3200, 0.001);
        assertEquals(160, trace.get(1).getElapsedMs());
    }
    @Test public void historicalMovesUseTheSameOriginAsCurrentSample() {
        TouchTracePolicy.RawPoint point = RecordingDisplayCoordinates.point(400, 600, 20, 168, 1010);
        assertEquals(420, point.getX(), 0);
        assertEquals(768, point.getY(), 0);
        assertEquals(1010, point.getEventTimeMs());
    }
    @Test public void edgeToEdgeCaptureDoesNotAddAnOffset() {
        TouchTracePolicy.RawPoint point = RecordingDisplayCoordinates.point(400, 600, 0, 0, 1010);
        assertEquals(400, point.getX(), 0);
        assertEquals(600, point.getY(), 0);
    }
}
