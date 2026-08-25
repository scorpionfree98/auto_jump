package com.autojump.app.data;

import com.autojump.app.bean.Rule;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.ConditionType;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.domain.RuleCondition;
import com.autojump.app.domain.TouchPoint;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class RuleJsonCodecTest {
    private final RuleJsonCodec codec = new RuleJsonCodec();

    @Test
    public void schemaV4RoundTripsTypedConditionsActionsAndUnlockPreference() {
        Rule rule = new Rule("rule-1", "工作日打卡");
        rule.setRequestUnlockBeforeRun(true);
        rule.setConditions(Arrays.asList(
                RuleCondition.time(new int[]{1, 2, 3, 4, 5}, 8, 0, 9, 0),
                RuleCondition.wifi("Office")));
        rule.setActions(Arrays.asList(
                RuleAction.deeplink("demo://open"),
                RuleAction.click("com.example", 1200, 5000, 250,
                        "com.example:id/check_in", "打卡", null, 0.5, 0.75)));

        String json = codec.encode(Arrays.asList(rule));
        List<Rule> decoded = codec.decode(json);

        assertTrue(json.contains("\"schemaVersion\": 4"));
        assertTrue(decoded.get(0).isRequestUnlockBeforeRun());
        assertEquals(ConditionType.WIFI, decoded.get(0).getConditions().get(1).getType());
        assertEquals(ActionType.CLICK, decoded.get(0).getActions().get(1).getType());
        assertEquals("com.example:id/check_in", decoded.get(0).getActions().get(1).getViewId());
    }

    @Test
    public void schemaV4RoundTripsRecordedTouchTrace() {
        Rule rule = new Rule("touch", "真实触摸");
        RuleAction repeated = RuleAction.recordedTouch("com.example", 800, 6000,
                1440, 3200, 1, "7.0.1", Arrays.asList(
                        new TouchPoint(0.5, 0.75, 0),
                        new TouchPoint(0.502, 0.751, 73),
                        new TouchPoint(0.5, 0.75, 161)));
        repeated.setRepeatCount(2);
        repeated.setRepeatIntervalMs(800);
        rule.setActions(Arrays.asList(repeated));

        List<Rule> decoded = codec.decode(codec.encode(Arrays.asList(rule)));
        RuleAction action = decoded.get(0).getActions().get(0);

        assertEquals(ActionType.RECORDED_TOUCH, action.getType());
        assertEquals(3, action.getTouchTrace().size());
        assertEquals(161L, action.getTouchDurationMs());
        assertEquals(3200, action.getRecordedDisplayHeight());
        assertEquals("7.0.1", action.getTargetAppVersion());
        assertEquals(2, action.getRepeatCount());
        assertEquals(800L, action.getRepeatIntervalMs());
    }

    @Test
    public void stillReadsSchemaV3CoordinateClick() {
        String v3 = "{\"schemaVersion\":3,\"rules\":[{\"id\":\"old\"," +
                "\"name\":\"旧点击\",\"actions\":[{\"type\":\"CLICK\"," +
                "\"targetPackage\":\"com.example\",\"relativeX\":0.5," +
                "\"relativeY\":0.7,\"timeoutMs\":5000,\"retryIntervalMs\":250}]}]}";

        RuleAction action = codec.decode(v3).get(0).getActions().get(0);

        assertEquals(ActionType.CLICK, action.getType());
        assertEquals(0.5, action.getRelativeX(), 0.0001);
    }

    @Test
    public void migratesLegacyArrayAndInfersConditionType() {
        String legacy = "[{\"id\":\"old\",\"name\":\"旧规则\",\"priority\":3," +
                "\"enabled\":true,\"conditions\":[{\"ssid\":\"Office\"}]," +
                "\"actions\":[\"demo://open\"]}]";

        List<Rule> decoded = codec.decode(legacy);

        assertEquals(ConditionType.WIFI, decoded.get(0).getConditions().get(0).getType());
        assertEquals(ActionType.DEEPLINK, decoded.get(0).getActions().get(0).getType());
        assertEquals("demo://open", decoded.get(0).getActions().get(0).getDeeplink());
        assertFalse(decoded.get(0).isRequestUnlockBeforeRun());
    }

    @Test(expected = RuntimeException.class)
    public void rejectsFutureSchema() {
        codec.decode("{\"schemaVersion\":999,\"rules\":[]}");
    }
}
