package com.autojump.app.util;

import android.content.Context;
import android.content.Intent;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import com.autojump.app.MainActivity;
import com.autojump.app.R;
import com.autojump.app.bean.ConfigBean;
import java.util.ArrayList;
import java.util.List;

public class ShortcutUtil {

    // 刷新长按APP图标动态快捷列表
    public static void refreshDynamicShortcut(Context context) {
        List<ConfigBean> configList = SPUtil.getConfigList(context);
        List<ShortcutInfoCompat> shortcutList = new ArrayList<>();

        // 系统对动态快捷数量有上限，超过部分忽略
        int max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context);
        if (max <= 0) max = 4;

        // shortcuts.xml 已提供永久“设置”入口；为它预留一个系统名额。
        // setDynamicShortcuts 会覆盖旧动态列表，因此也会清掉历史重复设置项。
        int dynamicMax = Math.max(0, max - 1);

        for (ConfigBean bean : configList) {
            if (shortcutList.size() >= dynamicMax) break;

            Intent jumpIntent = new Intent(context, MainActivity.class);
            jumpIntent.setAction(Intent.ACTION_VIEW);
            jumpIntent.putExtra("target_deep_link", bean.getDeepLink());
            jumpIntent.putExtra("config_id", bean.getId());

            ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(context, bean.getId())
                    .setShortLabel(bean.getName())
                    .setLongLabel("一键跳转：" + bean.getName())
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(jumpIntent)
                    .build();
            shortcutList.add(shortcut);
        }
        // 覆盖更新全部长按快捷
        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcutList);
        } catch (Exception ignored) {
        }
    }

    // 创建桌面独立快捷图标（钉到桌面）
    public static boolean createDesktopPinShortcut(Context context, ConfigBean bean) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return false;
        }
        Intent jumpIntent = new Intent(context, MainActivity.class);
        jumpIntent.setAction(Intent.ACTION_VIEW);
        jumpIntent.putExtra("target_deep_link", bean.getDeepLink());
        jumpIntent.putExtra("config_id", bean.getId());

        ShortcutInfoCompat pinShortcut = new ShortcutInfoCompat.Builder(context, "pin_" + bean.getId())
                .setShortLabel(bean.getName())
                .setLongLabel("一键跳转：" + bean.getName())
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(jumpIntent)
                .build();
        // 请求添加桌面快捷，系统弹出确认框
        return ShortcutManagerCompat.requestPinShortcut(context, pinShortcut, null);
    }
}
