package com.autojump.app.domain;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Locale;

/**
 * Persistable condition specification. It intentionally contains no Android behavior so the
 * stored JSON is explicit, stable, and testable.
 */
public class RuleCondition implements Serializable {
    private ConditionType type;
    private int[] daysOfWeek;
    private int startHour;
    private int startMinute;
    private int endHour;
    private int endMinute;
    private String ssid;
    private String deviceName;
    private boolean shouldBeCharging;
    private double latitude;
    private double longitude;
    private int radiusMeters;
    private String placeName;

    protected RuleCondition(ConditionType type) {
        this.type = type;
    }

    public static RuleCondition time(int[] daysOfWeek, int startHour, int startMinute,
                                     int endHour, int endMinute) {
        validateTime(startHour, startMinute);
        validateTime(endHour, endMinute);
        if (daysOfWeek != null) {
            for (int day : daysOfWeek) {
                if (day < 1 || day > 7) {
                    throw new IllegalArgumentException("星期必须在 1 到 7 之间");
                }
            }
        }
        RuleCondition condition = new RuleCondition(ConditionType.TIME);
        condition.daysOfWeek = daysOfWeek == null ? null : Arrays.copyOf(daysOfWeek, daysOfWeek.length);
        condition.startHour = startHour;
        condition.startMinute = startMinute;
        condition.endHour = endHour;
        condition.endMinute = endMinute;
        return condition;
    }

    public static RuleCondition wifi(String ssid) {
        RuleCondition condition = new RuleCondition(ConditionType.WIFI);
        condition.ssid = requireText(ssid, "WiFi 名称不能为空");
        return condition;
    }

    public static RuleCondition bluetooth(String deviceName) {
        RuleCondition condition = new RuleCondition(ConditionType.BLUETOOTH);
        condition.deviceName = requireText(deviceName, "蓝牙设备名称不能为空");
        return condition;
    }

    public static RuleCondition charging(boolean shouldBeCharging) {
        RuleCondition condition = new RuleCondition(ConditionType.CHARGING);
        condition.shouldBeCharging = shouldBeCharging;
        return condition;
    }

    public static RuleCondition location(double latitude, double longitude, int radiusMeters,
                                         String placeName) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("纬度必须在 -90 到 90 之间");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("经度必须在 -180 到 180 之间");
        }
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("位置半径必须大于 0");
        }
        RuleCondition condition = new RuleCondition(ConditionType.LOCATION);
        condition.latitude = latitude;
        condition.longitude = longitude;
        condition.radiusMeters = radiusMeters;
        condition.placeName = placeName == null ? "" : placeName.trim();
        return condition;
    }

    private static void validateTime(int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("时间格式无效");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public String getDescription() {
        if (type == null) return "未知条件";
        switch (type) {
            case TIME:
                StringBuilder time = new StringBuilder();
                if (daysOfWeek != null && daysOfWeek.length > 0) {
                    String[] names = {"一", "二", "三", "四", "五", "六", "日"};
                    time.append("周");
                    for (int i = 0; i < daysOfWeek.length; i++) {
                        if (i > 0) time.append(",");
                        int day = daysOfWeek[i];
                        time.append(day >= 1 && day <= 7 ? names[day - 1] : "?");
                    }
                    time.append(" ");
                }
                return time.append(String.format(Locale.US, "%02d:%02d-%02d:%02d",
                        startHour, startMinute, endHour, endMinute)).toString();
            case WIFI:
                return "WiFi: " + ssid;
            case BLUETOOTH:
                return "蓝牙: " + deviceName;
            case CHARGING:
                return shouldBeCharging ? "充电中" : "未充电";
            case LOCATION:
                return String.format(Locale.US, "位置: %s (±%dm)",
                        placeName == null || placeName.isEmpty() ? "目标点" : placeName,
                        radiusMeters);
            default:
                return "未知条件";
        }
    }

    public ConditionType getType() { return type; }
    public void setType(ConditionType type) { this.type = type; }
    public int[] getDaysOfWeek() { return daysOfWeek == null ? null : Arrays.copyOf(daysOfWeek, daysOfWeek.length); }
    public void setDaysOfWeek(int[] daysOfWeek) { this.daysOfWeek = daysOfWeek == null ? null : Arrays.copyOf(daysOfWeek, daysOfWeek.length); }
    public int getStartHour() { return startHour; }
    public void setStartHour(int startHour) { this.startHour = startHour; }
    public int getStartMinute() { return startMinute; }
    public void setStartMinute(int startMinute) { this.startMinute = startMinute; }
    public int getEndHour() { return endHour; }
    public void setEndHour(int endHour) { this.endHour = endHour; }
    public int getEndMinute() { return endMinute; }
    public void setEndMinute(int endMinute) { this.endMinute = endMinute; }
    public String getSsid() { return ssid; }
    public void setSsid(String ssid) { this.ssid = ssid; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public boolean isShouldBeCharging() { return shouldBeCharging; }
    public void setShouldBeCharging(boolean shouldBeCharging) { this.shouldBeCharging = shouldBeCharging; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public int getRadiusMeters() { return radiusMeters; }
    public void setRadiusMeters(int radiusMeters) { this.radiusMeters = radiusMeters; }
    public String getPlaceName() { return placeName; }
    public void setPlaceName(String placeName) { this.placeName = placeName; }
}
