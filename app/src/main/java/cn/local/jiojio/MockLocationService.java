package cn.local.jiojio;

import android.app.*;
import android.content.*;
import android.location.*;
import android.os.*;
import java.util.*;

public final class MockLocationService extends Service {
    public static final String CHANGE="cn.local.jiojio.LOCATION_CHANGED";
    public static volatile boolean running=false;
    private static volatile String status="定位尚未启动";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<String> registered=new LinkedHashSet<>();
    private LocationManager manager;
    private Position target;
    private boolean foreground=false;
    private final Runnable tick=new Runnable() {
        public void run() {
            try { inject(); handler.postDelayed(this,1000); }
            catch (Exception e) { finish("定位中断："+explain(e)); }
        }
    };
    public static String status(Context c) {
        if (!running && (c.getSharedPreferences("session",0).getBoolean("active",false) || !c.getSharedPreferences("session",0).getStringSet("providers",Collections.emptySet()).isEmpty()))
            return "上次服务已中断。请停止清理后重新启动；尚未验证定位恢复。";
        return status;
    }
    public static boolean authorized(Context c) {
        AppOpsManager ops=(AppOpsManager)c.getSystemService(APP_OPS_SERVICE);
        return ops!=null && ops.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION,android.os.Process.myUid(),c.getPackageName())==AppOpsManager.MODE_ALLOWED;
    }
    public void onCreate() { super.onCreate();manager=(LocationManager)getSystemService(LOCATION_SERVICE); }
    public int onStartCommand(Intent i,int flags,int id) {
        if (i==null) { stopSelf();return START_NOT_STICKY; }
        if ("stop".equals(i.getAction())) { finish("定位已停止");return START_NOT_STICKY; }
        try {
            target=Position.parse(i.getStringExtra("name"),i.getStringExtra("lat"),i.getStringExtra("lon"));
            handler.removeCallbacks(tick);
            showNotification();
            if (!authorized(this)) throw new SecurityException("未选择本应用为模拟位置应用");
            // Persist provider ownership so a subsequent launch can clean up after process death.
            cleanup();
            for (String provider:Arrays.asList(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER)) {
                manager.addTestProvider(provider,false,false,false,false,true,true,true,android.location.provider.ProviderProperties.POWER_USAGE_LOW,android.location.provider.ProviderProperties.ACCURACY_FINE);
                registered.add(provider);
                getSharedPreferences("session",0).edit().putStringSet("providers",new HashSet<>(registered)).commit();
                manager.setTestProviderEnabled(provider,true);
            }
            inject();
            running=true;
            getSharedPreferences("session",0).edit().putBoolean("active",true).apply();
            new PositionStore(this).add("history",target);
            update("正在定位 · "+target.name+"\n"+target.coordinates()+"（WGS84）");
            handler.postDelayed(tick,1000);
        } catch (Exception e) { finish("启动失败："+explain(e)); }
        return START_NOT_STICKY;
    }
    private void inject() {
        if (!authorized(this)) throw new SecurityException("模拟位置授权已撤销");
        for (String provider:registered) {
            Location loc=new Location(provider);
            loc.setLatitude(target.latitude);loc.setLongitude(target.longitude);
            loc.setAccuracy(5f);loc.setAltitude(0);loc.setSpeed(0);loc.setBearing(0);
            loc.setTime(System.currentTimeMillis());loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            manager.setTestProviderLocation(provider,loc);
        }
    }
    private void showNotification() {
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("location","模拟定位",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,MockLocationService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,"location").setSmallIcon(R.drawable.ic_app).setContentTitle("工具合集 · 模拟定位")
            .setContentText(target.name+" · "+target.coordinates()).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"停止定位",stop).build()).build();
        if (Build.VERSION.SDK_INT>=34) startForeground(1,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1,n);
        foreground=true;
    }
    private void cleanup() {
        Set<String> owned=new HashSet<>(getSharedPreferences("session",0).getStringSet("providers",Collections.emptySet()));
        owned.addAll(registered);
        Set<String> remaining=new HashSet<>();
        for (String provider:owned) {
            try { manager.removeTestProvider(provider); }
            catch (IllegalArgumentException ignored) { /* Already removed. */ }
            catch (Exception e) { remaining.add(provider); }
        }
        registered.clear();
        getSharedPreferences("session",0).edit().putStringSet("providers",remaining).commit();
        if (!remaining.isEmpty()) throw new IllegalStateException("清理未完成，请重新授权模拟定位后点击停止，必要时重启手机");
    }
    private void finish(String message) {
        handler.removeCallbacks(tick);running=false;
        try { cleanup();getSharedPreferences("session",0).edit().putBoolean("active",false).apply(); }
        catch (Exception e) { message+="\n"+e.getMessage(); }
        update(message);
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE);foreground=false; }
        stopSelf();
    }
    private void update(String text) { status=text;sendBroadcast(new Intent(CHANGE).setPackage(getPackageName())); }
    private String explain(Exception e) { return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }
    public void onDestroy() {
        handler.removeCallbacks(tick);
        if (running) { running=false;try { cleanup();getSharedPreferences("session",0).edit().putBoolean("active",false).apply();update("定位服务已停止"); }
            catch(Exception e) { update(e.getMessage()); } }
        super.onDestroy();
    }
    public IBinder onBind(Intent intent) { return null; }
}
