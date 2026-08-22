package com.autojump.app.automation;

import com.autojump.app.bean.Rule;
import com.autojump.app.domain.RuleAction;

/** Builds the app-local rule used by the permission screen's safe click diagnostic. */
public final class AccessibilityClickSelfTest {
    public static final String RULE_ID = "accessibility-click-self-test";
    public static final String RULE_NAME = "安全点击测试";
    public static final long DELAY_MS = 1_000L;
    public static final long TIMEOUT_MS = 5_000L;
    public static final long RETRY_MS = 250L;

    private AccessibilityClickSelfTest() {}

    public static Rule createRule(String packageName, String targetViewId) {
        Rule rule = new Rule(RULE_ID, RULE_NAME);
        rule.getActions().add(RuleAction.click(packageName, DELAY_MS, TIMEOUT_MS, RETRY_MS,
                targetViewId, null, null, null, null));
        return rule;
    }
}
