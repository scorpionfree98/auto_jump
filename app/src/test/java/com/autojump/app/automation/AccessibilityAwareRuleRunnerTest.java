package com.autojump.app.automation;

import com.autojump.app.bean.Rule;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.domain.TouchPoint;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccessibilityAwareRuleRunnerTest {
    @Test
    public void deeplinkOnlyRuleDoesNotRequireAccessibility() {
        Rule rule = new Rule("link", "link");
        rule.getActions().add(RuleAction.deeplink("demo://page"));

        assertFalse(AccessibilityAwareRuleRunner.requiresAccessibility(rule));
    }

    @Test
    public void selectorClickRequiresAccessibility() {
        Rule rule = new Rule("click", "click");
        rule.getActions().add(RuleAction.click("com.example", 0, 5_000, 250,
                "com.example:id/submit", null, null, null, null));

        assertTrue(AccessibilityAwareRuleRunner.requiresAccessibility(rule));
    }

    @Test
    public void recordedTouchRequiresAccessibility() {
        Rule rule = new Rule("touch", "touch");
        rule.getActions().add(RuleAction.recordedTouch("com.example", 1_000, 5_000,
                1080, 2400, 0, "1.0", Arrays.asList(
                        new TouchPoint(0.5, 0.5, 0),
                        new TouchPoint(0.5, 0.5, 80))));

        assertTrue(AccessibilityAwareRuleRunner.requiresAccessibility(rule));
    }
}
