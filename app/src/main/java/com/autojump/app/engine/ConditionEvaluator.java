package com.autojump.app.engine;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import com.autojump.app.domain.RuleCondition;
import java.util.Calendar;
import java.util.Set;

public class ConditionEvaluator {
    public String unavailableReason(Context context, RuleCondition c) {
        if (c == null || c.getType() == null) return "条件数据无效";
        if ((c.getType() == com.autojump.app.domain.ConditionType.WIFI
                || c.getType() == com.autojump.app.domain.ConditionType.LOCATION)
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return "需要精确位置权限以检查 WiFi/位置";
        if (c.getType() == com.autojump.app.domain.ConditionType.BLUETOOTH
                && Build.VERSION.SDK_INT >= 31
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) return "需要附近设备权限";
        if (c.getType() == com.autojump.app.domain.ConditionType.WIFI) {
            LocationManager location = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (location != null && Build.VERSION.SDK_INT >= 28 && !location.isLocationEnabled()) {
                return "请开启系统定位开关，以读取 WiFi 名称";
            }
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null && wifi.isWifiEnabled() && wifi.getConnectionInfo() != null
                    && WifiManager.UNKNOWN_SSID.equals(wifi.getConnectionInfo().getSSID())) {
                return "WiFi 名称不可用，请检查定位权限及连接";
            }
        }
        return null;
    }
    public boolean matches(Context context, RuleCondition condition) {
        if (context == null || condition == null || condition.getType() == null) return false;
        try {
            switch (condition.getType()) {
                case TIME: return matchesTime(condition, Calendar.getInstance());
                case WIFI: return matchesWifi(context, condition);
                case BLUETOOTH: return matchesBluetooth(context, condition);
                case CHARGING: return matchesCharging(context, condition);
                case LOCATION: return matchesLocation(context, condition);
                default: return false;
            }
        } catch (SecurityException ignored) {
            return false;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean matchesTime(RuleCondition condition, Calendar now) {
        int[] days = condition.getDaysOfWeek();
        if (days != null && days.length > 0) {
            int calendarDay = now.get(Calendar.DAY_OF_WEEK);
            int currentDay = calendarDay == Calendar.SUNDAY ? 7 : calendarDay - 1;
            boolean dayMatches = false;
            for (int day : days) {
                if (day == currentDay) { dayMatches = true; break; }
            }
            if (!dayMatches) return false;
        }
        int current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int start = condition.getStartHour() * 60 + condition.getStartMinute();
        int end = condition.getEndHour() * 60 + condition.getEndMinute();
        if (end > start) return current >= start && current <= end;
        return current >= start || current <= end;
    }

    private boolean matchesWifi(Context context, RuleCondition condition) {
        WifiManager manager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (manager == null || !manager.isWifiEnabled()) return false;
        WifiInfo info = manager.getConnectionInfo();
        if (info == null || info.getSSID() == null) return false;
        return info.getSSID().replace("\"", "").equals(condition.getSsid());
    }

    private boolean matchesBluetooth(Context context, RuleCondition condition) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ActivityCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) return false;
        BluetoothAdapter adapter;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            adapter = manager == null ? null : manager.getAdapter();
        } else {
            adapter = BluetoothAdapter.getDefaultAdapter();
        }
        if (adapter == null || !adapter.isEnabled()) return false;
        Set<BluetoothDevice> devices = adapter.getBondedDevices();
        if (devices == null) return false;
        for (BluetoothDevice device : devices) {
            if (condition.getDeviceName().equals(device.getName())) return true;
        }
        return false;
    }

    private boolean matchesCharging(Context context, RuleCondition condition) {
        boolean charging;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (manager == null) return false;
            charging = manager.isCharging();
        } else {
            Intent status = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (status == null) return false;
            int value = status.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = value == BatteryManager.BATTERY_STATUS_CHARGING
                    || value == BatteryManager.BATTERY_STATUS_FULL;
        }
        return charging == condition.isShouldBeCharging();
    }

    private boolean matchesLocation(Context context, RuleCondition condition) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return false;
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) return false;
        Location gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        Location location;
        if (gps != null && network != null) location = gps.getTime() > network.getTime() ? gps : network;
        else location = gps != null ? gps : network;
        if (location == null) return false;
        float[] result = new float[1];
        Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                condition.getLatitude(), condition.getLongitude(), result);
        return result[0] <= condition.getRadiusMeters();
    }
}
