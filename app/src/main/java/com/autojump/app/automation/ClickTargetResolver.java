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

        public NodeCandidate(String key, String viewId, String text, String contentDescription,
                             boolean enabledAndClickable) {
            this.key = key;
            this.viewId = viewId;
            this.text = text;
            this.contentDescription = contentDescription;
            this.enabledAndClickable = enabledAndClickable;
        }

        public String getKey() { return key; }
        public String getViewId() { return viewId; }
        public String getText() { return text; }
        public String getContentDescription() { return contentDescription; }
        public boolean isEnabledAndClickable() { return enabledAndClickable; }
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
        NodeCandidate candidate = findByViewId(action.getViewId(), safeCandidates);
        if (candidate == null) candidate = findByText(action.getText(), safeCandidates);
        if (candidate == null) candidate = findByDescription(action.getContentDescription(), safeCandidates);
        if (candidate != null) return ClickResolution.node(candidate);

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

    private NodeCandidate findByViewId(String expected, List<NodeCandidate> candidates) {
        if (expected == null) return null;
        for (NodeCandidate candidate : candidates) {
            if (candidate != null && candidate.isEnabledAndClickable()
                    && expected.equals(candidate.getViewId())) return candidate;
        }
        return null;
    }

    private NodeCandidate findByText(String expected, List<NodeCandidate> candidates) {
        if (expected == null) return null;
        for (NodeCandidate candidate : candidates) {
            if (candidate != null && candidate.isEnabledAndClickable()
                    && expected.equals(candidate.getText())) return candidate;
        }
        return null;
    }

    private NodeCandidate findByDescription(String expected, List<NodeCandidate> candidates) {
        if (expected == null) return null;
        for (NodeCandidate candidate : candidates) {
            if (candidate != null && candidate.isEnabledAndClickable()
                    && expected.equals(candidate.getContentDescription())) return candidate;
        }
        return null;
    }

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
