package cn.local.jiojio.plate;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import cn.local.jiojio.R;
import java.util.Map;

/** Refuses real devices/production package; fixtures never enter the user's library. */
public final class PlateRuntimeInstrumentation extends Instrumentation {
    private int checks;
    private PlateManagerActivity manager;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart() {
        Context context=getTargetContext();Bundle result=new Bundle();int code=Activity.RESULT_OK;
        if(!context.getPackageName().equals("cn.local.toolkit.runtime")
                || !(android.os.Build.HARDWARE.contains("ranchu")||android.os.Build.HARDWARE.contains("goldfish"))) {
            result.putString("stream","FAIL: isolated emulator required\n");finish(Activity.RESULT_CANCELED,result);return;
        }
        SharedPreferences library=context.getSharedPreferences("toolkit_plate_library",0),settings=context.getSharedPreferences("toolkit_plate_settings",0),rules=context.getSharedPreferences("jump_config_sp",0);
        Map<String,?> priorLibrary=library.getAll(),priorSettings=settings.getAll(),priorRules=rules.getAll();
        try {
            library.edit().clear().commit();settings.edit().clear().commit();
            manager=(PlateManagerActivity)startActivitySync(new Intent(context,PlateManagerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            PlateStore store=new PlateStore(context);
            check(store.list().isEmpty(),"First launch inserted demo records");
            edit("京A12345");screenshot("plate-editor-blue.png");click(R.id.plate_save);check(store.list().size()==1,"Save failed");
            edit("京A12345");click(R.id.plate_save);check(store.list().size()==1,"Duplicate record");
            click(R.id.plate_green);edit("皖AD12345");click(R.id.plate_save);
            check(store.list().size()==2,"Second style missing");
            check(store.list().get(0).style==1,"Recency/type lost");
            clickDescription("置顶车牌 京A12345");
            check(store.list().get(0).number.equals("京A12345") && store.list().get(0).pinned,"Pin/sort failed");
            click(R.id.plate_quick_switch);dialogButton("★ 京A12345 · 普通蓝牌");
            check(((EditText)manager.findViewById(R.id.plate_number_input)).getText().toString().equals("京A12345"),"Quick switch failed");
            check(new PlateStore(context).list().get(0).pinned,"Pin did not reload");
            clickDescription("取消置顶车牌 京A12345");check(!store.list().get(0).pinned,"Unpin failed");
            // Confirm cancel is non-mutating, then confirm actual delete including saved input.
            clickDescription("删除车牌 京A12345");dialogButton("取消");check(store.list().size()==2,"Cancel deleted a record");
            clickDescription("删除车牌 京A12345");dialogButton("删除");
            check(store.list().size()==1 && store.list().get(0).style==1,"Delete affected other style");
            check(!settings.contains("number0"),"Deleted input remained persisted");
            check(((EditText)manager.findViewById(R.id.plate_number_input)).length()==0,"Deleted input remained in editor");
            check(!new PlateStore(context).list().get(0).number.equals("京A12345"),"Deleted record resurrected on reload");
            // Display a known test fixture; preview/rendering uses bundled outlines, not Typeface.
            click(R.id.plate_green);edit("皖AD12345");
            Instrumentation.ActivityMonitor screen=addMonitor(PlateDisplayActivity.class.getName(),null,false);
            click(R.id.plate_show);Activity display=waitForMonitorWithTimeout(screen,8000);removeMonitor(screen);
            check(display!=null,"Full-screen launch failed");
            check(display.getRequestedOrientation()==android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,"Landscape request missing");
            check(display.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE,"Display not actually landscape");
            check((display.getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0,"Keep-screen-on flag missing");
            check(display.getWindow().getAttributes().screenBrightness==1f,"Window brightness missing");
            screenshot("plate-display-green.png");
            runOnMainSync(display::finish);waitForIdleSync();
            // Validate a second recreation rather than trusting the in-memory store.
            runOnMainSync(manager::finish);waitForIdleSync();
            manager=(PlateManagerActivity)startActivitySync(new Intent(context,PlateManagerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check(new PlateStore(context).list().size()==1,"History changed after activity recreation");
            check(((EditText)manager.findViewById(R.id.plate_number_input)).getText().toString().equals("皖AD12345"),"Saved selection lost");
            edit("INVALID");screen=addMonitor(PlateDisplayActivity.class.getName(),null,false);click(R.id.plate_show);
            check(waitForMonitorWithTimeout(screen,800)==null,"Invalid draft opened full-screen");removeMonitor(screen);
            check(store.list().size()==1,"Invalid draft entered history");
            clickDescription("删除车牌 皖AD12345");dialogButton("删除");
            check(store.list().isEmpty(),"Deleting final record did not empty library");
            check(!manager.findViewById(R.id.plate_quick_switch).isEnabled(),"Empty quick switch remained enabled");
            // A storage error must neither erase nor silently repair the raw history.
            library.edit().putString("history","invalid-json").commit();
            boolean rejected=false;try {store.use("京A12345",0);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected && library.getString("history","").equals("invalid-json"),"Unreadable data was overwritten");
            check(priorRules.equals(rules.getAll()),"Automation rules changed");
            result.putInt("checks_passed",checks);result.putString("stream","PASS: "+checks+" plate checks\n");
        } catch(Throwable error) { code=Activity.RESULT_CANCELED;result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error)); }
        finally {
            if(manager!=null){runOnMainSync(manager::finish);waitForIdleSync();}
            restore(library,priorLibrary);restore(settings,priorSettings);
        }
        finish(code,result);
    }
    private void click(int id){runOnMainSync(()->manager.findViewById(id).performClick());waitForIdleSync();}
    private void edit(String text){runOnMainSync(()->((EditText)manager.findViewById(R.id.plate_number_input)).setText(text));waitForIdleSync();}
    private void clickDescription(String description) {
        runOnMainSync(()->{View found=find(manager.getWindow().getDecorView(),description);if(found==null)throw new AssertionError("Action missing");found.performClick();});waitForIdleSync();
    }
    private View find(View view,String description) {
        if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),description);if(found!=null)return found;}}
        return null;
    }
    private void dialogButton(String label) {
        // Dialogs are separate windows: UiAutomation handles the actual confirmation controls.
        android.view.accessibility.AccessibilityNodeInfo button=null;
        long deadline=SystemClock.elapsedRealtime()+3000;
        while(button==null && SystemClock.elapsedRealtime()<deadline) {
            android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label))
                if(label.contentEquals(node.getText()==null?"":node.getText()) && node.isClickable())button=node;
            if(button==null)SystemClock.sleep(100);
        }
        if(button==null)throw new AssertionError("Dialog button missing: "+label);
        button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);SystemClock.sleep(250);waitForIdleSync();
    }
    private void screenshot(String name) throws Exception {
        waitForIdleSync();SystemClock.sleep(350);
        android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
        if(bitmap==null)throw new AssertionError("Screenshot unavailable");
        try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),name))) {
            if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output))throw new AssertionError("Screenshot encoding failed");
        } finally {bitmap.recycle();}
    }
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    private void restore(SharedPreferences prefs,Map<String,?> snapshot) {
        SharedPreferences.Editor editor=prefs.edit().clear();
        for(Map.Entry<String,?> item:snapshot.entrySet()) {
            Object value=item.getValue();String key=item.getKey();
            if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Integer)editor.putInt(key,(Integer)value);
            else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);else if(value instanceof Long)editor.putLong(key,(Long)value);
            else if(value instanceof Float)editor.putFloat(key,(Float)value);
        }
        editor.commit();
    }
}
