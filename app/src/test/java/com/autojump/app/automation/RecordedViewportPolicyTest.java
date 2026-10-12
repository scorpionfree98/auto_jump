package com.autojump.app.automation;
import org.junit.Test;
import static org.junit.Assert.*;
public class RecordedViewportPolicyTest {
    @Test public void exactStatusBarDifferencePreservesLegacyPixels() {
        assertEquals(3032, RecordedViewportPolicy.height(1440,3032,1440,3200,168));
    }
    @Test public void anotherScreenOrInsetDifferenceIsNotAccepted() {
        assertEquals(3200, RecordedViewportPolicy.height(1080,3032,1440,3200,168));
        assertEquals(3200, RecordedViewportPolicy.height(1440,2900,1440,3200,168));
    }
    @Test public void fullScreenNewRecordingStaysFullScreen() {
        assertEquals(3200, RecordedViewportPolicy.height(1440,3200,1440,3200,168));
    }
}
