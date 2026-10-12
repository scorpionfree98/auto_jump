package com.autojump.app.data;

import com.autojump.app.bean.Rule;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.domain.RuleCondition;
import com.google.gson.*;
import java.util.List;

/** Import must never silently execute a partially decoded instruction. */
public final class StrictRuleImport {
    public static List<Rule> decode(String json) {
        if (json == null || json.length() > 4_000_000) throw new IllegalArgumentException("备份过大或为空");
        JsonElement root = JsonParser.parseString(json);
        JsonArray raw = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("rules");
        if (raw == null || raw.size() > 200) throw new IllegalArgumentException("备份最多支持 200 条指令");
        List<Rule> rules = new RuleJsonCodec().decode(json);
        if (rules.size() != raw.size()) throw new IllegalArgumentException("备份包含无效指令");
        for (int i = 0; i < rules.size(); i++) {
            JsonObject source = raw.get(i).getAsJsonObject();
            Rule rule = rules.get(i);
            if (count(source, "actions") != rule.getActions().size() || count(source, "conditions") != rule.getConditions().size()) {
                throw new IllegalArgumentException("指令“" + rule.getName() + "”含未知或无效动作/条件，已拒绝导入");
            }
            for (RuleAction a : rule.getActions()) {
                if (a == null || a.getType() == null) throw new IllegalArgumentException("未知动作");
                if (a.getDelayBeforeMs() < 0 || a.getDelayBeforeMs() > 86_400_000L
                        || a.getTimeoutMs() <= 0 || a.getTimeoutMs() > 600_000L) {
                    throw new IllegalArgumentException("动作延迟或超时超出支持范围");
                }
                switch (a.getType()) {
                    case DEEPLINK:
                        RuleAction.deeplink(a.getDeeplink());
                        if (!a.getDeeplink().matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")) throw new IllegalArgumentException("跳转链接缺少 scheme");
                        break;
                    case WAIT:
                        RuleAction.waitFor(a.getWaitDurationMs());
                        if (a.getWaitDurationMs() > 86_400_000L) throw new IllegalArgumentException("单次等待最多 24 小时");
                        break;
                    case CLICK:
                        RuleAction.click(a.getTargetPackage(), a.getDelayBeforeMs(), a.getTimeoutMs(), a.getRetryIntervalMs(),
                                a.getViewId(), a.getText(), a.getContentDescription(), a.getRelativeX(), a.getRelativeY()); break;
                    case RECORDED_TOUCH:
                        RuleAction validated = RuleAction.recordedTouch(a.getTargetPackage(), a.getDelayBeforeMs(), a.getTimeoutMs(),
                                a.getRecordedDisplayWidth(), a.getRecordedDisplayHeight(), a.getRecordedOrientation(), a.getTargetAppVersion(), a.getTouchTrace());
                        validated.setRepeatCount(a.getRepeatCount()); validated.setRepeatIntervalMs(a.getRepeatIntervalMs());
                        validated.setScreenTolerancePercent(a.getScreenTolerancePercent());
                        validated.setReplayOffsets(a.getReplayOffsetXPx(),a.getReplayOffsetYPx());
                        validated.setPageSettleMs(a.getPageSettleMs()); break;
                }
            }
            for (RuleCondition c : rule.getConditions()) {
                if (c == null || c.getType() == null) throw new IllegalArgumentException("未知条件");
                switch (c.getType()) {
                    case TIME: RuleCondition.time(c.getDaysOfWeek(), c.getStartHour(), c.getStartMinute(), c.getEndHour(), c.getEndMinute()); break;
                    case WIFI: RuleCondition.wifi(c.getSsid()); break;
                    case BLUETOOTH: RuleCondition.bluetooth(c.getDeviceName()); break;
                    case CHARGING: break;
                    case LOCATION: RuleCondition.location(c.getLatitude(), c.getLongitude(), c.getRadiusMeters(), c.getPlaceName()); break;
                }
            }
        }
        return rules;
    }
    private static int count(JsonObject o, String key) {
        return !o.has(key) || o.get(key).isJsonNull() ? 0 : o.getAsJsonArray(key).size();
    }
}
