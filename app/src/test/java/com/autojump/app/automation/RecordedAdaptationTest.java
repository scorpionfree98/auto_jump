package com.autojump.app.automation;
import com.autojump.app.domain.*;
import com.autojump.app.data.*;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
public class RecordedAdaptationTest {
    private RuleAction action() { return RuleAction.recordedTouch("target",0,15000,1000,2000,1,null,
            Arrays.asList(new TouchPoint(.5,.5,0),new TouchPoint(.5,.5,80))); }
    @Test public void tenPercentDefaultAcceptsModerateRatioChange() {
        assertEquals(RecordedTouchResolver.Decision.REPLAY,new RecordedTouchResolver().resolve(action(),"target",0,1,1000,1850,1).getDecision());
    }
    @Test public void outsideToleranceAndRotationStillFail() {
        assertEquals(RecordedTouchResolver.Decision.FAIL,new RecordedTouchResolver().resolve(action(),"target",0,1,1000,1500,1).getDecision());
        assertEquals(RecordedTouchResolver.Decision.FAIL,new RecordedTouchResolver().resolve(action(),"target",0,1,1000,2000,2).getDecision());
    }
    @Test public void toleranceIsConfigurable() {
        RuleAction a=action(); a.setScreenTolerancePercent(3);
        assertEquals(RecordedTouchResolver.Decision.FAIL,new RecordedTouchResolver().resolve(a,"target",0,1,1000,1850,1).getDecision());
        a.setScreenTolerancePercent(20);
        assertEquals(RecordedTouchResolver.Decision.REPLAY,new RecordedTouchResolver().resolve(a,"target",0,1,1000,1700,1).getDecision());
    }
    @Test public void offsetChangesPixelsAndPreservesOriginalAndTiming() {
        RuleAction a=action(); a.setReplayOffsets(50,-100);
        java.util.List<TouchPoint> mapped=RecordedTouchResolver.mappedTrace(a,1000,2000);
        assertEquals(.55,mapped.get(0).getRelativeX(),1e-8);
        assertEquals(.45,mapped.get(0).getRelativeY(),1e-8);
        assertEquals(.5,a.getTouchTrace().get(0).getRelativeY(),0);
        assertEquals(80,mapped.get(1).getElapsedMs());
    }
    @Test public void allPointsMustStayInsideScreen() {
        RuleAction a=RuleAction.recordedTouch("target",0,15000,1000,2000,1,null,
            Arrays.asList(new TouchPoint(.99,.5,0),new TouchPoint(.99,.5,80))); a.setReplayOffsets(100,0);
        assertEquals(RecordedTouchResolver.Decision.FAIL,new RecordedTouchResolver().resolve(a,"target",0,1,1000,2000,1).getDecision());
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsNonFiniteOffsets() { action().setReplayOffsets(Double.NaN,0); }
    @Test(expected=IllegalArgumentException.class) public void rejectsNonFiniteTouchPoint() { new TouchPoint(Double.NaN,.5,0); }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnboundedImportedGestureDuration() {
        RuleAction.recordedTouch("target",0,15000,1000,2000,1,null,
                Arrays.asList(new TouchPoint(.5,.5,0),new TouchPoint(.5,.5,10001)));
    }
    @Test public void readinessStartsAtTargetAndWaitsForQuietContent() {
        assertEquals(4060,RecordedReadinessPolicy.readyAt(1000,1200,3060,1000));
        assertEquals(4400,RecordedReadinessPolicy.readyAt(1000,4100,3060,1000));
    }
    @Test public void continuouslyChangingPageCannotStarveReplay() {
        assertEquals(5060,RecordedReadinessPolicy.readyAt(1000,60000,3060,1000));
        assertEquals(4260,RecordedReadinessPolicy.readyAt(1000,60000,3060,200));
        assertEquals(12000,RecordedReadinessPolicy.readyAt(1000,60000,3060,10000));
    }
    @Test public void oldJsonGetsSafeDefaultsAndNewFieldsRoundTrip() {
        RuleAction old=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(action()),RuleAction.class);
        assertEquals(10,old.getScreenTolerancePercent()); assertEquals(1000,old.getPageSettleMs());
        old.setScreenTolerancePercent(20);old.setReplayOffsets(12,-8);old.setPageSettleMs(2500);
        RuleAction copy=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(old),RuleAction.class);
        assertEquals(20,copy.getScreenTolerancePercent()); assertEquals(-8,copy.getReplayOffsetYPx(),0);
        assertEquals(2500,copy.getPageSettleMs());
    }
    @Test public void versionFourRecordingRemainsImportableAndExportUpgradesToFive() {
        com.autojump.app.bean.Rule r=new com.autojump.app.bean.Rule("x","x");r.getActions().add(action());
        RuleJsonCodec codec=new RuleJsonCodec();String json=codec.encode(Arrays.asList(r));
        assertTrue(json.contains("\"schemaVersion\": 5"));
        String old=json.replace("\"schemaVersion\": 5","\"schemaVersion\": 4");
        assertEquals(ActionType.RECORDED_TOUCH,StrictRuleImport.decode(old).get(0).getActions().get(0).getType());
    }
}
