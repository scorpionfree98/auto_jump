package com.autojump.app.automation;

import com.autojump.app.bean.Rule;
import com.autojump.app.domain.RuleAction;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.*;

public class ExecutionSessionPolicyTest {
    private final ExecutionSessionPolicy policy = new ExecutionSessionPolicy();

    @Test public void findsNewestActiveSessionForSameRule() {
        ExecutionSession older = session("rule-1", 1000, ExecutionSession.Status.WAITING);
        ExecutionSession newer = session("rule-1", 2000, ExecutionSession.Status.RUNNING);
        assertSame(newer, policy.findActiveForRule(Arrays.asList(older, newer), "rule-1"));
    }

    @Test public void ignoresTerminalAndDifferentRuleSessions() {
        ExecutionSession failed = session("rule-1", 3000, ExecutionSession.Status.FAILED);
        ExecutionSession cancelled = session("rule-1", 4000, ExecutionSession.Status.CANCELLED);
        ExecutionSession other = session("rule-2", 5000, ExecutionSession.Status.WAITING);
        assertNull(policy.findActiveForRule(
                Arrays.asList(failed, cancelled, other), "rule-1"));
    }

    @Test public void handlesMissingInputs() {
        assertNull(policy.findActiveForRule(null, "rule-1"));
        assertNull(policy.findActiveForRule(Collections.emptyList(), null));
    }

    @Test public void oldTaskCannotBlockFreshLaunchEvenAfterRecentUnlock() {
        ExecutionSession old = session("rule-1", 1000, ExecutionSession.Status.WAITING);
        old.setUpdatedAt(900_000);
        assertTrue(policy.isExpired(old, 900_001));
        assertFalse(policy.canReuse(old, 900_001));
    }

    @Test public void rapidDuplicateIsReusedButLaterRetryStartsFromBeginning() {
        ExecutionSession waiting = session("rule-1", 1000, ExecutionSession.Status.WAITING);
        assertTrue(policy.canReuse(waiting, 2000));
        assertFalse(policy.canReuse(waiting, 16000));
        assertFalse(policy.isExpired(waiting, 16000));
    }

    @Test public void runningGestureIsNotRestartedByAnotherTrigger() {
        ExecutionSession running = session("rule-1", 1000, ExecutionSession.Status.RUNNING);
        assertTrue(policy.canReuse(running, 60000));
        assertFalse(policy.canReuse(running, 601000));
    }

    private ExecutionSession session(String ruleId, long updatedAt,
                                     ExecutionSession.Status status) {
        Rule rule = new Rule(ruleId, "规则");
        rule.getActions().add(RuleAction.waitFor(1000));
        ExecutionSession session = ExecutionSession.fromRule(rule, updatedAt);
        session.setUpdatedAt(updatedAt);
        session.setStatus(status);
        return session;
    }
}
