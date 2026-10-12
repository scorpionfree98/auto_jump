package com.autojump.app.data;
import android.content.Context;
import android.content.SharedPreferences;
public final class InstructionSettings {
    private final SharedPreferences prefs;
    public InstructionSettings(Context c) { prefs = c.getSharedPreferences("instruction_settings", Context.MODE_PRIVATE); }
    public String defaultId() { return prefs.getString("default_id", ""); }
    public void setDefaultId(String id) { prefs.edit().putString("default_id", id == null ? "" : id).apply(); }
}
