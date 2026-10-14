package com.autojump.app.engine;

import com.autojump.app.domain.RuleCondition;
import java.util.Calendar;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class ConditionFailureTextTest {
    @Test public void explainsCurrentTimeAndEveryAllowedWindow() {
        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.OCTOBER, 14, 10, 58, 0);

        String message = ConditionFailureText.timeWindows(List.of(
                RuleCondition.time(null, 11, 20, 12, 50),
                RuleCondition.time(null, 17, 20, 18, 50)), now);

        assertTrue(message.contains("当前时间：10:58（周三）"));
        assertTrue(message.contains("允许时段：11:20-12:50 或 17:20-18:50"));
        assertTrue(message.contains("因此本次未执行"));
    }
}
