package cn.local.jiojio;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class PositionStore {
    private final SharedPreferences prefs;
    public PositionStore(Context context) { prefs = context.getSharedPreferences("positions", Context.MODE_PRIVATE); }
    public List<Position> list(String kind) {
        List<Position> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString(kind, "[]"));
            for (int i=0; i<a.length(); i++) {
                JSONObject o=a.getJSONObject(i);
                out.add(new Position(o.getString("name"),o.getDouble("lat"),o.getDouble("lon"),o.getLong("time")));
            }
        } catch (Exception e) { throw new IllegalStateException("位置记录损坏，请先导出诊断或清空对应列表", e); }
        return out;
    }
    public void add(String kind, Position p) {
        List<Position> entries = list(kind);
        entries.removeIf(x -> x.key().equals(p.key()));
        entries.add(0,p);
        if (entries.size()>200) entries=entries.subList(0,200);
        write(kind, entries);
    }
    public void remove(String kind, String key) {
        List<Position> entries=list(kind); entries.removeIf(x->x.key().equals(key));write(kind,entries);
    }
    public void clear(String kind) { write(kind,new ArrayList<>()); }
    private void write(String kind,List<Position> entries) {
        JSONArray a=new JSONArray();
        try {
            for (Position p:entries) a.put(new JSONObject().put("name",p.name).put("lat",p.latitude).put("lon",p.longitude).put("time",p.timestamp));
        } catch (Exception e) { throw new IllegalStateException("无法保存位置",e); }
        prefs.edit().putString(kind,a.toString()).apply();
    }
}
