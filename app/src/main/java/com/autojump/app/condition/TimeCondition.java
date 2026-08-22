package com.autojump.app.condition;

import com.autojump.app.domain.ConditionType;
import com.autojump.app.domain.RuleCondition;

/**
 * 时间段条件
 * 支持：星期几 + 时间范围
 */
@Deprecated
public class TimeCondition extends RuleCondition {

    public TimeCondition(int[] daysOfWeek, int startHour, int startMinute, int endHour, int endMinute) {
        super(ConditionType.TIME);
        RuleCondition value = RuleCondition.time(daysOfWeek, startHour, startMinute, endHour, endMinute);
        setDaysOfWeek(value.getDaysOfWeek());
        setStartHour(value.getStartHour());
        setStartMinute(value.getStartMinute());
        setEndHour(value.getEndHour());
        setEndMinute(value.getEndMinute());
    }
}
