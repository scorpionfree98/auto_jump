package com.autojump.app.engine;

import com.autojump.app.bean.Rule;
import com.autojump.app.domain.RuleCondition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RuleMatcher {
    public interface ConditionPredicate { boolean matches(RuleCondition condition); }

    public List<Rule> findMatches(List<Rule> rules, ConditionPredicate predicate) {
        List<Rule> candidates = new ArrayList<>();
        if (rules == null) return candidates;
        for (Rule rule : rules) if (rule != null && rule.isEnabled()) candidates.add(rule);
        Collections.sort(candidates, new Comparator<Rule>() {
            @Override public int compare(Rule left, Rule right) {
                return Integer.compare(right.getPriority(), left.getPriority());
            }
        });
        List<Rule> matches = new ArrayList<>();
        for (Rule rule : candidates) {
            boolean matched = true;
            for (RuleCondition condition : rule.getConditions()) {
                if (!predicate.matches(condition)) { matched = false; break; }
            }
            if (matched) matches.add(rule);
        }
        return matches;
    }

    public int countEnabled(List<Rule> rules) {
        int count = 0;
        if (rules != null) for (Rule rule : rules) if (rule != null && rule.isEnabled()) count++;
        return count;
    }
}
