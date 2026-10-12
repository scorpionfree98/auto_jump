package com.autojump.app.automation;

import com.autojump.app.bean.Rule;
import com.autojump.app.domain.RuleAction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ExecutionSession {
    public enum Status { QUEUED, PENDING, WAITING, RUNNING, COMPLETED, FAILED, CANCELLED, SKIPPED }
    private long sequence;
    private boolean cancelRequested;
    public boolean isCancelRequested() { return cancelRequested; }
    public void requestCancel() { cancelRequested = true; }
    private String cancellationStatus;
    private String cancellationReason;
    private long cancellationElapsedAt;
    public void requestCancel(String status, String reason, long elapsed) {
        cancelRequested = true; cancellationStatus=status; cancellationReason=reason; cancellationElapsedAt=elapsed;
    }
    public String getCancellationStatus() { return cancellationStatus == null ? "CANCELLED" : cancellationStatus; }
    public String getCancellationReason() { return cancellationReason == null ? "用户取消了任务" : cancellationReason; }
    public long getCancellationElapsedAt() { return cancellationElapsedAt; }
    private long expiresAt;
    private long elapsedDeadline;
    private int deadlineBoot = -1;
    private long unlockElapsedDeadline;
    public long getElapsedDeadline() { return elapsedDeadline; }
    public int getDeadlineBoot() { return deadlineBoot; }
    public void setElapsedDeadline(long value, int boot) { elapsedDeadline = value; deadlineBoot = boot; }
    public long getUnlockElapsedDeadline() { return unlockElapsedDeadline; }
    public void setUnlockElapsedDeadline(long value) { unlockElapsedDeadline = value; }
    private long unlockDeadline;
    public long getSequence() { return sequence; }
    public void setSequence(long value) { sequence = value; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long value) { expiresAt = value; }
    public long getUnlockDeadline() { return unlockDeadline; }
    public void setUnlockDeadline(long value) { unlockDeadline = value; }
    public void startFrom(Rule latest, long now) {
        ruleName = latest.getName();
        requestUnlockBeforeRun = latest.isRequestUnlockBeforeRun();
        actions = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(latest.getActions()),
                new com.google.gson.reflect.TypeToken<List<RuleAction>>() {}.getType());
        currentActionIndex = 0;
        updatedAt = now;
        notBefore = 0;
        status = Status.PENDING;
    }

    private String id;
    private String ruleId;
    private String ruleName;
    private boolean requestUnlockBeforeRun;
    private List<RuleAction> actions = new ArrayList<>();
    private int currentActionIndex;
    private long createdAt;
    private long updatedAt;
    private long notBefore;
    private Status status;
    private String lastError;

    public ExecutionSession() {}

    public static ExecutionSession fromRule(Rule rule, long now) {
        ExecutionSession session = new ExecutionSession();
        session.id = UUID.randomUUID().toString();
        session.ruleId = rule.getId();
        session.ruleName = rule.getName();
        session.requestUnlockBeforeRun = rule.isRequestUnlockBeforeRun();
        session.actions = new ArrayList<>(rule.getActions());
        session.createdAt = now;
        session.updatedAt = now;
        session.status = Status.PENDING;
        return session;
    }

    public RuleAction currentAction() {
        return currentActionIndex >= 0 && currentActionIndex < getActions().size()
                ? getActions().get(currentActionIndex) : null;
    }

    public void advance(long now) {
        currentActionIndex++;
        updatedAt = now;
        notBefore = 0;
        status = currentActionIndex >= getActions().size() ? Status.COMPLETED : Status.PENDING;
    }

    public String getId() { return id; }
    public String getRuleId() { return ruleId; }
    public String getRuleName() { return ruleName; }
    public boolean isRequestUnlockBeforeRun() { return requestUnlockBeforeRun; }
    public List<RuleAction> getActions() { if (actions == null) actions = new ArrayList<>(); return actions; }
    public int getCurrentActionIndex() { return currentActionIndex; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public long getNotBefore() { return notBefore; }
    public void setNotBefore(long notBefore) { this.notBefore = notBefore; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
