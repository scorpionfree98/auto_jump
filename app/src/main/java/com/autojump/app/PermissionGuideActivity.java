package com.autojump.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.autojump.app.R;

/**
 * 权限引导页面
 */
public class PermissionGuideActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_LOCATION = 1001;
    private static final int REQUEST_CODE_BLUETOOTH = 1002;

    private TextView tvLocationStatus, tvBluetoothStatus;
    private Button btnRequestLocation, btnRequestBluetooth, btnAppSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_guide);

        tvLocationStatus = findViewById(R.id.tv_location_status);
        tvBluetoothStatus = findViewById(R.id.tv_bluetooth_status);
        btnRequestLocation = findViewById(R.id.btn_request_location);
        btnRequestBluetooth = findViewById(R.id.btn_request_bluetooth);
        btnAppSettings = findViewById(R.id.btn_app_settings);

        btnRequestLocation.setOnClickListener(v -> requestLocationPermission());
        btnRequestBluetooth.setOnClickListener(v -> requestBluetoothPermission());
        btnAppSettings.setOnClickListener(v -> openAppSettings());

        updatePermissionStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
    }

    private void updatePermissionStatus() {
        // 位置权限
        boolean hasLocation = checkLocationPermission();
        tvLocationStatus.setText(hasLocation ? "已授权 ✓" : "未授权");
        btnRequestLocation.setEnabled(!hasLocation);

        // 蓝牙权限
        boolean hasBluetooth = checkBluetoothPermission();
        tvBluetoothStatus.setText(hasBluetooth ? "已授权 ✓" : "未授权");
        btnRequestBluetooth.setEnabled(!hasBluetooth);
    }

    private boolean checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean checkBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_CODE_LOCATION);
        }
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_CODE_BLUETOOTH);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH},
                    REQUEST_CODE_BLUETOOTH);
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updatePermissionStatus();
    }
}
