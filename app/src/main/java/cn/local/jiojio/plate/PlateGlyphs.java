package cn.local.jiojio.plate;

import android.content.Context;
import android.graphics.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Bundled plate outlines, separately selected for blue and new-energy plates. */
final class PlateGlyphs {
    private static PlateGlyphs instance;
    private final Map<String,Path> blue = new HashMap<>(), green = new HashMap<>();
    static synchronized PlateGlyphs get(Context context) {
        if(instance==null) instance=new PlateGlyphs(context.getApplicationContext());
        return instance;
    }
    private PlateGlyphs(Context context) {
        try (InputStream input=context.getAssets().open("plates/glyphs.json")) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))!=-1) bytes.write(buffer,0,count);
            JSONObject data=new JSONObject(bytes.toString("UTF-8"));
            load(data.getJSONObject("blue"),blue);load(data.getJSONObject("green"),green);
            String required=PlateSpec.PROVINCES+"ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
            for(int i=0;i<required.length();i++) {
                String c=required.substring(i,i+1);
                if(!blue.containsKey(c)||!green.containsKey(c))throw new IllegalStateException("Missing plate glyph: "+c);
            }
        } catch(Exception error) { throw new IllegalStateException("Unable to load bundled plate glyphs",error); }
    }
    private void load(JSONObject data,Map<String,Path> target) throws Exception {
        java.util.Iterator<String> keys=data.keys();
        while(keys.hasNext()) {
            String key=keys.next();JSONArray commands=data.getJSONArray(key);Path path=new Path();path.setFillType(Path.FillType.EVEN_ODD);
            for(int i=0;i<commands.length();i++) {
                JSONArray command=commands.getJSONArray(i);
                switch(command.getInt(0)) {
                    case 0:path.moveTo((float)command.getDouble(1),(float)command.getDouble(2));break;
                    case 1:path.lineTo((float)command.getDouble(1),(float)command.getDouble(2));break;
                    case 2:path.cubicTo((float)command.getDouble(1),(float)command.getDouble(2),
                            (float)command.getDouble(3),(float)command.getDouble(4),
                            (float)command.getDouble(5),(float)command.getDouble(6));break;
                    case 3:path.close();break;
                    default:throw new IllegalStateException("Invalid outline command");
                }
            }
            target.put(key,path);
        }
    }
    Path path(String character,int style){return (style==PlateSpec.GREEN ? green : blue).get(character);}
}
