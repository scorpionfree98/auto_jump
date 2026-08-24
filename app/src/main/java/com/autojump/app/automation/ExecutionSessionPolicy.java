package com.autojump.app.automation;

import java.util.List;

/** Pure policy for reusing unfinished rule executions instead of enqueuing duplicates. */
public class ExecutionSessionPolicy {
    public ExecutionSession findActiveForRule(List<ExecutionSession> sessions, String ruleId) {
        if (sessions == null || ruleId == null) return null;
        ExecutionSession newest = null;
        for (ExecutionSession session : sessions) {
            if (session == null || !ruleId.equals(session.getRuleId()) || !isActive(session)) {
                continue;
            }
            if (newest == null || session.getUpdatedAt() > newest.getUpdatedAt()) newest = session;
        }
        return newest;
    }

    private boolean isActive(ExecutionSession session) {
        return session.getStatus() == ExecutionSession.Status.PENDING
                || session.getStatus() == ExecutionSession.Status.WAITING
                || session.getStatus() == ExecutionSession.Status.RUNNING;
    }
}
