package com.autojump.app.condition;

import com.autojump.app.domain.ConditionType;
import com.autojump.app.domain.RuleCondition;

/**
 * WiFi 连接条件
 * 判断当前连接的 WiFi SSID 是否匹配
 */
@Deprecated
public class WiFiCondition extends RuleCondition {

    public WiFiCondition(String ssid) {
        super(ConditionType.WIFI);
        setSsid(RuleCondition.wifi(ssid).getSsid());
    }
}
