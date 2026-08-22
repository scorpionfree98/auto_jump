package com.autojump.app.automation;

import com.autojump.app.bean.Rule;
import com.autojump.app.domain.RuleAction;
import org.junit.Test;

import static org.junit.Assert.*;

public class ExecutionSessionTest {
    @Test public void advancesInOrderAndCompletes() {
        Rule rule = new Rule("rule", "rule");
        rule.getActions().add(RuleAction.deeplink("demo://open"));
        rule.getActions().add(RuleAction.waitFor(1000));
        ExecutionSession session = ExecutionSession.fromRule(rule, 10);

        assertEquals("demo://open", session.currentAction().getDeeplink());
        session.advance(20);
        assertEquals(ExecutionSession.Status.PENDING, session.getStatus());
        assertEquals(1000, session.currentAction().getWaitDurationMs());
        session.advance(30);
        assertEquals(ExecutionSession.Status.COMPLETED, session.getStatus());
        assertNull(session.currentAction());
    }

    @Test public void advanceClearsPreviousDelayGate() {
        Rule rule = new Rule("rule", "rule");
        rule.getActions().add(RuleAction.waitFor(1000));
        ExecutionSession session = ExecutionSession.fromRule(rule, 10);
        session.setNotBefore(5000);
        session.advance(20);
        assertEquals(0, session.getNotBefore());
    }

    @Test public void carriesExplicitUnlockPreferenceFromRule() {
        Rule rule = new Rule("rule", "rule");
        rule.setRequestUnlockBeforeRun(true);
        rule.getActions().add(RuleAction.deeplink("demo://open"));

        ExecutionSession session = ExecutionSession.fromRule(rule, 10);

        assertTrue(session.isRequestUnlockBeforeRun());
    }
}
