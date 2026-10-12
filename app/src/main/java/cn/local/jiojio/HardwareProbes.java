package cn.local.jiojio;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.*;
import android.os.*;
import android.util.Size;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real hardware operations with explicit permissions and closeable ownership. */
public final class HardwareProbes {
    public interface Callback { void result(String message); }
    public static boolean vibrate(Context c) {
        Vibrator v=(Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE);
        if(v==null||!v.hasVibrator())return false;
        v.vibrate(VibrationEffect.createOneShot(300,VibrationEffect.DEFAULT_AMPLITUDE));return true;
    }
    public static final class Speaker implements AutoCloseable {
        private ToneGenerator tone;
        public void play() { close();tone=new ToneGenerator(AudioManager.STREAM_MUSIC,35);tone.startTone(ToneGenerator.TONE_PROP_BEEP,700); }
        public void close(){if(tone!=null){tone.release();tone=null;}}
    }
    public static final class Microphone implements AutoCloseable {
        private AudioRecord recorder;
        private final AtomicBoolean active=new AtomicBoolean();
        private Thread worker;
        public void start(Context c,Callback callback) {
            if(c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("请先授权麦克风");
            close();
            int min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IllegalStateException("设备不支持录音参数");
            AudioRecord r=new AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,8192));
            if(r.getState()!=AudioRecord.STATE_INITIALIZED){r.release();throw new IllegalStateException("录音初始化失败");}
            recorder=r;
            try {r.startRecording();}catch(RuntimeException e){close();throw e;}
            active.set(true);
            worker=new Thread(()->{
                short[] buffer=new short[2048];long start=SystemClock.elapsedRealtime();
                try {
                    while(active.get()&&SystemClock.elapsedRealtime()-start<5000) {
                        int n=r.read(buffer,0,buffer.length);
                        if(n<0)throw new IllegalStateException("录音读取失败："+n);
                        if(n==0)continue;
                        double square=0;for(int i=0;i<n;i++)square+=(double)buffer[i]*buffer[i];
                        double rms=Math.sqrt(square/n);
                        callback.result(String.format(Locale.US,"麦克风采样 %d · 电平 %.1f dBFS（非声压级）",n,20*Math.log10(Math.max(1,rms)/32768.0)));
                    }
                    if(active.get())callback.result("5 秒采样结束；有采样不等于麦克风音质合格");
                }catch(Exception e){if(active.get())callback.result("麦克风检测失败："+e.getMessage());}
                finally {synchronized(this){if(recorder==r){try{r.stop();}catch(Exception ignored){}r.release();recorder=null;active.set(false);}}}
            },"microphone-probe");worker.start();
        }
        public void close(){
            active.set(false);
            AudioRecord r=recorder;if(r!=null){try{r.stop();}catch(Exception ignored){}}
            Thread t=worker;if(t!=null&&t!=Thread.currentThread()){try{t.join(1500);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
            synchronized(this){if(recorder!=null){recorder.release();recorder=null;}}
            worker=null;
        }
    }
    public static final class Camera implements AutoCloseable {
        private CameraDevice device;private CameraCaptureSession session;private ImageReader reader;
        private HandlerThread thread;private boolean closed=true;private Handler handler;private long generation;
        public synchronized void start(Context c,int facing,Callback callback)throws Exception {
            close();if(c.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("请先授权相机");
            CameraManager manager=(CameraManager)c.getSystemService(Context.CAMERA_SERVICE);String selected=null;Size chosen=null;
            for(String id:manager.getCameraIdList()) {
                CameraCharacteristics ch=manager.getCameraCharacteristics(id);
                if(Objects.equals(ch.get(CameraCharacteristics.LENS_FACING),facing)) {
                    android.hardware.camera2.params.StreamConfigurationMap map=ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if(map==null)continue;Size[] sizes=map.getOutputSizes(ImageFormat.YUV_420_888);
                    if(sizes==null||sizes.length==0)continue;
                    selected=id;chosen=Arrays.stream(sizes).min(Comparator.comparingLong(s->(long)s.getWidth()*s.getHeight())).get();break;
                }
            }
            if(selected==null)throw new IllegalStateException("未发现支持检测的该方向摄像头");
            final long run=++generation;closed=false;thread=new HandlerThread("camera-probe");thread.start();handler=new Handler(thread.getLooper());
            reader=ImageReader.newInstance(chosen.getWidth(),chosen.getHeight(),ImageFormat.YUV_420_888,2);
            reader.setOnImageAvailableListener(r->{
                synchronized(Camera.this){if(closed||run!=generation||r!=reader)return;
                    try(Image image=r.acquireLatestImage()){if(image!=null){callback.result("摄像头成功输出图像："+image.getWidth()+" × "+image.getHeight()+"；未保存图像");close();}}
                    catch(Exception e){callback.result("读取图像失败："+e.getMessage());close();}
                }
            },handler);
            handler.postDelayed(()->{synchronized(Camera.this){if(!closed&&run==generation){callback.result("相机检测超时");close();}}},8000);
            try {manager.openCamera(selected,new CameraDevice.StateCallback(){
                public void onOpened(CameraDevice camera){synchronized(Camera.this){if(closed||run!=generation){camera.close();return;}device=camera;
                    try {camera.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
                        public void onConfigured(CameraCaptureSession s){synchronized(Camera.this){if(closed||run!=generation){s.close();return;}session=s;
                            try {CaptureRequest.Builder b=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);b.addTarget(reader.getSurface());s.setRepeatingRequest(b.build(),null,handler);}
                            catch(Exception e){callback.result("相机请求失败："+e.getMessage());close();}
                        }}
                        public void onConfigureFailed(CameraCaptureSession s){synchronized(Camera.this){if(run!=generation)return;callback.result("相机会话配置失败");close();}}
                    },handler);}catch(Exception e){callback.result("相机打开后配置失败："+e.getMessage());close();}
                }}
                public void onDisconnected(CameraDevice camera){camera.close();synchronized(Camera.this){if(run!=generation)return;callback.result("相机连接断开");close();}}
                public void onError(CameraDevice camera,int error){camera.close();synchronized(Camera.this){if(run!=generation)return;callback.result("相机错误："+error);close();}}
            },handler);}catch(Exception e){close();throw e;}
        }
        public synchronized void close(){generation++;closed=true;if(handler!=null)handler.removeCallbacksAndMessages(null);if(session!=null)session.close();if(device!=null)device.close();if(reader!=null)reader.close();if(thread!=null)thread.quitSafely();session=null;device=null;reader=null;thread=null;handler=null;}
    }
}
