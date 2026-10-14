package com.autojump.app.engine;

import com.autojump.app.domain.ConditionType;
import com.autojump.app.domain.RuleCondition;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Builds a concrete explanation for alternative time-window conditions. */
public final class ConditionFailureText {
    private ConditionFailureText() {}

    public static String timeWindows(List<RuleCondition> conditions, Calendar now) {
        String[] weekday = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        String current = String.format(Locale.CHINA, "%02d:%02d（%s）",
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE),
                weekday[now.get(Calendar.DAY_OF_WEEK) - 1]);
        StringBuilder required = new StringBuilder();
        if (conditions != null) {
            for (RuleCondition condition : conditions) {
                if (condition == null || condition.getType() != ConditionType.TIME) continue;
                if (required.length() > 0) required.append(" 或 ");
                required.append(condition.getDescription());
            }
        }
        return "条件不满足：\n当前时间：" + current
                + "\n允许时段：" + (required.length() == 0 ? "未设置" : required)
                + "\n因此本次未执行。";
    }
}
