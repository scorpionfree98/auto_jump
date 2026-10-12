package cn.local.jiojio;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Guided manual and live hardware diagnostics. */
public final class HardwareActivity extends Activity {
    private static final int INK=Color.rgb(27,43,49),MUTED=Color.rgb(112,129,129),PAPER=Color.rgb(246,247,242),GREEN=Color.rgb(20,91,76),MINT=Color.rgb(223,239,230),WHITE=Color.WHITE;
    private TextView output;private LinearLayout page;private boolean inScreen;
    private SensorProbe sensors;private final Map<String,String> samples=new LinkedHashMap<>();
    private final HardwareProbes.Speaker speaker=new HardwareProbes.Speaker();private final HardwareProbes.Microphone mic=new HardwareProbes.Microphone();private final HardwareProbes.Camera camera=new HardwareProbes.Camera();
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(PAPER);getWindow().setNavigationBarColor(PAPER);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);showMenu();}
    private void showMenu(){
        inScreen=false;ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(20),dp(18),dp(20),dp(32));page.setBackgroundColor(PAPER);scroll.addView(page);setContentView(scroll);scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i;});
        TextView back=text("‹   返回工具合集",14,GREEN,true);page.addView(back,lp(-1,dp(42)));back.setGravity(Gravity.CENTER_VERTICAL);back.setOnClickListener(v->finish());
        TextView over=text("DEVICE CHECKUP",10,MUTED,true);page.addView(over,lp(-1,dp(22)));
        TextView title=text("硬件检测",28,INK,true);page.addView(title,lp(-1,dp(40)));
        TextView intro=text("逐项检查设备传感器与输入输出。需要人工观察的项目会明确提示，不会自动判定设备好坏。",13,MUTED,false);intro.setLineSpacing(dp(3),1f);page.addView(intro,lp(-1,-2));
        LinearLayout result=column();result.setPadding(dp(16),dp(14),dp(16),dp(14));result.setBackground(round(WHITE,18));result.setElevation(dp(1));LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(18);page.addView(result,rp);
        TextView rhead=text("检测状态",12,GREEN,true);result.addView(rhead,lp(-1,-2));output=text("选择下方项目开始检测。\n摄像头和麦克风仅在你点击后使用。",13,INK,false);output.setLineSpacing(dp(3),1f);LinearLayout.LayoutParams op=lp(-1,-2);op.topMargin=dp(8);result.addView(output,op);
        group("传感器","读取设备实时传感器数据",0);
        addTest("◉","实时传感器","方向、加速度等传感器数据",()->{if(sensors!=null)sensors.close();samples.clear();sensors=new SensorProbe(this,(name,values,accuracy)->{samples.put(name,Arrays.toString(values)+" · 精度 "+accuracy);StringBuilder b=new StringBuilder();for(Map.Entry<String,String> e:samples.entrySet())b.append(e.getKey()).append("\n").append(e.getValue()).append("\n\n");output.setText(b);});List<String> names=sensors.start();output.setText(names.isEmpty()?"此设备未报告可用传感器。":"已开启 "+names.size()+" 个传感器，等待实时数据…");});
        group("输入与反馈","需要你配合观察",17);
        addTest("◌","振动","发送 300 毫秒振动",()->output.setText(HardwareProbes.vibrate(this)?"振动指令已发送。请确认设备是否振动。":"此设备没有可用的振动器。"));
        addTest("♪","扬声器","播放一段短测试音",()->{speaker.play();output.setText("测试音已播放。请检查媒体音量并确认是否听见。");});
        addTest("⌁","触摸屏","绘制轨迹检查触控响应",()->{stop();inScreen=true;setContentView(new Screen(true));});
        group("相机与音频","开始后按系统提示授予权限",17);
        addTest("◉","麦克风","录制并分析 5 秒采样",()->{if(permission(Manifest.permission.RECORD_AUDIO))mic.start(this,this::report);});
        addTest("▣","前置相机","采集一帧并报告结果",()->{if(permission(Manifest.permission.CAMERA))camera.start(this,CameraCharacteristics.LENS_FACING_FRONT,this::report);});
        addTest("▣","后置相机","采集一帧并报告结果",()->{if(permission(Manifest.permission.CAMERA))camera.start(this,CameraCharacteristics.LENS_FACING_BACK,this::report);});
        group("显示","检查屏幕颜色和坏点",17);
        addTest("□","纯色屏幕","点击切换白、红、绿、蓝、黑",()->{stop();inScreen=true;setContentView(new Screen(false));});
        TextView stop=text("停止所有检测",14,GREEN,true);stop.setGravity(Gravity.CENTER);stop.setBackground(round(MINT,14));LinearLayout.LayoutParams sp=lp(-1,dp(48));sp.topMargin=dp(18);page.addView(stop,sp);stop.setOnClickListener(v->{stop();output.setText("已停止检测并释放硬件资源。");});
    }
    private void group(String title,String detail,int top){LinearLayout g=column();LinearLayout.LayoutParams p=lp(-1,-2);p.topMargin=dp(top);p.bottomMargin=dp(8);page.addView(g,p);TextView t=text(title,16,INK,true);g.addView(t,lp(-1,-2));TextView d=text(detail,11,MUTED,false);LinearLayout.LayoutParams dp=lp(-1,-2);dp.topMargin=dp(3);g.addView(d,dp);}
    private void addTest(String symbol,String title,String detail,Action action){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(13),dp(10),dp(13),dp(10));card.setBackground(round(WHITE,16));card.setClickable(true);card.setFocusable(true);card.setElevation(dp(1));LinearLayout.LayoutParams p=lp(-1,dp(68));p.bottomMargin=dp(8);page.addView(card,p);
        TextView icon=text(symbol,20,GREEN,true);icon.setGravity(Gravity.CENTER);icon.setBackground(round(MINT,12));card.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout copy=column();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=dp(12);card.addView(copy,cp);TextView t=text(title,14,INK,true);copy.addView(t,lp(-1,-2));TextView d=text(detail,11,MUTED,false);LinearLayout.LayoutParams dlp=lp(-1,-2);dlp.topMargin=dp(3);copy.addView(d,dlp);TextView arrow=text("›",23,MUTED,false);arrow.setGravity(Gravity.CENTER);card.addView(arrow,new LinearLayout.LayoutParams(dp(24),dp(36)));
        card.setOnClickListener(v->{try{action.run();}catch(Exception e){output.setText("检测失败："+e.getMessage());}});
    }
    private void report(String s){runOnUiThread(()->{if(!isFinishing())output.setText(s);});}
    private boolean permission(String p){if(checkSelfPermission(p)==android.content.pm.PackageManager.PERMISSION_GRANTED)return true;requestPermissions(new String[]{p},7);output.setText("授予权限后，再点一次对应项目开始检测。");return false;}
    private interface Action{void run()throws Exception;}
    private void stop(){if(sensors!=null)sensors.close();speaker.close();mic.close();camera.close();}
    @Override protected void onPause(){stop();super.onPause();}
    @Override public void onBackPressed(){stop();if(inScreen)showMenu();else finish();}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout.LayoutParams lp(int width,int height){return new LinearLayout.LayoutParams(width,height);}
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private final class Screen extends View {
        private final boolean touch;private int color=0;private final int[] colors={Color.WHITE,Color.RED,Color.GREEN,Color.BLUE,Color.BLACK};private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();
        Screen(boolean touch){super(HardwareActivity.this);this.touch=touch;setContentDescription(touch?"滑动绘制触摸轨迹，返回键退出":"点击切换纯色，返回键退出");}
        @Override protected void onDraw(Canvas c){c.drawColor(touch?Color.WHITE:colors[color]);paint.setColor(color==4?Color.WHITE:Color.DKGRAY);paint.setTextSize(dp(20));paint.setStyle(Paint.Style.FILL);c.drawText(touch?"滑动检查触控；返回键退出":"点击切换颜色；返回键退出",dp(22),dp(76),paint);if(touch){paint.setColor(GREEN);paint.setStrokeWidth(dp(4));paint.setStyle(Paint.Style.STROKE);c.drawPath(path,paint);}}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(touch){if(e.getAction()==0)path.moveTo(e.getX(),e.getY());else if(e.getAction()==2)path.lineTo(e.getX(),e.getY());}else if(e.getAction()==1){color=(color+1)%colors.length;performClick();}invalidate();return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
