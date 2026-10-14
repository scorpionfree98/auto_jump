package com.autojump.app.engine;

import com.autojump.app.domain.ConditionType;
import com.autojump.app.domain.RuleCondition;
import java.util.List;

/** Multiple time windows are alternatives; all other conditions remain mandatory. */
public final class RuleConditionPolicy {
    public interface Predicate { boolean matches(RuleCondition condition); }

    private RuleConditionPolicy() {}

    public static boolean matches(List<RuleCondition> conditions, Predicate predicate) {
        boolean hasTimeWindow = false;
        boolean timeWindowMatched = false;
        if (conditions == null) return true;
        for (RuleCondition condition : conditions) {
            if (condition != null && condition.getType() == ConditionType.TIME) {
                hasTimeWindow = true;
                if (predicate.matches(condition)) timeWindowMatched = true;
            } else if (!predicate.matches(condition)) {
                return false;
            }
        }
        return !hasTimeWindow || timeWindowMatched;
    }
}
