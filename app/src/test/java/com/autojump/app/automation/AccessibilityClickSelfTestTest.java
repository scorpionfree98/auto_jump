package com.autojump.app.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.autojump.app.bean.Rule;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleAction;
import org.junit.Test;

public class AccessibilityClickSelfTestTest {
    @Test
    public void createsDelayedExactViewIdClickWithoutCoordinateFallback() {
        String packageName = "com.autojump.app";
        String viewId = "com.autojump.app:id/btn_click_test_target";

        Rule rule = AccessibilityClickSelfTest.createRule(packageName, viewId);
        RuleAction action = rule.getActions().get(0);

        assertEquals(AccessibilityClickSelfTest.RULE_ID, rule.getId());
        assertEquals(1, rule.getActions().size());
        assertEquals(ActionType.CLICK, action.getType());
        assertEquals(packageName, action.getTargetPackage());
        assertEquals(viewId, action.getViewId());
        assertEquals(AccessibilityClickSelfTest.DELAY_MS, action.getDelayBeforeMs());
        assertEquals(AccessibilityClickSelfTest.TIMEOUT_MS, action.getTimeoutMs());
        assertEquals(AccessibilityClickSelfTest.RETRY_MS, action.getRetryIntervalMs());
        assertFalse(action.hasCoordinateFallback());
        assertNull(action.getText());
        assertNull(action.getContentDescription());
    }
}
