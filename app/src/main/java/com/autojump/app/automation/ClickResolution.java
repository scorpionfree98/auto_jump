package com.autojump.app.automation;

public class ClickResolution {
    public enum Decision { IGNORE, WAIT, NODE, RETRY, GESTURE, FAIL }

    private final Decision decision;
    private final ClickTargetResolver.NodeCandidate candidate;
    private final long nextCheckAtMs;
    private final String message;

    private ClickResolution(Decision decision, ClickTargetResolver.NodeCandidate candidate,
                            long nextCheckAtMs, String message) {
        this.decision = decision;
        this.candidate = candidate;
        this.nextCheckAtMs = nextCheckAtMs;
        this.message = message;
    }

    public static ClickResolution of(Decision decision) {
        return new ClickResolution(decision, null, 0, null);
    }

    public static ClickResolution node(ClickTargetResolver.NodeCandidate candidate) {
        return new ClickResolution(Decision.NODE, candidate, 0, null);
    }

    public static ClickResolution gesture(ClickTargetResolver.NodeCandidate candidate) {
        return new ClickResolution(Decision.GESTURE, candidate, 0, null);
    }

    public static ClickResolution scheduled(Decision decision, long nextCheckAtMs) {
        return new ClickResolution(decision, null, nextCheckAtMs, null);
    }

    public static ClickResolution failure(String message) {
        return new ClickResolution(Decision.FAIL, null, 0, message);
    }

    public Decision getDecision() { return decision; }
    public ClickTargetResolver.NodeCandidate getCandidate() { return candidate; }
    public long getNextCheckAtMs() { return nextCheckAtMs; }
    public String getMessage() { return message; }
}
