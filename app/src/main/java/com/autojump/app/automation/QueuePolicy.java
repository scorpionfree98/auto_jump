package com.autojump.app.automation;

import com.autojump.app.domain.RuleAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class QueuePolicy {
    public static final int CAPACITY = 20;
    public static final long QUEUE_TIMEOUT_MS = 15 * 60_000L;
    public static final long UNLOCK_TIMEOUT_MS = 2 * 60_000L;
    public static boolean live(ExecutionSession s) {
        if (s == null || s.getStatus() == null) return false;
        switch (s.getStatus()) {
            case QUEUED: case PENDING: case WAITING: case RUNNING: return true;
            default: return false;
        }
    }
    public static boolean expired(ExecutionSession s, long now) {
        return live(s) && s.getExpiresAt() > 0 && (now >= s.getExpiresAt() || now < s.getCreatedAt());
    }
    public static boolean expired(ExecutionSession s, long wall, long elapsed, int boot) {
        if (!live(s)) return false;
        if (boot >= 0 && s.getDeadlineBoot() == boot && s.getElapsedDeadline() > 0) {
            return elapsed >= s.getElapsedDeadline();
        }
        return expired(s, wall);
    }
    public static List<ExecutionSession> ordered(List<ExecutionSession> input) {
        List<ExecutionSession> result = new ArrayList<>();
        for (ExecutionSession s : input) if (live(s)) result.add(s);
        result.sort(Comparator.comparingLong(ExecutionSession::getSequence));
        return result;
    }
    public static long executionBudget(List<RuleAction> actions) {
        long budget = UNLOCK_TIMEOUT_MS + 30_000L;
        for (RuleAction a : actions) {
            budget += Math.max(0, a.getDelayBeforeMs()) + Math.max(0, a.getWaitDurationMs());
            budget += Math.max(0, a.getTimeoutMs()) + a.getTouchDurationMs() * a.getRepeatCount();
            budget += a.getRepeatIntervalMs() * Math.max(0, a.getRepeatCount() - 1);
        }
        return budget;
    }
}
