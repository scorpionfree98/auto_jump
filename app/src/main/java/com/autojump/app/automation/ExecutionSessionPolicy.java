package com.autojump.app.automation;

import java.util.List;

/** Pure policy for reusing unfinished rule executions instead of enqueuing duplicates. */
public class ExecutionSessionPolicy {
    public static final long MAX_SESSION_AGE_MS = 10 * 60_000L;
    public static final long DUPLICATE_TRIGGER_WINDOW_MS = 15_000L;

    public boolean isExpired(ExecutionSession session, long now) {
        if (session != null && session.getExpiresAt() > 0) return QueuePolicy.expired(session, now);
        return session != null && isActive(session)
                && now - session.getCreatedAt() >= MAX_SESSION_AGE_MS;
    }

    public boolean canReuse(ExecutionSession session, long now) {
        return session != null && isActive(session) && !isExpired(session, now)
                && (session.getStatus() == ExecutionSession.Status.RUNNING
                || now - session.getCreatedAt() < DUPLICATE_TRIGGER_WINDOW_MS);
    }
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
