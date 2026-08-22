package com.autojump.app.engine;

import com.autojump.app.bean.Rule;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.domain.RuleCondition;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class RuleMatcherTest {
    @Test public void matchesAllConditionsAndSortsByPriority() {
        Rule low = rule("low", 1, RuleCondition.wifi("Office"));
        Rule high = rule("high", 10, RuleCondition.charging(true));
        List<Rule> result = new RuleMatcher().findMatches(Arrays.asList(low, high), condition -> true);
        assertEquals("high", result.get(0).getId());
        assertEquals("low", result.get(1).getId());
    }

    @Test public void skipsDisabledAndFailedAndCondition() {
        Rule disabled = rule("disabled", 10, RuleCondition.wifi("Office"));
        disabled.setEnabled(false);
        Rule failed = rule("failed", 5, RuleCondition.wifi("Office"), RuleCondition.charging(true));
        List<Rule> result = new RuleMatcher().findMatches(Arrays.asList(disabled, failed),
                condition -> condition.getSsid() == null);
        assertTrue(result.isEmpty());
        assertEquals(1, new RuleMatcher().countEnabled(Arrays.asList(disabled, failed)));
    }

    @Test public void conditionlessRuleMatches() {
        Rule rule = new Rule("always", "always");
        rule.getActions().add(RuleAction.deeplink("demo://open"));
        assertEquals(1, new RuleMatcher().findMatches(Arrays.asList(rule), condition -> false).size());
    }

    private Rule rule(String id, int priority, RuleCondition... conditions) {
        Rule rule = new Rule(id, id);
        rule.setPriority(priority);
        rule.setConditions(Arrays.asList(conditions));
        rule.getActions().add(RuleAction.deeplink("demo://" + id));
        return rule;
    }
}
