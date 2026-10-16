package cn.local.jiojio;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Main toolbox screen and entry points for device utilities. */
public final class ToolboxActivity extends Activity {
    private static final int INK=Color.rgb(27,43,49), MUTED=Color.rgb(112,129,129), PAPER=Color.rgb(246,247,242);
    private static final int GREEN=Color.rgb(20,91,76), MINT=Color.rgb(223,239,230), LINE=Color.rgb(229,233,226), WHITE=Color.WHITE;
    private EditText name,latitude,longitude,locationName;
    private TextView status,network;
    private TextView speedAction;
    private TextView startAction,stopAction,favoriteAction;
    private PositionStore store;
    private NetworkProbe probe;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final BroadcastReceiver changes=new BroadcastReceiver(){public void onReceive(Context c,Intent i){refreshStatus();}};

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new PositionStore(this);
        getWindow().setStatusBarColor(PAPER);getWindow().setNavigationBarColor(PAPER);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(20),dp(12),dp(20),dp(36));page.setBackgroundColor(PAPER);scroll.addView(page);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});

        LinearLayout masthead=new LinearLayout(this);masthead.setGravity(Gravity.CENTER_VERTICAL);masthead.setPadding(0,dp(5),0,dp(19));page.addView(masthead,matchWrap());
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);masthead.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        label(brand,"TOOLKIT   /   DEVICE UTILITIES",10,MUTED,true,0,3);label(brand,"工具合集",28,INK,true,0,0);
        TextView mark=new TextView(this);mark.setText("T");mark.setTextColor(GREEN);mark.setTextSize(18);mark.setTypeface(Typeface.DEFAULT,Typeface.BOLD);mark.setGravity(Gravity.CENTER);mark.setBackground(circle(MINT));masthead.addView(mark,new LinearLayout.LayoutParams(dp(42),dp(42)));

        LinearLayout hero=column();hero.setPadding(dp(20),dp(18),dp(20),dp(19));hero.setBackground(round(Color.rgb(23,67,58),22));hero.setElevation(dp(2));page.addView(hero,margin(matchWrap(),0,0,0,18));
        LinearLayout heroTop=row();TextView liveDot=new TextView(this);liveDot.setText("●");liveDot.setTextColor(Color.rgb(150,221,176));liveDot.setTextSize(10);heroTop.addView(liveDot,wrap());label(heroTop,"LOCATION SERVICE",10,Color.rgb(177,211,193),true,dp(7),0);hero.addView(heroTop,matchWrap());
        label(hero,"把手机工具，放在一个地方。",21,WHITE,true,0,dp(5));
        status=new TextView(this);status.setTextColor(Color.rgb(210,229,217));status.setTextSize(13);status.setLineSpacing(dp(3),1f);hero.addView(status,margin(matchWrap(),0,dp(4),0,0));

        sectionTitle(page,"模拟定位","选择地点并注入设备位置",0,10);
        LinearLayout location=column();location.setPadding(dp(16),dp(15),dp(16),dp(16));location.setBackground(round(WHITE,20));location.setElevation(dp(1));page.addView(location,matchWrap());
        label(location,"位置名称",11,MUTED,true,0,dp(5));name=edit(location,"例如：公司、家",state==null?"当前位置":state.getString("name","当前位置"),false);
        label(location,"地点名称",11,MUTED,true,0,dp(5));locationName=edit(location,"地图选点自动填写，也可手动输入",state==null?"":state.getString("locationName",""),false);
        LinearLayout coords=row();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(10);location.addView(coords,cp);
        LinearLayout latCol=column(),lonCol=column();coords.addView(latCol,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(10);coords.addView(lonCol,lp);
        label(latCol,"纬度",11,MUTED,true,0,dp(5));latitude=edit(latCol,"31.2304",state==null?"":state.getString("lat",""),true);
        label(lonCol,"经度",11,MUTED,true,0,dp(5));longitude=edit(lonCol,"121.4737",state==null?"":state.getString("lon",""),true);
        LinearLayout mapButton=actionTile("⌖","获取当前位置或搜索地点","打开地图后优先定位当前位置，也可搜索选点",false);location.addView(mapButton,margin(matchWrap(),0,dp(12),0,0));
        mapButton.setOnClickListener(v->{Intent pick=new Intent(this,MapPickerActivity.class);pick.putExtra("lat",latitude.getText().toString());pick.putExtra("lon",longitude.getText().toString());startActivityForResult(pick,20);});
        favoriteAction=buttonView("收藏当前选点",MINT,GREEN);location.addView(favoriteAction,margin(matchWrap(),0,dp(8),0,0));favoriteAction.setOnClickListener(v->guard(this::saveFavorite));
        startAction=buttonView("启动模拟定位",GREEN,WHITE);location.addView(startAction,margin(matchWrap(),0,dp(10),0,0));startAction.setOnClickListener(v->guard(()->startMock()));
        stopAction=buttonView("停止并清理",WHITE,GREEN);stopAction.setBackground(roundStroke(WHITE,13,LINE));applyRipple(stopAction);LinearLayout.LayoutParams stopParams=margin(matchWrap(),0,dp(8),0,0);location.addView(stopAction,stopParams);stopAction.setVisibility(View.GONE);stopAction.setOnClickListener(v->startService(new Intent(this,MockLocationService.class).setAction("stop")));
        TextWatcher coordinateWatcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){locationName.setText("");syncLocationActions();}public void afterTextChanged(Editable e){}};latitude.addTextChangedListener(coordinateWatcher);longitude.addTextChangedListener(coordinateWatcher);

        sectionTitle(page,"常用工具","快速访问收藏和设备检测",dp(23),10);
        LinearLayout savedRow=row();page.addView(savedRow,matchWrap());
        LinearLayout favorites=actionTile("♡","位置收藏","保存的地点",true);savedRow.addView(favorites,new LinearLayout.LayoutParams(0,dp(106),1));favorites.setOnClickListener(v->showSaved("favorites"));
        LinearLayout history=actionTile("↺","运行记录","已启动的位置",true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,dp(106),1);hp.leftMargin=dp(10);savedRow.addView(history,hp);history.setOnClickListener(v->showSaved("history"));
        LinearLayout toolRow=row();LinearLayout.LayoutParams trp=new LinearLayout.LayoutParams(-1,-2);trp.topMargin=dp(10);page.addView(toolRow,trp);
        LinearLayout device=actionTile("▦","设备信息","系统与网络",true);toolRow.addView(device,new LinearLayout.LayoutParams(0,dp(106),1));device.setOnClickListener(v->showDeviceInfo());
        LinearLayout hardware=actionTile("⌁","硬件检测","传感器、相机等",true);LinearLayout.LayoutParams hwp=new LinearLayout.LayoutParams(0,dp(106),1);hwp.leftMargin=dp(10);toolRow.addView(hardware,hwp);hardware.setOnClickListener(v->startActivity(new Intent(this,HardwareActivity.class)));

        LinearLayout plates=actionTile("▤","全屏车牌","历史切换 · 置顶 · 本机保存",true);plates.setId(R.id.tool_plate_entry);page.addView(plates,margin(matchWrap(),0,dp(10),0,0));plates.setOnClickListener(v->startActivity(new Intent(this,cn.local.jiojio.plate.PlateManagerActivity.class)));

        sectionTitle(page,"网络测速","短样本 HTTPS 链路测试",dp(23),10);
        LinearLayout speed=column();speed.setPadding(dp(16),dp(14),dp(16),dp(14));speed.setBackground(round(WHITE,20));page.addView(speed,matchWrap());
        LinearLayout speedHead=row();speedHead.setGravity(Gravity.CENTER_VERTICAL);speed.addView(speedHead,matchWrap());
        TextView provider=new TextView(this);provider.setText("Cloudflare");provider.setTextSize(16);provider.setTextColor(INK);provider.setTypeface(Typeface.DEFAULT,Typeface.BOLD);speedHead.addView(provider,wrap());network=new TextView(this);network.setText("尚未测试");network.setTextColor(MUTED);network.setTextSize(12);network.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams networkParams=new LinearLayout.LayoutParams(0,-2,1);networkParams.leftMargin=dp(12);speedHead.addView(network,networkParams);
        label(speed,"下载最多 8 MiB · 上传 2 MiB · 可能消耗移动数据",11,MUTED,false,0,dp(12));
        speedAction=buttonView("开始测速",GREEN,WHITE);speed.addView(speedAction,matchWrap());speedAction.setOnClickListener(v->{if(probe!=null){network.setText("正在停止…");speedAction.setEnabled(false);probe.cancel();}else new AlertDialog.Builder(this).setTitle("开始网络测速").setMessage("将向 speed.cloudflare.com 发送少量测试流量，可能使用移动数据。是否继续？").setNegativeButton("返回",null).setPositiveButton("开始",(d,w)->runNetwork()).show();});
        label(page,"坐标选点会自动转换为 WGS-84 后注入。模拟定位仅影响选择使用本应用的测试环境。",11,MUTED,false,dp(17),0);

        if(Build.VERSION.SDK_INT>=33)registerReceiver(changes,new IntentFilter(MockLocationService.CHANGE),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(changes,new IntentFilter(MockLocationService.CHANGE));refreshStatus();
    }

    private void saveFavorite(){
        Position point=Position.parse(name.getText().toString(),latitude.getText().toString(),longitude.getText().toString());
        EditText title=new EditText(this);title.setSingleLine(true);title.setHint("例如：家、公司");title.setText(point.name);title.selectAll();
        LinearLayout box=column();box.setPadding(dp(24),dp(8),dp(24),0);box.addView(title,matchWrap());
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("收藏位置").setMessage("给这个位置起个名字，方便下次查找。相同坐标将更新原收藏。").setView(box).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=title.getText().toString().trim();
            if(value.isEmpty()){title.setError("请输入收藏名称");return;}
            guard(()->{store.add("favorites",new Position(value,point.latitude,point.longitude,System.currentTimeMillis(),locationName.getText().toString()));name.setText(value);dialog.dismiss();Toast.makeText(this,"已收藏，可在位置收藏中查看",Toast.LENGTH_SHORT).show();});
        }));dialog.show();
    }
    private void startMock(){Position p=Position.parse(name.getText().toString(),latitude.getText().toString(),longitude.getText().toString());if(!MockLocationService.authorized(this)){new AlertDialog.Builder(this).setTitle("还未选择模拟位置应用").setMessage("请在开发者选项中，将“选择模拟位置信息应用”设为“工具合集”。").setNegativeButton("稍后",null).setPositiveButton("打开开发者选项",(d,w)->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}).show();return;}if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);return;}Intent i=new Intent(this,MockLocationService.class).putExtra("name",p.name).putExtra("lat",Double.toString(p.latitude)).putExtra("lon",Double.toString(p.longitude));status.setText("正在启动模拟定位…\n授权状态：已选择此应用");startForegroundService(i);}
    private void showDeviceInfo(){StringBuilder b=new StringBuilder();for(Map.Entry<String,String> e:DeviceInspector.snapshot(this).entrySet())b.append(e.getKey()).append("\n").append(e.getValue()).append("\n\n");new AlertDialog.Builder(this).setTitle("设备信息").setMessage(b).setPositiveButton("完成",null).show();}
    private void runNetwork(){if(probe!=null)return;NetworkProbe current=new NetworkProbe();probe=current;speedAction.setText("停止测速");speedAction.setEnabled(true);network.setText("准备中…");worker.execute(()->{try{NetworkProbe.Result r=current.run("https://speed.cloudflare.com/__down?bytes=8388608","https://speed.cloudflare.com/__up",(phase,n)->runOnUiThread(()->network.setText(phase+" · "+n/1024+" KB")));runOnUiThread(()->network.setText(String.format(Locale.US,"↓ %.2f  ↑ %.2f Mbps",r.downloadMbps,r.uploadMbps)));}catch(Exception e){runOnUiThread(()->network.setText(current.isCancelled()?"已停止":"测试失败"));}finally{runOnUiThread(()->{if(probe==current)probe=null;speedAction.setText("开始测速");speedAction.setEnabled(true);});}});}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==20&&resultCode==RESULT_OK&&data!=null){latitude.setText(data.getStringExtra("lat"));longitude.setText(data.getStringExtra("lon"));String pickedName=data.getStringExtra("name");locationName.setText(pickedName==null?"":pickedName);if(pickedName!=null&&!pickedName.isEmpty())name.setText(pickedName);syncLocationActions();}}
    private void refreshStatus(){if(status!=null){boolean authorized=MockLocationService.authorized(this);status.setText(MockLocationService.status(this)+"\n模拟位置授权　"+(authorized?"已设置":"待设置"));syncLocationActions();}}
    private void syncLocationActions(){if(startAction==null||latitude==null||longitude==null)return;boolean hasPoint=false;try{Position.parse(name.getText().toString(),latitude.getText().toString(),longitude.getText().toString());hasPoint=true;}catch(RuntimeException ignored){}String current=MockLocationService.status(this);boolean active=MockLocationService.running||current.startsWith("正在定位");boolean needsCleanup=active||current.contains("已中断");startAction.setText(active?"更新模拟位置":"启动模拟定位");startAction.setEnabled(hasPoint);startAction.setAlpha(hasPoint?1f:0.45f);favoriteAction.setEnabled(hasPoint);favoriteAction.setAlpha(hasPoint?1f:0.45f);stopAction.setVisibility(needsCleanup?View.VISIBLE:View.GONE);}
    private void showSaved(String kind){List<Position> entries=store.list(kind);String[] items=new String[entries.size()];for(int i=0;i<entries.size();i++){Position p=entries.get(i);items[i]=p.name+"\n地点："+(p.locationName.isEmpty()?"未填写":p.locationName)+"\n"+p.coordinates()+"   ·   "+android.text.format.DateFormat.format("MM-dd HH:mm",p.timestamp);}new AlertDialog.Builder(this).setTitle(entries.isEmpty()?"还没有保存的位置":kind.equals("favorites")?"位置收藏":"最近使用").setItems(items,(d,index)->{Position p=entries.get(index);new AlertDialog.Builder(this).setTitle(p.name).setItems(new String[]{"填入定位表单","删除这条记录"},(dd,which)->{if(which==0){name.setText(p.name);latitude.setText(Double.toString(p.latitude));longitude.setText(Double.toString(p.longitude));locationName.setText(p.locationName);}else store.remove(kind,p.key());}).show();}).setNegativeButton("关闭",null).setNeutralButton("清空",(d,w)->new AlertDialog.Builder(this).setMessage("清空整个列表？").setPositiveButton("清空",(dd,ww)->store.clear(kind)).setNegativeButton("取消",null).show()).show();}
    private void guard(Runnable action){try{action.run();}catch(Exception e){new AlertDialog.Builder(this).setTitle("无法执行").setMessage(e.getMessage()).setPositiveButton("知道了",null).show();}}

    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private void sectionTitle(LinearLayout parent,String title,String detail,int top,int bottom){LinearLayout box=column();parent.addView(box,margin(matchWrap(),0,top,0,bottom));label(box,title,18,INK,true,0,0);label(box,detail,11,MUTED,false,0,3);}
    private LinearLayout actionTile(String icon,String title,String detail,boolean elevated){LinearLayout card=column();card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(14),dp(12),dp(12),dp(12));card.setBackground(round(WHITE,18));if(elevated)card.setElevation(dp(1));applyRipple(card);TextView symbol=new TextView(this);symbol.setText(icon);symbol.setTextColor(GREEN);symbol.setTextSize(21);symbol.setGravity(Gravity.CENTER);symbol.setBackground(round(MINT,12));card.addView(symbol,new LinearLayout.LayoutParams(dp(36),dp(34)));label(card,title,14,INK,true,0,dp(7));label(card,detail,10,MUTED,false,0,dp(2));return card;}
    private EditText edit(LinearLayout parent,String hint,String value,boolean numeric){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(14);e.setTextColor(INK);e.setHintTextColor(Color.rgb(165,176,171));e.setHint(hint);e.setPadding(dp(12),0,dp(10),0);e.setBackground(round(PAPER,12));if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);e.setText(value);parent.addView(e,new LinearLayout.LayoutParams(-1,dp(46)));return e;}
    private TextView buttonView(String text,int background,int foreground){TextView v=new TextView(this);v.setText(text);v.setTextColor(foreground);v.setTextSize(14);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(48));v.setPadding(dp(16),dp(8),dp(16),dp(8));v.setBackground(round(background,13));v.setClickable(true);v.setFocusable(true);applyRipple(v);return v;}
    private GradientDrawable roundStroke(int color,int radius,int stroke){GradientDrawable d=round(color,radius);d.setStroke(dp(1),stroke);return d;}
    private void applyRipple(View view){view.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(30,20,91,76)),view.getBackground(),round(Color.WHITE,16)));}
    private void label(LinearLayout parent,String text,int size,int color,boolean bold,int left,int top){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);if(top!=0||left!=0)v.setPadding(left,top,0,0);parent.addView(v,matchWrap());}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable circle(int color){GradientDrawable d=round(color,24);return d;}
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(-2,-2);}
    private LinearLayout.LayoutParams margin(LinearLayout.LayoutParams p,int l,int t,int r,int b){p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    @Override protected void onResume(){super.onResume();refreshStatus();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);if(name!=null){b.putString("name",name.getText().toString());b.putString("locationName",locationName.getText().toString());b.putString("lat",latitude.getText().toString());b.putString("lon",longitude.getText().toString());}}
    @Override protected void onDestroy(){try{unregisterReceiver(changes);}catch(Exception ignored){}if(probe!=null)probe.cancel();worker.shutdownNow();super.onDestroy();}
}
