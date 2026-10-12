package com.autojump.app.automation;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReplayWindowTrackingTest {
    @Test public void switchesAwayIncludingManagementAndSystemWindows() {
        ForegroundPackageTracker tracker = new ForegroundPackageTracker();
        assertEquals("target", tracker.replayWindow(null,"target",true));
        assertEquals("cn.local.toolkit", tracker.replayWindow("target","cn.local.toolkit",true));
        assertEquals("com.android.systemui", tracker.replayWindow("target","com.android.systemui",true));
    }
    @Test public void backgroundContentEventsDoNotChangeForeground() {
        ForegroundPackageTracker tracker = new ForegroundPackageTracker();
        assertEquals("target",tracker.replayWindow("target","other",false));
        assertEquals("target",tracker.replayWindow("target",null,true));
        assertNull(tracker.replayWindow(null,null,true));
    }
}
