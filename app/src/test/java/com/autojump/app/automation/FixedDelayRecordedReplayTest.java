package com.autojump.app.automation;
import com.autojump.app.domain.*;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class FixedDelayRecordedReplayTest {
    private RuleAction action() {
        return RuleAction.recordedTouch("target",3060,15000,1440,3032,1,null,
                Arrays.asList(new TouchPoint(.56,.47,0),new TouchPoint(.56,.47,67)));
    }
    @Test public void waitsExactlyConfiguredDelayWithoutWindowOrNodeRecognition() {
        RecordedTouchResolver resolver = new RecordedTouchResolver();
        assertEquals(RecordedTouchResolver.Decision.WAIT_FOR_DELAY,
                resolver.resolveAfterDelay(action(),1000,4059,1440,3032,1).getDecision());
        assertEquals(RecordedTouchResolver.Decision.REPLAY,
                resolver.resolveAfterDelay(action(),1000,4060,1440,3032,1).getDecision());
    }
    @Test public void unknownOrOldForegroundDoesNotBlockButFreshSwitchDoes() {
        assertFalse(RecordedReplayWindowPolicy.switchedAway("target",null,0,1000));
        assertFalse(RecordedReplayWindowPolicy.switchedAway("target","launcher",999,1000));
        assertTrue(RecordedReplayWindowPolicy.switchedAway("target","launcher",1001,1000));
        assertTrue(RecordedReplayWindowPolicy.switchedAway("target","com.android.systemui",1001,1000));
        assertFalse(RecordedReplayWindowPolicy.switchedAway("target","target",1001,1000));
    }
    @Test public void timedModeStillRejectsRotationAndOutOfBounds() {
        RecordedTouchResolver resolver = new RecordedTouchResolver();
        assertEquals(RecordedTouchResolver.Decision.FAIL,
                resolver.resolveAfterDelay(action(),1000,4060,1440,3032,2).getDecision());
        RuleAction a = action(); a.setReplayOffsets(-500,0);
        assertEquals(RecordedTouchResolver.Decision.FAIL,
                resolver.resolveAfterDelay(a,1000,4060,100,211,1).getDecision());
    }
}
