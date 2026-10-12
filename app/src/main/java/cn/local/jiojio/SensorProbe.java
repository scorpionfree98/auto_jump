package cn.local.jiojio;

import android.content.Context;
import android.hardware.*;
import java.util.*;

/** Caller owns lifecycle. start returns actual sensor registration, not a health verdict. */
public final class SensorProbe implements SensorEventListener,AutoCloseable {
    public interface Listener { void sample(String name,float[] values,int accuracy); }
    private final SensorManager manager;
    private final Listener listener;
    public SensorProbe(Context c,Listener listener) {manager=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);this.listener=listener;}
    public List<String> start() {
        close();List<String> registered=new ArrayList<>();
        for(int type:new int[]{Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_GYROSCOPE,Sensor.TYPE_LIGHT,Sensor.TYPE_PROXIMITY,Sensor.TYPE_MAGNETIC_FIELD}) {
            Sensor s=manager.getDefaultSensor(type);
            if(s!=null&&manager.registerListener(this,s,SensorManager.SENSOR_DELAY_UI))registered.add(s.getName());
        }
        return registered;
    }
    public void onSensorChanged(SensorEvent event){listener.sample(event.sensor.getName(),event.values.clone(),event.accuracy);}
    public void onAccuracyChanged(Sensor sensor,int accuracy){}
    public void close(){manager.unregisterListener(this);}
}
