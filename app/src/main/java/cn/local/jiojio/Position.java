package cn.local.jiojio;

import java.util.Locale;

public final class Position {
    public final String name;
    public final String locationName;
    public final double latitude, longitude;
    public final long timestamp;

    public Position(String name, double latitude, double longitude, long timestamp) {
        this(name, latitude, longitude, timestamp, "");
    }
    public Position(String name, double latitude, double longitude, long timestamp, String locationName) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90)
            throw new IllegalArgumentException("纬度须在 -90～90 之间");
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("经度须在 -180～180 之间");
        this.name = name == null || name.trim().isEmpty() ? "未命名位置" : name.trim();
        this.locationName = locationName == null ? "" : locationName.trim();
        this.latitude = latitude; this.longitude = longitude; this.timestamp = timestamp;
    }
    public static Position parse(String name, String lat, String lon) {
        try { return new Position(name, Double.parseDouble(lat.trim()), Double.parseDouble(lon.trim()), System.currentTimeMillis()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("请输入有效的经纬度数字"); }
    }
    public String coordinates() { return String.format(Locale.US, "%.6f, %.6f", latitude, longitude); }
    public String key() { return String.format(Locale.US, "%.6f/%.6f", latitude, longitude); }
}
