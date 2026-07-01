package com.autojump.app.condition;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import com.autojump.app.bean.Rule;

/**
 * 充电状态条件
 * 判断设备是否正在充电
 */
public class ChargingCondition extends Rule.Condition {
    private boolean shouldBeCharging;  // true=需要充电中, false=需要未充电

    public ChargingCondition(boolean shouldBeCharging) {
        this.shouldBeCharging = shouldBeCharging;
    }

    @Override
    public boolean match(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                if (batteryManager == null) return false;
                boolean isCharging = batteryManager.isCharging();
                return isCharging == shouldBeCharging;
            } else {
                IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = context.registerReceiver(null, filter);
                if (batteryStatus == null) return false;

                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                   status == BatteryManager.BATTERY_STATUS_FULL;
                return isCharging == shouldBeCharging;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return shouldBeCharging ? "充电中" : "未充电";
    }

    public boolean isShouldBeCharging() { return shouldBeCharging; }
    public void setShouldBeCharging(boolean shouldBeCharging) { this.shouldBeCharging = shouldBeCharging; }
}
