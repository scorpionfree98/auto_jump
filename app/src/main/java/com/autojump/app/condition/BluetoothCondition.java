package com.autojump.app.condition;

import com.autojump.app.domain.ConditionType;
import com.autojump.app.domain.RuleCondition;

/**
 * 蓝牙设备连接条件
 * 判断指定的蓝牙设备是否已连接
 */
@Deprecated
public class BluetoothCondition extends RuleCondition {

    public BluetoothCondition(String deviceName) {
        super(ConditionType.BLUETOOTH);
        setDeviceName(RuleCondition.bluetooth(deviceName).getDeviceName());
    }
}
