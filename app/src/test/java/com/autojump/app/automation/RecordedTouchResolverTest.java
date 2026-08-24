package com.autojump.app.automation;

import com.autojump.app.domain.RuleAction;
import com.autojump.app.domain.TouchPoint;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.*;

public class RecordedTouchResolverTest {
    private final RecordedTouchResolver resolver = new RecordedTouchResolver();
    private final RuleAction action = RuleAction.recordedTouch("com.example", 500, 5000,
            1080, 2400, 1, "1.0", Arrays.asList(
                    new TouchPoint(0.5, 0.7, 0),
                    new TouchPoint(0.502, 0.701, 80),
                    new TouchPoint(0.5, 0.7, 160)));

    @Test
    public void waitsForTargetPackageAndDelay() {
        assertEquals(RecordedTouchResolver.Decision.WAIT_FOR_PACKAGE,
                resolver.resolve(action, "other", 1000, 2000, 1080, 2400, 1).getDecision());
        assertEquals(RecordedTouchResolver.Decision.WAIT_FOR_DELAY,
                resolver.resolve(action, "com.example", 1000, 1200, 1080, 2400, 1).getDecision());
    }

    @Test
    public void replaysWithoutAnyNodeCandidate() {
        RecordedTouchResolver.Result result = resolver.resolve(action, "com.example",
                1000, 1600, 1080, 2400, 1);
        assertEquals(RecordedTouchResolver.Decision.REPLAY, result.getDecision());
        assertEquals(3, result.getTrace().size());
    }

    @Test
    public void rejectsOrientationMismatch() {
        assertEquals(RecordedTouchResolver.Decision.FAIL,
                resolver.resolve(action, "com.example", 1000, 1600,
                        2400, 1080, 2).getDecision());
    }
}
