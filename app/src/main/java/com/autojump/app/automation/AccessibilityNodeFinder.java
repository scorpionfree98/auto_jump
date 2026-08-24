package com.autojump.app.automation;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import com.autojump.app.domain.RuleAction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Converts the current accessibility tree into bounded, non-sensitive click candidates. */
public class AccessibilityNodeFinder {
    private static final int MAX_NODES = 500;
    private static final int MAX_ANCESTOR_DEPTH = 16;
    private final ClickTargetPrecisionPolicy precisionPolicy =
            new ClickTargetPrecisionPolicy();

    public static class NodeMatch {
        private final ClickTargetResolver.NodeCandidate candidate;
        private final AccessibilityNodeInfo clickableNode;

        NodeMatch(ClickTargetResolver.NodeCandidate candidate, AccessibilityNodeInfo clickableNode) {
            this.candidate = candidate;
            this.clickableNode = clickableNode;
        }

        public ClickTargetResolver.NodeCandidate getCandidate() { return candidate; }
        public AccessibilityNodeInfo getClickableNode() { return clickableNode; }
    }

    public List<NodeMatch> find(AccessibilityNodeInfo root, RuleAction action,
                                int displayWidth, int displayHeight) {
        if (root == null || action == null || action.getTargetPackage() == null
                || displayWidth <= 0 || displayHeight <= 0) {
            return Collections.emptyList();
        }
        List<NodeMatch> matches = new ArrayList<>();
        Deque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited < MAX_NODES) {
            AccessibilityNodeInfo node = queue.removeFirst();
            visited++;
            addChildren(queue, node);
            if (!belongsToPackage(node, action.getTargetPackage()) || node.isPassword()) continue;
            AccessibilityNodeInfo clickable = nearestClickable(node);
            if (clickable == null) continue;
            Rect sourceBounds = new Rect();
            node.getBoundsInScreen(sourceBounds);
            Rect bounds = new Rect();
            clickable.getBoundsInScreen(bounds);
            String key = "node-" + visited;
            matches.add(new NodeMatch(new ClickTargetResolver.NodeCandidate(
                    key,
                    text(node.getViewIdResourceName()),
                    text(node.getText()),
                    text(node.getContentDescription()),
                    clickable.isEnabled() && clickable.isVisibleToUser() && clickable.isClickable(),
                    (double) sourceBounds.exactCenterX() / displayWidth,
                    (double) sourceBounds.exactCenterY() / displayHeight,
                    isPreciseClickTarget(node, clickable, bounds, displayWidth, displayHeight,
                            isInsideWebView(node))),
                    clickable));
        }
        return matches;
    }

    public List<ClickTargetResolver.NodeCandidate> candidates(List<NodeMatch> matches) {
        List<ClickTargetResolver.NodeCandidate> result = new ArrayList<>();
        if (matches != null) for (NodeMatch match : matches) result.add(match.getCandidate());
        return result;
    }

    public AccessibilityNodeInfo nodeFor(String key, List<NodeMatch> matches) {
        if (key == null || matches == null) return null;
        for (NodeMatch match : matches) {
            if (key.equals(match.getCandidate().getKey())) return match.getClickableNode();
        }
        return null;
    }

    private void addChildren(Deque<AccessibilityNodeInfo> queue, AccessibilityNodeInfo node) {
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) queue.addLast(child);
        }
    }

    private boolean belongsToPackage(AccessibilityNodeInfo node, String targetPackage) {
        CharSequence packageName = node.getPackageName();
        return packageName != null && targetPackage.contentEquals(packageName);
    }

    private AccessibilityNodeInfo nearestClickable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current.isClickable()) return current;
            current = current.getParent();
        }
        return null;
    }

    private boolean isPreciseClickTarget(AccessibilityNodeInfo source,
                                         AccessibilityNodeInfo clickable, Rect clickableBounds,
                                         int displayWidth, int displayHeight,
                                         boolean insideWebView) {
        double clickableArea = Math.max(0, clickableBounds.width())
                * (double) Math.max(0, clickableBounds.height());
        double displayArea = displayWidth * (double) displayHeight;
        double clickableAreaRatio = displayArea <= 0 ? 1d : clickableArea / displayArea;
        return precisionPolicy.isPrecise(source == clickable, clickableAreaRatio, insideWebView);
    }

    private boolean isInsideWebView(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int depth = 0; current != null && depth < MAX_ANCESTOR_DEPTH; depth++) {
            CharSequence className = current.getClassName();
            if (className != null && className.toString().endsWith("WebView")) return true;
            current = current.getParent();
        }
        return false;
    }

    private String text(CharSequence value) {
        if (value == null) return null;
        String result = value.toString().trim();
        return result.isEmpty() ? null : result;
    }
}
