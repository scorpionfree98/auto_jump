package cn.local.jiojio.plate;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

public final class PlateDisplayActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        if(getIntent().getBooleanExtra("bright",true)) {
            WindowManager.LayoutParams attrs=getWindow().getAttributes(); attrs.screenBrightness=1f;getWindow().setAttributes(attrs);
        }
        PlateView plate=new PlateView(this); plate.setBackgroundColor(Color.BLACK);
        int style=getIntent().getIntExtra("style",0);
        if(style!=PlateSpec.BLUE && style!=PlateSpec.GREEN){finish();return;}
        String number=getIntent().getStringExtra("number");
        if(PlateSpec.error(number,style)!=null){finish();return;}
        plate.setPlate(number,style);plate.setScaleFraction(getIntent().getIntExtra("size",100)/100f);
        plate.setOnApplyWindowInsetsListener((v,insets)->{
            // Avoid camera cutouts and temporarily visible system bars without
            // stretching the plate. Android 15 enforces edge-to-edge windows.
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.displayCutout() | WindowInsets.Type.systemBars());
                v.setPadding(safe.left,safe.top,safe.right,safe.bottom);
            } else if(Build.VERSION.SDK_INT>=28 && insets.getDisplayCutout()!=null){
                android.view.DisplayCutout cut=insets.getDisplayCutout();v.setPadding(cut.getSafeInsetLeft(),cut.getSafeInsetTop(),cut.getSafeInsetRight(),cut.getSafeInsetBottom());
            }
            return insets;
        });
        plate.setOnClickListener(v->{});
        GestureDetector gestures=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onSingleTapConfirmed(MotionEvent e){plate.performClick();return true;}
            @Override public boolean onDoubleTap(MotionEvent e){finish();return true;}
        });
        plate.setOnTouchListener((v,event)->gestures.onTouchEvent(event));
        plate.setContentDescription(plate.getContentDescription()+"，双击退出，也可使用系统返回");
        // Accessibility services expose long-click as a semantic exit action.
        plate.setOnLongClickListener(v->{finish();return true;});
        setContentView(plate);hideBars();
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)hideBars();}
    private void hideBars(){
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null){controller.hide(WindowInsets.Type.systemBars());controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
        }else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
