package com.autojump.app.condition;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import com.autojump.app.bean.Rule;

/**
 * WiFi 连接条件
 * 判断当前连接的 WiFi SSID 是否匹配
 */
public class WiFiCondition extends Rule.Condition {
    private String ssid;  // WiFi 名称（SSID）

    public WiFiCondition(String ssid) {
        this.ssid = ssid;
    }

    @Override
    public boolean match(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null || !wifiManager.isWifiEnabled()) {
                return false;
            }

            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null) {
                return false;
            }

            String currentSsid = wifiInfo.getSSID();
            if (currentSsid == null) {
                return false;
            }

            // 去掉引号（系统返回的 SSID 带引号，如 "MyWiFi"）
            currentSsid = currentSsid.replace("\"", "");
            return currentSsid.equals(ssid);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "WiFi: " + ssid;
    }

    public String getSsid() { return ssid; }
    public void setSsid(String ssid) { this.ssid = ssid; }
}
