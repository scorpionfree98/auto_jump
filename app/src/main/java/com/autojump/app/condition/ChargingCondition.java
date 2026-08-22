package com.autojump.app.condition;

import com.autojump.app.domain.ConditionType;
import com.autojump.app.domain.RuleCondition;

/**
 * 充电状态条件
 * 判断设备是否正在充电
 */
@Deprecated
public class ChargingCondition extends RuleCondition {

    public ChargingCondition(boolean shouldBeCharging) {
        super(ConditionType.CHARGING);
        setShouldBeCharging(shouldBeCharging);
    }
}
