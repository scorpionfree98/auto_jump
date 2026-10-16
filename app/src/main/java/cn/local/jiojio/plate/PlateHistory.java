package cn.local.jiojio.plate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure local history model; the plate type and normalized number form its identity. */
public final class PlateHistory {
    public static final class Entry {
        public final String number;
        public final int style;
        public final boolean pinned;
        public final long lastUsed;
        private Entry(String number,int style,boolean pinned,long lastUsed) {
            if ((style!=PlateSpec.BLUE && style!=PlateSpec.GREEN) || PlateSpec.error(number,style)!=null || lastUsed<0)
                throw new IllegalArgumentException("无效的车牌记录");
            this.number=PlateSpec.normalize(number);this.style=style;this.pinned=pinned;this.lastUsed=lastUsed;
        }
        public String key() { return style+":"+number; }
    }
    private final List<Entry> entries=new ArrayList<>();
    public List<Entry> list() {
        List<Entry> sorted=new ArrayList<>(entries);
        sorted.sort(Comparator.comparing((Entry e)->!e.pinned)
            .thenComparing(Comparator.comparingLong((Entry e)->e.lastUsed).reversed()).thenComparing(Entry::key));
        return sorted;
    }
    public void use(String number,int style,long now) {
        Entry next=new Entry(number,style,false,now);
        Entry old=find(next.key());
        long latest=entries.stream().mapToLong(e->e.lastUsed).max().orElse(-1);
        if(latest==Long.MAX_VALUE) throw new IllegalStateException("记录时间异常，暂时不能保存");
        next=new Entry(next.number,style,old!=null && old.pinned,Math.max(now,latest+1));
        remove(next.key());entries.add(next);
    }
    public void pin(String key,boolean pinned) {
        Entry old=find(key);
        if(old==null)throw new IllegalArgumentException("车牌记录已不存在");
        remove(key);entries.add(new Entry(old.number,old.style,pinned,old.lastUsed));
    }
    public void remove(String key) { entries.removeIf(e->e.key().equals(key)); }
    private Entry find(String key) { for(Entry e:entries)if(e.key().equals(key))return e;return null; }
    public String encode() {
        JsonArray data=new JsonArray();
        for(Entry e:entries) {
            JsonObject row=new JsonObject();row.addProperty("number",e.number);row.addProperty("style",e.style);
            row.addProperty("pinned",e.pinned);row.addProperty("lastUsed",e.lastUsed);data.add(row);
        }
        JsonObject root=new JsonObject();root.addProperty("schemaVersion",1);root.add("entries",data);return root.toString();
    }
    public static PlateHistory decode(String json) {
        PlateHistory result=new PlateHistory();
        if(json==null)return result;
        try {
            JsonObject root=JsonParser.parseString(json).getAsJsonObject();
            if(!root.getAsJsonPrimitive("schemaVersion").isNumber()
                    || root.getAsJsonPrimitive("schemaVersion").getAsBigDecimal().intValueExact()!=1)
                throw new IllegalArgumentException("不支持的车牌记录版本");
            for(JsonElement item:root.getAsJsonArray("entries")) {
                JsonObject row=item.getAsJsonObject();
                if(!row.getAsJsonPrimitive("number").isString() || !row.getAsJsonPrimitive("style").isNumber()
                        || !row.getAsJsonPrimitive("pinned").isBoolean() || !row.getAsJsonPrimitive("lastUsed").isNumber())
                    throw new IllegalArgumentException("车牌记录字段类型无效");
                Entry entry=new Entry(row.get("number").getAsString(),row.getAsJsonPrimitive("style").getAsBigDecimal().intValueExact(),
                    row.get("pinned").getAsBoolean(),row.getAsJsonPrimitive("lastUsed").getAsBigDecimal().longValueExact());
                if(result.find(entry.key())!=null)throw new IllegalArgumentException("重复的车牌记录");
                result.entries.add(entry);
            }
            return result;
        } catch(RuntimeException error) {
            // Fail closed: never overwrite unreadable records with an empty list.
            throw new IllegalArgumentException("无法读取车牌记录，原数据已保留",error);
        }
    }
}
