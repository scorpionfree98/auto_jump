package com.autojump.app.data;

import com.autojump.app.bean.Rule;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.ConditionType;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.domain.RuleCondition;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class RuleJsonCodecTest {
    private final RuleJsonCodec codec = new RuleJsonCodec();

    @Test
    public void schemaV2RoundTripsTypedConditionsAndActions() {
        Rule rule = new Rule("rule-1", "工作日打卡");
        rule.setConditions(Arrays.asList(
                RuleCondition.time(new int[]{1, 2, 3, 4, 5}, 8, 0, 9, 0),
                RuleCondition.wifi("Office")));
        rule.setActions(Arrays.asList(
                RuleAction.deeplink("demo://open"),
                RuleAction.click("com.example", 1200, 5000, 250,
                        "com.example:id/check_in", "打卡", null, 0.5, 0.75)));

        String json = codec.encode(Arrays.asList(rule));
        List<Rule> decoded = codec.decode(json);

        assertTrue(json.contains("\"schemaVersion\": 2"));
        assertEquals(ConditionType.WIFI, decoded.get(0).getConditions().get(1).getType());
        assertEquals(ActionType.CLICK, decoded.get(0).getActions().get(1).getType());
        assertEquals("com.example:id/check_in", decoded.get(0).getActions().get(1).getViewId());
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
    }

    @Test(expected = RuntimeException.class)
    public void rejectsFutureSchema() {
        codec.decode("{\"schemaVersion\":999,\"rules\":[]}");
    }
}
