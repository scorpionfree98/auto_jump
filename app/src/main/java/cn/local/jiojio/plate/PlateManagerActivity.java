package cn.local.jiojio.plate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import cn.local.jiojio.R;

/** Offline plate editor/library. It does not read or write automation instructions. */
public final class PlateManagerActivity extends Activity {
    private static final int INK=0xff1b2b31,MUTED=0xff586b6b,PAPER=0xfff6f7f2,GREEN=0xff145b4c,MINT=0xffdfefe6;
    private SharedPreferences prefs;
    private PlateStore store;
    private final String[] numbers={"",""};
    private int style,size;
    private boolean bright,switching,readError;
    private EditText input;
    private PlateView preview;
    private TextView validation,ratio;
    private LinearLayout history;
    private Button quickSwitch;
    private Toast savedNotice;
    private final Button[] choices=new Button[2];

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("toolkit_plate_settings",MODE_PRIVATE);store=new PlateStore(this);
        style=prefs.getInt("style",0)==1?1:0;size=Math.max(50,Math.min(100,prefs.getInt("size",100)));bright=prefs.getBoolean("bright",true);
        for(int i=0;i<2;i++)numbers[i]=prefs.getString("number"+i,"");
        if(state!=null) {
            style=state.getInt("style",style)==1?1:0;
            for(int i=0;i<2;i++)numbers[i]=state.getString("number"+i,numbers[i]);
        }
        getWindow().setStatusBarColor(PAPER);getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(PAPER);
        LinearLayout page=column();page.setPadding(dp(20),dp(16),dp(20),dp(30));scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(safe.left,safe.top,safe.right,safe.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout navigation=row();add(page,navigation,0);
        Button back=button("‹ 工具合集",false);back.setOnClickListener(v->onBackPressed());equal(navigation,back,false);
        quickSwitch=button("切换车牌",false);quickSwitch.setId(R.id.plate_quick_switch);equal(navigation,quickSwitch,true);
        quickSwitch.setOnClickListener(v->guard(()->{
            java.util.List<PlateHistory.Entry> saved=store.list();
            String[] labels=new String[saved.size()];
            for(int i=0;i<labels.length;i++){PlateHistory.Entry plate=saved.get(i);labels[i]=(plate.pinned?"★ ":"")+plate.number+" · "+(plate.style==0?"普通蓝牌":"新能源绿牌");}
            new AlertDialog.Builder(this).setTitle("切换已保存车牌").setItems(labels,(d,which)->select(saved.get(which),false)).setNegativeButton("取消",null).show();
        }));
        add(page,label("全屏车牌",28,INK,true),16);
        add(page,label("本机记录 · 快速切换 · 专用车牌字形",13,MUTED,false),5);
        LinearLayout card=column();card.setPadding(dp(14),dp(14),dp(14),dp(14));card.setBackground(bg(Color.WHITE,20));add(page,card,18);
        add(card,label("实时预览",12,MUTED,false),0);
        preview=new PlateView(this);preview.setId(R.id.plate_preview);preview.setPadding(dp(2),dp(12),dp(2),dp(12));
        card.addView(preview,new LinearLayout.LayoutParams(-1,dp(145)));
        ratio=label("",12,MUTED,false);ratio.setGravity(Gravity.CENTER);add(card,ratio,0);
        LinearLayout types=row();add(page,types,18);
        for(int i=0;i<2;i++) {
            final int selected=i;choices[i]=button(i==0?"普通蓝牌":"新能源绿牌",false);
            choices[i].setId(i==0?R.id.plate_blue:R.id.plate_green);equal(types,choices[i],i>0);
            choices[i].setOnClickListener(v->switchStyle(selected));
        }
        add(page,label("车牌号码",14,INK,true),18);
        LinearLayout entry=row();add(page,entry,8);
        Button province=button("省份 ▾",false);entry.addView(province,new LinearLayout.LayoutParams(dp(88),dp(56)));
        province.setOnClickListener(v->{
            // Build explicitly: split("") behaves differently on some old runtimes.
            String[] values=new String[PlateSpec.PROVINCES.length()];
            for(int i=0;i<values.length;i++)values[i]=PlateSpec.PROVINCES.substring(i,i+1);
            final String[] provinces=values;
            new AlertDialog.Builder(this).setTitle("选择省份简称").setItems(provinces,(d,which)->{
                String n=PlateSpec.normalize(input.getText().toString());
                input.setText(provinces[which]+(!n.isEmpty()&&PlateSpec.PROVINCES.indexOf(n.charAt(0))>=0?n.substring(1):n));
                input.setSelection(input.length());
            }).show();
        });
        input=new EditText(this);input.setId(R.id.plate_number_input);input.setSingleLine(true);input.setTextSize(22);input.setTextColor(INK);
        input.setContentDescription("车牌号码");input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14),new InputFilter.AllCaps()});
        input.setPadding(dp(12),0,dp(8),0);input.setBackground(bg(Color.WHITE,12));
        LinearLayout.LayoutParams inputParams=new LinearLayout.LayoutParams(0,dp(56),1);inputParams.leftMargin=dp(10);entry.addView(input,inputParams);
        validation=label("",12,MUTED,false);validation.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(page,validation,8);
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                if(!switching){numbers[style]=s.toString();updatePreview();persistValid();}
            }
            public void afterTextChanged(Editable text) { }
        });
        LinearLayout actions=row();add(page,actions,14);
        Button save=button("保存车牌",false);save.setId(R.id.plate_save);equal(actions,save,false);
        Button show=button("全屏展示",true);show.setId(R.id.plate_show);equal(actions,show,true);
        save.setOnClickListener(v->saveCurrent(false));show.setOnClickListener(v->saveCurrent(true));
        add(page,label("点击保存或展示后加入记录；有效输入会自动保存在本机。",12,MUTED,false),8);
        add(page,label("展示设置",14,INK,true),22);
        TextView sizeLabel=label("车牌大小 · "+size+"%",13,MUTED,false);add(page,sizeLabel,10);
        SeekBar slider=new SeekBar(this);slider.setMax(50);slider.setProgress(size-50);slider.setContentDescription("车牌大小，百分之五十到一百");
        page.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int value,boolean user){size=value+50;sizeLabel.setText("车牌大小 · "+size+"%");prefs.edit().putInt("size",size).apply();}
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        Switch brightness=new Switch(this);brightness.setText("展示时提高亮度");brightness.setTextColor(INK);brightness.setChecked(bright);brightness.setMinHeight(dp(48));add(page,brightness,0);
        brightness.setOnCheckedChangeListener((v,checked)->{bright=checked;prefs.edit().putBoolean("bright",bright).apply();});
        add(page,label("自动横屏 · 保持常亮 · 双击或系统返回退出展示",12,MUTED,false),8);
        add(page,label("车牌记录",20,INK,true),24);
        add(page,label("点击号码切换；置顶优先，其余按最近使用排序。",12,MUTED,false),6);
        history=column();history.setId(R.id.plate_history);add(page,history,12);
        setContentView(scroll);switchStyle(style);renderHistory();
    }
    private void switchStyle(int selected) {
        switching=true;style=selected;input.setText(numbers[style]);input.setSelection(input.length());switching=false;
        for(int i=0;i<2;i++){choices[i].setTextColor(i==style?Color.WHITE:GREEN);choices[i].setBackground(bg(i==style?GREEN:MINT,12));choices[i].setSelected(i==style);}
        input.setHint(style==0?"例如：皖A12345":"例如：皖AD12345");updatePreview();persistValid();
    }
    private void updatePreview() {
        String n=PlateSpec.normalize(input.getText().toString());preview.setPlate(n,style);
        ratio.setText(style==0?"普通汽车 · 440 : 140":"小型新能源汽车 · 480 : 140");
        String error=PlateSpec.error(n,style);
        validation.setText(n.isEmpty()?"请输入车牌号码":error==null?"号码格式正确 · 输入已自动保存":error);
        validation.setTextColor(error==null?GREEN:MUTED);
    }
    private void persistValid() {
        String n=PlateSpec.normalize(input.getText().toString());
        if(PlateSpec.error(n,style)==null)prefs.edit().putInt("style",style).putString("number"+style,n).apply();
    }
    private void saveCurrent(boolean display) {
        String n=PlateSpec.normalize(input.getText().toString()),error=PlateSpec.error(n,style);
        if(error!=null){input.setError(error);input.requestFocus();return;}
        guard(()->{
            store.use(n,style);persistValid();renderHistory();
            if(display)openDisplay(n,style);else {
                if(savedNotice!=null)savedNotice.cancel();
                savedNotice=Toast.makeText(this,"已保存到车牌记录",Toast.LENGTH_SHORT);savedNotice.show();
            }
        });
    }
    private void select(PlateHistory.Entry plate,boolean display) {
        guard(()->{
            store.use(plate.number,plate.style);numbers[plate.style]=plate.number;switchStyle(plate.style);
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
            renderHistory();if(display)openDisplay(plate.number,plate.style);
        });
    }
    private void openDisplay(String number,int type) {
        if(savedNotice!=null)savedNotice.cancel();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
        startActivity(new Intent(this,PlateDisplayActivity.class).putExtra("number",number).putExtra("style",type).putExtra("size",size).putExtra("bright",bright));
    }
    private void renderHistory() {
        if(history==null)return;
        try {
            java.util.List<PlateHistory.Entry> saved=store.list();readError=false;history.removeAllViews();
            quickSwitch.setEnabled(!saved.isEmpty());quickSwitch.setAlpha(saved.isEmpty()?.5f:1f);
            if(saved.isEmpty()){add(history,label("还没有车牌记录，保存或展示一个号码后会出现在这里。",14,MUTED,false),0);return;}
            for(PlateHistory.Entry plate:saved) {
                LinearLayout card=column();card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(bg(Color.WHITE,16));add(history,card,10);
                Button choose=button((plate.pinned?"★ ":"")+plate.number,false);choose.setContentDescription("切换车牌 "+plate.number);add(card,choose,0);
                choose.setOnClickListener(v->select(plate,false));
                add(card,label((plate.style==0?"普通蓝牌":"新能源绿牌")+(plate.pinned?" · 已置顶":""),12,MUTED,false),6);
                LinearLayout actions=row();add(card,actions,10);
                Button show=button("展示",true);show.setContentDescription("展示车牌 "+plate.number);equal(actions,show,false);show.setOnClickListener(v->select(plate,true));
                Button pin=button(plate.pinned?"取消置顶":"置顶",false);pin.setContentDescription((plate.pinned?"取消置顶车牌 ":"置顶车牌 ")+plate.number);equal(actions,pin,true);
                pin.setOnClickListener(v->guard(()->{store.pin(plate.key(),!plate.pinned);renderHistory();}));
                Button delete=button("删除",false);delete.setTextColor(0xff9a312d);delete.setContentDescription("删除车牌 "+plate.number);equal(actions,delete,true);
                delete.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("删除车牌？")
                    .setMessage("删除 "+plate.number+" 的本机记录及对应的已保存输入，不影响其他车牌。")
                    .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->guard(()->delete(plate))).show());
            }
        } catch(RuntimeException error) {
            quickSwitch.setEnabled(false);quickSwitch.setAlpha(.5f);
            readError=true;history.removeAllViews();add(history,label("记录暂时无法读取，原数据已保留；不会覆盖旧记录。",14,0xff9a312d,false),0);
        }
    }
    private void delete(PlateHistory.Entry plate) {
        store.remove(plate.key());
        if(PlateSpec.normalize(numbers[plate.style]).equals(plate.number))numbers[plate.style]="";
        if(PlateSpec.normalize(prefs.getString("number"+plate.style,"")).equals(plate.number))prefs.edit().remove("number"+plate.style).apply();
        if(style==plate.style && PlateSpec.normalize(input.getText().toString()).equals(plate.number))input.setText("");
        renderHistory();
    }
    private void guard(Runnable action) {
        try { if(readError)throw new IllegalStateException("原记录无法读取，暂不能修改；请保留应用数据排查");action.run(); }
        catch(RuntimeException error){new AlertDialog.Builder(this).setTitle("未能完成").setMessage(error.getMessage()).setPositiveButton("知道了",null).show();}
    }
    @Override public void onBackPressed() {
        boolean unfinished=false;
        for(int i=0;i<2;i++){String draft=PlateSpec.normalize(numbers[i]);if(!draft.equals(prefs.getString("number"+i,"")) && PlateSpec.error(draft,i)!=null)unfinished=true;}
        if(unfinished)
            new AlertDialog.Builder(this).setTitle("号码尚未保存").setMessage("当前号码未完成，离开后将丢弃这次未完成的输入。")
                .setPositiveButton("继续编辑",null).setNegativeButton("放弃并返回",(d,w)->finish()).show();
        else super.onBackPressed();
    }
    @Override protected void onResume(){super.onResume();renderHistory();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("style",style);for(int i=0;i<2;i++)state.putString("number"+i,numbers[i]);}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private LinearLayout row(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.HORIZONTAL);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private TextView label(String value,int size,int color,boolean bold){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);if(bold)view.setTypeface(null,Typeface.BOLD);return view;}
    private Button button(String title,boolean primary){Button view=new Button(this);view.setText(title);view.setAllCaps(false);view.setTextSize(14);view.setTextColor(primary?Color.WHITE:GREEN);view.setBackground(bg(primary?GREEN:MINT,12));view.setMinHeight(dp(48));view.setPadding(dp(10),dp(8),dp(10),dp(8));return view;}
    private GradientDrawable bg(int color,int radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;}
    private void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);parent.addView(child,lp);}
    private void equal(LinearLayout parent,View child,boolean gap){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(gap)lp.leftMargin=dp(8);parent.addView(child,lp);}
}
