package com.autojump.app.automation;

import com.autojump.app.bean.Rule;
import com.autojump.app.domain.RuleAction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ExecutionSession {
    public enum Status { PENDING, WAITING, RUNNING, COMPLETED, FAILED, CANCELLED }

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
