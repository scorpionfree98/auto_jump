package com.autojump.app.automation;

import com.autojump.app.domain.RuleAction;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.*;

public class ClickTargetResolverTest {
    private final ClickTargetResolver resolver = new ClickTargetResolver();

    @Test public void ignoresOtherPackages() {
        ClickResolution result = resolver.resolve(action(), "com.other", Collections.emptyList(), 1000, 2000);
        assertEquals(ClickResolution.Decision.IGNORE, result.getDecision());
    }

    @Test public void viewIdHasHighestSelectorPriority() {
        ClickTargetResolver.NodeCandidate text = candidate("text", null, "提交", null);
        ClickTargetResolver.NodeCandidate id = candidate("id", "com.example:id/submit", null, null);
        ClickResolution result = resolver.resolve(action(), "com.example", Arrays.asList(text, id), 1000, 3000);
        assertEquals(ClickResolution.Decision.NODE, result.getDecision());
        assertEquals("id", result.getCandidate().getKey());
    }

    @Test public void textIsUsedBeforeDescription() {
        ClickTargetResolver.NodeCandidate description = candidate("description", null, null, "确认提交");
        ClickTargetResolver.NodeCandidate text = candidate("text", null, "提交", null);
        ClickResolution result = resolver.resolve(action(), "com.example",
                Arrays.asList(description, text), 1000, 3000);
        assertEquals("text", result.getCandidate().getKey());
    }

    @Test public void waitsUntilDelayExpires() {
        ClickResolution result = resolver.resolve(action(), "com.example", Collections.emptyList(), 1000, 1999);
        assertEquals(ClickResolution.Decision.WAIT, result.getDecision());
        assertEquals(1, result.getNextCheckAtMs() - 1999);
    }

    @Test public void retriesBeforeTimeout() {
        ClickResolution result = resolver.resolve(action(), "com.example", Collections.emptyList(), 1000, 2500);
        assertEquals(ClickResolution.Decision.RETRY, result.getDecision());
        assertEquals(3000, result.getNextCheckAtMs());
    }

    @Test public void usesCoordinateFallbackAtTimeout() {
        ClickResolution result = resolver.resolve(action(), "com.example", Collections.emptyList(), 1000, 7000);
        assertEquals(ClickResolution.Decision.GESTURE, result.getDecision());
    }

    @Test public void failsAtTimeoutWithoutFallback() {
        RuleAction noFallback = RuleAction.click("com.example", 1000, 5000, 500,
                "com.example:id/submit", null, null, null, null);
        ClickResolution result = resolver.resolve(noFallback, "com.example",
                Collections.emptyList(), 1000, 7000);
        assertEquals(ClickResolution.Decision.FAIL, result.getDecision());
    }

    @Test public void skipsDisabledCandidate() {
        ClickTargetResolver.NodeCandidate disabled = new ClickTargetResolver.NodeCandidate(
                "disabled", "com.example:id/submit", "提交", null, false);
        ClickResolution result = resolver.resolve(action(), "com.example",
                Collections.singletonList(disabled), 1000, 2500);
        assertEquals(ClickResolution.Decision.RETRY, result.getDecision());
    }

    private RuleAction action() {
        return RuleAction.click("com.example", 1000, 5000, 500,
                "com.example:id/submit", "提交", "确认提交", 0.5, 0.75);
    }

    private ClickTargetResolver.NodeCandidate candidate(String key, String id, String text,
                                                         String description) {
        return new ClickTargetResolver.NodeCandidate(key, id, text, description, true);
    }
}
