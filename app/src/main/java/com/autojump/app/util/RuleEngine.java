package com.autojump.app.util;

import android.content.Context;
import com.autojump.app.automation.AutomationOrchestrator;
import com.autojump.app.bean.Rule;
import com.autojump.app.engine.ConditionEvaluator;
import com.autojump.app.engine.RuleMatcher;
import java.util.List;

public class RuleEngine {
    public static class ExecutionSummary {
        private final int enabledRuleCount;
        private final int matchedRuleCount;

        ExecutionSummary(int enabledRuleCount, int matchedRuleCount) {
            this.enabledRuleCount = enabledRuleCount;
            this.matchedRuleCount = matchedRuleCount;
        }

        public int getEnabledRuleCount() { return enabledRuleCount; }
        public int getMatchedRuleCount() { return matchedRuleCount; }
    }

    public static ExecutionSummary execute(Context context) {
        List<Rule> rules = SPUtil.getRuleList(context);
        RuleMatcher matcher = new RuleMatcher();
        ConditionEvaluator evaluator = new ConditionEvaluator();
        List<Rule> matches = matcher.findMatches(rules,
                condition -> evaluator.matches(context, condition));
        AutomationOrchestrator orchestrator = new AutomationOrchestrator(context);
        for (Rule rule : matches) {
            if (!rule.getActions().isEmpty()) orchestrator.enqueue(context, rule);
        }
        return new ExecutionSummary(matcher.countEnabled(rules), matches.size());
    }

    public static int executeRules(Context context) {
        return execute(context).getMatchedRuleCount();
    }
}
