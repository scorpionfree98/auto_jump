package cn.local.jiojio;

import android.app.ActivityManager;
import android.content.*;
import android.hardware.*;
import android.hardware.camera2.*;
import android.net.*;
import android.os.*;
import android.util.DisplayMetrics;
import java.util.*;

/** Read-only diagnostics: unavailable fields stay unavailable, never fabricated. */
public final class DeviceInspector {
    public static Map<String,String> snapshot(Context c) {
        Map<String,String> m=new LinkedHashMap<>();
        m.put("品牌 / 型号",Build.MANUFACTURER+" / "+Build.MODEL);
        m.put("Android",Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT);
        m.put("安全补丁",Build.VERSION.SECURITY_PATCH);
        m.put("架构",String.join(", ",Build.SUPPORTED_ABIS));
        m.put("CPU 可用核心",Integer.toString(Runtime.getRuntime().availableProcessors()));
        m.put("硬件平台",Build.HARDWARE);
        if(Build.VERSION.SDK_INT>=31)m.put("芯片",Build.SOC_MANUFACTURER+" "+Build.SOC_MODEL);
        ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();
        ((ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mem);
        m.put("内存（可用 / 总量）",size(mem.availMem)+" / "+size(mem.totalMem));
        StatFs storage=new StatFs(c.getFilesDir().getAbsolutePath());
        m.put("数据分区（可用 / 总量）",size(storage.getAvailableBytes())+" / "+size(storage.getTotalBytes()));
        DisplayMetrics dm=c.getResources().getDisplayMetrics();
        m.put("当前显示区域",dm.widthPixels+" × "+dm.heightPixels+" px · "+dm.densityDpi+" dpi");
        m.put("开机时长",String.format(Locale.US,"%.1f 小时",SystemClock.elapsedRealtime()/3600000.0));
        Intent battery=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(battery!=null) {
            int scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1),level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
            m.put("电量",scale>0&&level>=0?Math.round(level*100.0/scale)+"%":"不可用");
            int temp=battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
            m.put("电池温度",temp==Integer.MIN_VALUE?"不可用":temp/10.0+" °C");
            m.put("电池电压",battery.hasExtra(BatteryManager.EXTRA_VOLTAGE)?battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE,0)+" mV":"不可用");
        }
        ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network net=cm.getActiveNetwork();NetworkCapabilities cap=cm.getNetworkCapabilities(net);
        m.put("网络",cap==null?"未连接":cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"移动网络":cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)?"以太网":"其他（可能含 VPN）");
        m.put("互联网验证",cap!=null&&cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)?"系统已验证":"系统未验证");
        if(net!=null) {
            LinkProperties lp=cm.getLinkProperties(net);
            if(lp!=null) {m.put("本机地址",lp.getLinkAddresses().toString());m.put("DNS",lp.getDnsServers().toString());}
        }
        SensorManager sm=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);
        m.put("可见传感器",Integer.toString(sm.getSensorList(Sensor.TYPE_ALL).size()));
        try {
            CameraManager cams=(CameraManager)c.getSystemService(Context.CAMERA_SERVICE);
            for(String id:cams.getCameraIdList()) {
                CameraCharacteristics ch=cams.getCameraCharacteristics(id);
                Integer facing=ch.get(CameraCharacteristics.LENS_FACING);
                m.put("摄像头 "+id,(facing!=null&&facing==CameraCharacteristics.LENS_FACING_FRONT?"前置":"后置/外置")+" · 像素阵列 "+ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE));
            }
        }catch(Exception e){m.put("摄像头信息","不可用："+e.getClass().getSimpleName());}
        return m;
    }
    public static String size(long n){return String.format(Locale.US,"%.2f GiB",n/1073741824.0);}
}
