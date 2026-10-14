package com.autojump.app.engine;

import com.autojump.app.domain.RuleCondition;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class RuleConditionPolicyTest {
    @Test public void eitherTimeWindowCanMatch() {
        RuleCondition lunch = RuleCondition.time(null, 11, 20, 12, 50);
        RuleCondition dinner = RuleCondition.time(null, 17, 20, 18, 50);

        assertTrue(RuleConditionPolicy.matches(List.of(lunch, dinner), c -> c == dinner));
        assertFalse(RuleConditionPolicy.matches(List.of(lunch, dinner), c -> false));
    }

    @Test public void nonTimeConditionsRemainMandatory() {
        RuleCondition lunch = RuleCondition.time(null, 11, 20, 12, 50);
        RuleCondition dinner = RuleCondition.time(null, 17, 20, 18, 50);
        RuleCondition wifi = RuleCondition.wifi("Office");

        assertTrue(RuleConditionPolicy.matches(List.of(lunch, dinner, wifi),
                c -> c == dinner || c == wifi));
        assertFalse(RuleConditionPolicy.matches(List.of(lunch, dinner, wifi),
                c -> c == dinner));
    }
}
