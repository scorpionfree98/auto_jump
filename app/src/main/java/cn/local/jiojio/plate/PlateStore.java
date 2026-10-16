package cn.local.jiojio.plate;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;

/** Separate from instruction/location preferences; no network or export path. */
public final class PlateStore {
    private final SharedPreferences prefs;
    public PlateStore(Context context) { prefs=context.getSharedPreferences("toolkit_plate_library",Context.MODE_PRIVATE); }
    public synchronized List<PlateHistory.Entry> list() { return read().list(); }
    public synchronized void use(String number,int style) {
        PlateHistory data=read();data.use(number,style,System.currentTimeMillis());write(data);
    }
    public synchronized void pin(String key,boolean pinned) { PlateHistory data=read();data.pin(key,pinned);write(data); }
    public synchronized void remove(String key) { PlateHistory data=read();data.remove(key);write(data); }
    private PlateHistory read() { return PlateHistory.decode(prefs.getString("history",null)); }
    private void write(PlateHistory data) {
        if(!prefs.edit().putString("history",data.encode()).commit())
            throw new IllegalStateException("保存失败，请检查设备剩余空间");
    }
}
