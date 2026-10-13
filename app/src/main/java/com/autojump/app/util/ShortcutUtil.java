package com.autojump.app.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import com.autojump.app.bean.Rule;
import com.autojump.app.data.RuleRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ShortcutUtil {
    public static String shortcutId(Rule rule) { return "instruction:" + rule.getId(); }
    private static ShortcutInfoCompat info(Context context, Rule rule, int rank) {
        String label = rule.getName().length() > 20 ? rule.getName().substring(0, 20) : rule.getName();
        Intent run = new Intent(context, cn.local.jiojio.MainActivity.class)
                .setAction(com.autojump.app.MainActivity.ACTION_RUN).putExtra("rule_id", rule.getId());
        return new ShortcutInfoCompat.Builder(context, shortcutId(rule)).setShortLabel(label)
                .setLongLabel(rule.getName()).setRank(rank)
                .setIcon(ShortcutIconRenderer.render(context, rule.getShortcutEmoji()))
                .setIntent(run).build();
    }
    public static void refreshDynamicShortcut(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("shortcut_publication", Context.MODE_PRIVATE);
        List<Rule> rules = new RuleRepository(context).getRules();
        rules.sort(Comparator.comparingInt(Rule::getShortcutRank).thenComparing(Rule::getId));
        // Bump when shortcut rendering or static shortcut allocation changes so an
        // app update republishes existing rules even if their stored data is unchanged.
        String signature = "static-v3-emoji-icons:" + new com.google.gson.Gson().toJson(rules);
        if (signature.equals(prefs.getString("signature", ""))) return;
        int capacity = Math.max(0, ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) - 2);
        List<ShortcutInfoCompat> desired = new ArrayList<>();
        Set<String> valid = new HashSet<>();
        for (Rule rule : rules) if (rule.isEnabled() && !rule.getActions().isEmpty()) {
            valid.add(shortcutId(rule));
            if (rule.isShowInShortcutMenu() && desired.size() < capacity) desired.add(info(context, rule, desired.size()));
        }
        try {
            List<String> invalidPinned = new ArrayList<>();
            List<String> enablePinned = new ArrayList<>();
            List<ShortcutInfoCompat> updates = new ArrayList<>();
            android.content.pm.ShortcutManager manager = context.getSystemService(android.content.pm.ShortcutManager.class);
            for (android.content.pm.ShortcutInfo pinned : manager.getPinnedShortcuts()) {
                if (!pinned.getId().startsWith("instruction:")) continue;
                if (!valid.contains(pinned.getId())) invalidPinned.add(pinned.getId());
                else {
                    enablePinned.add(pinned.getId());
                    for (Rule rule : rules) if (shortcutId(rule).equals(pinned.getId())) updates.add(info(context, rule, 0));
                }
            }
            ShortcutManagerCompat.disableShortcuts(context, invalidPinned, "指令已删除或停用");
            manager.enableShortcuts(enablePinned);
            if (!updates.isEmpty()) ShortcutManagerCompat.updateShortcuts(context, updates);
            boolean published = ShortcutManagerCompat.setDynamicShortcuts(context, desired);
            if (published) prefs.edit().putString("signature", signature)
                    .putString("status", "已发布 " + desired.size() + " 个指令入口；桌面显示数量可能更少").apply();
            else prefs.edit().putString("status", "快捷菜单同步受限，请稍后重试").apply();
        } catch (RuntimeException error) {
            prefs.edit().putString("status", "快捷菜单同步失败，请重试：" + error.getClass().getSimpleName()).apply();
        }
    }
    public static void retry(Context context) {
        context.getSharedPreferences("shortcut_publication", Context.MODE_PRIVATE).edit().remove("signature").apply();
        refreshDynamicShortcut(context);
    }
    public static String status(Context context) {
        SharedPreferences prefs=context.getSharedPreferences("shortcut_publication", Context.MODE_PRIVATE);
        String publication=prefs.getString("status", "尚未发布快捷入口");
        String pin=prefs.getString("pin_status", "");return pin.isEmpty()?publication:publication+"\n"+pin;
    }
    public static boolean pin(Context context, Rule rule) {
        SharedPreferences prefs=context.getSharedPreferences("shortcut_publication",Context.MODE_PRIVATE);
        if(!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            prefs.edit().putString("pin_status","当前桌面不支持固定指令入口").apply();return false;
        }
        Intent confirmation=new Intent(context,PinResultReceiver.class).setAction("cn.local.toolkit.PIN_RESULT")
                .setData(android.net.Uri.parse("toolkit://pin/"+rule.getId())).putExtra("shortcut_id",shortcutId(rule));
        android.app.PendingIntent callback=android.app.PendingIntent.getBroadcast(context,0,confirmation,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE);
        try {
            boolean requested=ShortcutManagerCompat.requestPinShortcut(context,info(context,rule,0),callback.getIntentSender());
            prefs.edit().putString("pin_status",requested?"固定入口请求已发送，请在桌面确认；尚未确认成功":"桌面未接受固定入口请求").apply();
            return requested;
        } catch (RuntimeException error) {
            prefs.edit().putString("pin_status","固定入口请求失败，请重试").apply();return false;
        }
    }
    // Legacy config editor is retained only as a compatibility screen.
    public static boolean createDesktopPinShortcut(Context context, com.autojump.app.bean.ConfigBean ignored) { return false; }
}
