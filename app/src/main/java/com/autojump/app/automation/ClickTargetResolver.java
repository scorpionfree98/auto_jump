package com.autojump.app.automation;

import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleAction;
import java.util.Collections;
import java.util.List;

/** Pure decision logic for selecting a click target and applying delay/retry/timeout policy. */
public class ClickTargetResolver {
    public static class NodeCandidate {
        private final String key;
        private final String viewId;
        private final String text;
        private final String contentDescription;
        private final boolean enabledAndClickable;
        private final Double relativeX;
        private final Double relativeY;
        private final boolean preciseClickTarget;

        public NodeCandidate(String key, String viewId, String text, String contentDescription,
                             boolean enabledAndClickable) {
            this(key, viewId, text, contentDescription, enabledAndClickable, null, null, true);
        }

        public NodeCandidate(String key, String viewId, String text, String contentDescription,
                             boolean enabledAndClickable, Double relativeX, Double relativeY) {
            this(key, viewId, text, contentDescription, enabledAndClickable,
                    relativeX, relativeY, true);
        }

        public NodeCandidate(String key, String viewId, String text, String contentDescription,
                             boolean enabledAndClickable, Double relativeX, Double relativeY,
                             boolean preciseClickTarget) {
            this.key = key;
            this.viewId = viewId;
            this.text = text;
            this.contentDescription = contentDescription;
            this.enabledAndClickable = enabledAndClickable;
            this.relativeX = relativeX;
            this.relativeY = relativeY;
            this.preciseClickTarget = preciseClickTarget;
        }

        public String getKey() { return key; }
        public String getViewId() { return viewId; }
        public String getText() { return text; }
        public String getContentDescription() { return contentDescription; }
        public boolean isEnabledAndClickable() { return enabledAndClickable; }
        public Double getRelativeX() { return relativeX; }
        public Double getRelativeY() { return relativeY; }
        public boolean isPreciseClickTarget() { return preciseClickTarget; }
    }

    public ClickResolution resolve(RuleAction action, String foregroundPackage,
                                   List<NodeCandidate> candidates, long actionStartedAtMs,
                                   long nowMs) {
        if (action == null || action.getType() != ActionType.CLICK) {
            return ClickResolution.failure("当前动作不是点击动作");
        }
        if (foregroundPackage == null || !foregroundPackage.equals(action.getTargetPackage())) {
            return ClickResolution.of(ClickResolution.Decision.IGNORE);
        }

        long readyAt = safeAdd(actionStartedAtMs, Math.max(0, action.getDelayBeforeMs()));
        if (nowMs < readyAt) {
            return ClickResolution.scheduled(ClickResolution.Decision.WAIT, readyAt);
        }

        List<NodeCandidate> safeCandidates = candidates == null ? Collections.emptyList() : candidates;
        NodeCandidate candidate = findByViewId(action, safeCandidates);
        if (candidate == null) candidate = findByText(action, safeCandidates);
        if (candidate == null) candidate = findByDescription(action, safeCandidates);
        if (candidate != null) {
            if (hasValidCoordinates(action)) {
                return ClickResolution.gesture(candidate);
            }
            if (candidate.isPreciseClickTarget()) return ClickResolution.node(candidate);
        }

        long timeoutAt = safeAdd(readyAt, Math.max(1, action.getTimeoutMs()));
        if (nowMs < timeoutAt) {
            long retryAt = safeAdd(nowMs, Math.max(1, action.getRetryIntervalMs()));
            return ClickResolution.scheduled(ClickResolution.Decision.RETRY,
                    Math.min(retryAt, timeoutAt));
        }
        if (hasValidCoordinates(action)) {
            return ClickResolution.of(ClickResolution.Decision.GESTURE);
        }
        return ClickResolution.failure("超时前未找到点击目标");
    }

    private NodeCandidate findByViewId(RuleAction action, List<NodeCandidate> candidates) {
        String expected = action.getViewId();
        if (expected == null) return null;
        return closestMatching(action, candidates,
                candidate -> expected.equals(candidate.getViewId()));
    }

    private NodeCandidate findByText(RuleAction action, List<NodeCandidate> candidates) {
        String expected = action.getText();
        if (expected == null) return null;
        return closestMatching(action, candidates,
                candidate -> expected.equals(candidate.getText()));
    }

    private NodeCandidate findByDescription(RuleAction action, List<NodeCandidate> candidates) {
        String expected = action.getContentDescription();
        if (expected == null) return null;
        return closestMatching(action, candidates,
                candidate -> expected.equals(candidate.getContentDescription()));
    }

    private NodeCandidate closestMatching(RuleAction action, List<NodeCandidate> candidates,
                                          CandidateMatcher matcher) {
        NodeCandidate best = null;
        double bestDistance = Double.MAX_VALUE;
        for (NodeCandidate candidate : candidates) {
            if (candidate != null && candidate.isEnabledAndClickable()
                    && matcher.matches(candidate)) {
                double distance = coordinateDistance(action, candidate);
                if (best == null || distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private double coordinateDistance(RuleAction action, NodeCandidate candidate) {
        if (!hasValidCoordinates(action) || candidate.getRelativeX() == null
                || candidate.getRelativeY() == null) return Double.MAX_VALUE;
        double dx = action.getRelativeX() - candidate.getRelativeX();
        double dy = action.getRelativeY() - candidate.getRelativeY();
        return dx * dx + dy * dy;
    }

    private interface CandidateMatcher { boolean matches(NodeCandidate candidate); }

    private boolean hasValidCoordinates(RuleAction action) {
        Double x = action.getRelativeX();
        Double y = action.getRelativeY();
        return x != null && y != null && x >= 0 && x <= 1 && y >= 0 && y <= 1;
    }

    private long safeAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
