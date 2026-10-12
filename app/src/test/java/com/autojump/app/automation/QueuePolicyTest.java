package com.autojump.app.automation;

import com.autojump.app.bean.Rule;
import com.autojump.app.domain.RuleAction;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class QueuePolicyTest {
    private ExecutionSession task(String id, long sequence, long wall) {
        ExecutionSession s = ExecutionSession.fromRule(new Rule(id, id), wall);
        s.setSequence(sequence); s.setStatus(ExecutionSession.Status.QUEUED);
        s.setExpiresAt(wall + 10000); return s;
    }
    @Test public void fifoUsesSequenceEvenIfWallClockMovesBackwards() {
        ExecutionSession a = task("a", 1, 5000), b = task("b", 2, 3000);
        assertEquals(a, QueuePolicy.ordered(Arrays.asList(b, a)).get(0));
    }
    @Test public void terminalTaskDoesNotBlockFollowingTasks() {
        ExecutionSession a = task("a", 1, 5000), b = task("b", 2, 5000);
        a.setStatus(ExecutionSession.Status.FAILED);
        assertEquals(Arrays.asList(b), QueuePolicy.ordered(Arrays.asList(a, b)));
    }
    @Test public void sameBootUsesMonotonicDeadlineNotEditedClock() {
        ExecutionSession a = task("a", 1, 5000);
        a.setElapsedDeadline(90000, 7);
        assertFalse(QueuePolicy.expired(a, 999999, 80000, 7));
        assertTrue(QueuePolicy.expired(a, 1, 90000, 7));
    }
    @Test public void differentBootUsesBoundedWallDeadline() {
        ExecutionSession a = task("a", 1, 5000); a.setElapsedDeadline(90000, 7);
        assertTrue(QueuePolicy.expired(a, 16000, 1000, 8));
        assertTrue(QueuePolicy.expired(a, 4000, 1000, 8));
    }
    @Test public void explicitLongWaitExtendsBudget() {
        assertTrue(QueuePolicy.executionBudget(Arrays.asList(RuleAction.waitFor(900000))) > 900000);
    }
    @Test public void latestConfigurationBecomesSnapshotOnlyWhenStarting() {
        ExecutionSession a = task("a", 1, 5000);
        Rule latest = new Rule("a", "new name"); latest.getActions().add(RuleAction.deeplink("example://latest"));
        a.startFrom(latest, 6000); latest.getActions().clear();
        assertEquals("example://latest", a.currentAction().getDeeplink());
        assertEquals("new name", a.getRuleName());
        assertEquals(0, a.getCurrentActionIndex());
    }
    @Test public void actionObjectMutationCannotChangeRunningSnapshot() {
        Rule latest=new Rule("a","a"); RuleAction action=RuleAction.deeplink("example://old");
        latest.getActions().add(action);ExecutionSession s=task("a",1,5000);s.startFrom(latest,6000);
        action.setDeeplink("example://new"); assertEquals("example://old",s.currentAction().getDeeplink());
    }
    @Test public void cancellingKeepsRunningSlotUntilGestureCallback() {
        ExecutionSession a=task("a",1,5000),b=task("b",2,5000);a.setStatus(ExecutionSession.Status.RUNNING);
        a.requestCancel("FAILED","超时",1000);
        assertEquals(a,QueuePolicy.ordered(Arrays.asList(a,b)).get(0));
        assertEquals("FAILED",a.getCancellationStatus());
        a.setStatus(ExecutionSession.Status.FAILED);
        assertEquals(b,QueuePolicy.ordered(Arrays.asList(a,b)).get(0));
    }
}
