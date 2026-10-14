package com.autojump.app;

import androidx.appcompat.app.AppCompatActivity;
import com.autojump.app.bean.Rule;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** User-facing explanation for a manual run that was blocked by its conditions. */
public final class RunConditionDialog {
    private RunConditionDialog() {}

    public static boolean handles(String message) {
        return message != null && (message.startsWith("条件不满足：")
                || message.startsWith("条件无法检查："));
    }

    public static void show(AppCompatActivity activity, Rule rule, String message,
                            Runnable afterDismiss) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        String name = rule == null || rule.getName() == null ? "该指令" : "「" + rule.getName() + "」";
        new MaterialAlertDialogBuilder(activity)
                .setTitle(name + "暂未执行")
                .setMessage(message + "\n\n请调整运行条件，或在条件满足后重试。")
                .setPositiveButton("知道了", null)
                .setOnDismissListener(dialog -> {
                    if (afterDismiss != null) afterDismiss.run();
                })
                .show();
    }
}
