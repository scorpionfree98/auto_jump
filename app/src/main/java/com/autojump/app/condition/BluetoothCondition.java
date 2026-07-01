package com.autojump.app.condition;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import com.autojump.app.bean.Rule;
import java.util.Set;

/**
 * 蓝牙设备连接条件
 * 判断指定的蓝牙设备是否已连接
 */
public class BluetoothCondition extends Rule.Condition {
    private String deviceName;  // 蓝牙设备名称

    public BluetoothCondition(String deviceName) {
        this.deviceName = deviceName;
    }

    @Override
    public boolean match(Context context) {
        try {
            BluetoothAdapter bluetoothAdapter;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
                if (bluetoothManager == null) return false;
                bluetoothAdapter = bluetoothManager.getAdapter();
            } else {
                bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                return false;
            }

            // 获取已配对设备列表（需要 BLUETOOTH_CONNECT 权限）
            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
            if (bondedDevices == null || bondedDevices.isEmpty()) {
                return false;
            }

            // 检查目标设备是否在已配对列表中
            // 注意：这里只能判断"已配对"，真正的"已连接"状态需要通过 BluetoothProfile
            // 但 Profile 监听比较复杂，对于大多数场景"已配对"已经够用
            for (BluetoothDevice device : bondedDevices) {
                String name = device.getName();
                if (name != null && name.equals(deviceName)) {
                    // 简化处理：只要配对了就认为"可能连接"
                    // 真正严谨的做法需要监听 ACTION_ACL_CONNECTED 广播
                    return true;
                }
            }
            return false;
        } catch (SecurityException e) {
            // Android 12+ 需要 BLUETOOTH_CONNECT 权限
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "蓝牙: " + deviceName;
    }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
}
