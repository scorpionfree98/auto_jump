package com.autojump.app.util;
import android.content.*;
import android.content.pm.ShortcutManager;

/** Confirmation is separate from merely requesting a launcher shortcut. */
public final class PinResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        String id=intent.getStringExtra("shortcut_id"); if(id==null || !id.startsWith("instruction:"))return;
        ShortcutManager manager=context.getSystemService(ShortcutManager.class);
        if(manager==null)return;
        for(android.content.pm.ShortcutInfo shortcut:manager.getPinnedShortcuts()) if(id.equals(shortcut.getId())) {
            context.getSharedPreferences("shortcut_publication",Context.MODE_PRIVATE).edit()
                    .putString("pin_status","桌面已确认固定指令入口").apply();return;
        }
    }
}
