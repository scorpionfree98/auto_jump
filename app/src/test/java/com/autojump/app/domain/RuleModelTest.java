package com.autojump.app.domain;

import com.autojump.app.bean.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class RuleModelTest {
    @Test
    public void ruleAlwaysInitializesLists() {
        Rule rule = new Rule("id", "name");
        rule.setConditions(null);
        rule.setActions(null);
        assertNotNull(rule.getConditions());
        assertNotNull(rule.getActions());
    }

    @Test
    public void shortcutEmojiDefaultsAndRejectsUnsupportedText() {
        Rule rule = new Rule("id", "name");
        assertEquals(ShortcutEmoji.DEFAULT, rule.getShortcutEmoji());
        rule.setShortcutEmoji("🚀");
        assertEquals("🚀", rule.getShortcutEmoji());
        rule.setShortcutEmoji("not-an-emoji");
        assertEquals(ShortcutEmoji.DEFAULT, rule.getShortcutEmoji());
    }

    @Test
    public void timeConditionDefensivelyCopiesDays() {
        int[] days = {1, 5};
        RuleCondition condition = RuleCondition.time(days, 8, 0, 9, 30);
        days[0] = 7;
        assertArrayEquals(new int[]{1, 5}, condition.getDaysOfWeek());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidTimeIsRejected() {
        RuleCondition.time(null, 25, 0, 9, 0);
    }

    @Test
    public void clickAcceptsSelectorAndCoordinateFallback() {
        RuleAction action = RuleAction.click("com.example", 1000, 5000, 250,
                "com.example:id/submit", null, null, 0.5, 0.75);
        assertTrue(action.hasSelector());
        assertTrue(action.hasCoordinateFallback());
    }

    @Test(expected = IllegalArgumentException.class)
    public void clickRejectsOutOfRangeCoordinates() {
        RuleAction.click("com.example", 0, 5000, 250,
                null, null, null, 1.1, 0.5);
    }
}
