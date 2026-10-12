package com.autojump.app.ui;

import com.autojump.app.domain.RuleAction;
import java.util.Locale;

/** Human-facing summaries, independent of selector and trace implementation details. */
public final class ActionLabels {
    private ActionLabels() {}
    public static String describe(RuleAction action) {
        switch (action.getType()) {
            case DEEPLINK: return "打开目标页面";
            case WAIT: return "等待 " + seconds(action.getWaitDurationMs()) + " 秒";
            case RECORDED_TOUCH: return "等待 " + seconds(action.getDelayBeforeMs())
                    + " 秒，回放录制点击" + (action.getRepeatCount() > 1
                    ? " " + action.getRepeatCount() + " 次（间隔 " + seconds(action.getRepeatIntervalMs()) + " 秒）" : "");
            case CLICK: return "等待 " + seconds(action.getDelayBeforeMs()) + " 秒，点击已保存的位置";
            default: return "操作";
        }
    }
    private static String seconds(long millis) {
        return String.format(Locale.getDefault(), "%.2f", millis / 1000.0);
    }
}
