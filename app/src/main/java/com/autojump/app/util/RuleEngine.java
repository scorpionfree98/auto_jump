package com.autojump.app.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import com.autojump.app.bean.Rule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 规则引擎：匹配规则并执行动作
 */
public class RuleEngine {
    private static final String TAG = "RuleEngine";

    /**
     * 执行规则匹配
     * @return 匹配成功的规则数量
     */
    public static int executeRules(Context context) {
        List<Rule> rules = SPUtil.getRuleList(context);
        if (rules == null || rules.isEmpty()) {
            Log.d(TAG, "没有配置规则");
            return 0;
        }

        // 按优先级排序（高优先级优先）
        List<Rule> sortedRules = new ArrayList<>(rules);
        Collections.sort(sortedRules, new Comparator<Rule>() {
            @Override
            public int compare(Rule r1, Rule r2) {
                return Integer.compare(r2.getPriority(), r1.getPriority());
            }
        });

        int matchedCount = 0;
        for (Rule rule : sortedRules) {
            if (!rule.isEnabled()) {
                continue;
            }

            if (matchRule(context, rule)) {
                Log.d(TAG, "规则匹配: " + rule.getName());
                executeActions(context, rule);
                matchedCount++;
            }
        }

        return matchedCount;
    }

    /**
     * 判断规则是否匹配（所有条件 AND）
     */
    private static boolean matchRule(Context context, Rule rule) {
        List<Rule.Condition> conditions = rule.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            // 无条件规则永远匹配
            return true;
        }

        for (Rule.Condition condition : conditions) {
            if (!condition.match(context)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 执行规则的动作（跳转 deeplink）
     */
    private static void executeActions(Context context, Rule rule) {
        List<String> actions = rule.getActions();
        if (actions == null || actions.isEmpty()) {
            Log.w(TAG, "规则无动作: " + rule.getName());
            return;
        }

        for (String deeplink : actions) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(deeplink));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.d(TAG, "执行跳转: " + deeplink);
                // 多个动作之间稍微间隔，避免冲突
                Thread.sleep(500);
            } catch (Exception e) {
                Log.e(TAG, "跳转失败: " + deeplink, e);
            }
        }
    }
}
