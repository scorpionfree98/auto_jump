package com.autojump.app.condition;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import com.autojump.app.bean.Rule;

/**
 * 地理位置条件
 * 判断当前位置是否在目标位置的指定半径内
 */
public class LocationCondition extends Rule.Condition {
    private double latitude;   // 目标纬度
    private double longitude;  // 目标经度
    private int radiusMeters;  // 半径（米）
    private String placeName;  // 地点名称（用于显示）

    public LocationCondition(double latitude, double longitude, int radiusMeters, String placeName) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters;
        this.placeName = placeName;
    }

    @Override
    public boolean match(Context context) {
        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        try {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                return false;
            }

            // 尝试从 GPS 和网络获取最近的位置
            Location lastKnownLocation = null;
            try {
                Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                if (gpsLocation != null && networkLocation != null) {
                    lastKnownLocation = gpsLocation.getTime() > networkLocation.getTime() ? gpsLocation : networkLocation;
                } else if (gpsLocation != null) {
                    lastKnownLocation = gpsLocation;
                } else if (networkLocation != null) {
                    lastKnownLocation = networkLocation;
                }
            } catch (SecurityException e) {
                return false;
            }

            if (lastKnownLocation == null) {
                return false;
            }

            // 计算距离
            float[] results = new float[1];
            Location.distanceBetween(
                lastKnownLocation.getLatitude(),
                lastKnownLocation.getLongitude(),
                latitude,
                longitude,
                results
            );

            return results[0] <= radiusMeters;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return String.format("位置: %s (±%dm)", placeName != null ? placeName : "目标点", radiusMeters);
    }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public int getRadiusMeters() { return radiusMeters; }
    public void setRadiusMeters(int radiusMeters) { this.radiusMeters = radiusMeters; }

    public String getPlaceName() { return placeName; }
    public void setPlaceName(String placeName) { this.placeName = placeName; }
}
