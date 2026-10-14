package com.autojump.app.domain;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShortcutEmojiTest {
    @Test public void customEmojiAreNotLimitedToPresets() {
        for (String value : new String[]{"🍚", "🥳", "🫠", "❤️", "☕", "👍🏽", "👩🏽‍💻", "👨‍👩‍👧‍👦", "🏳️‍🌈", "🇨🇳", "1️⃣", "#️⃣"}) {
            assertTrue(value, ShortcutEmoji.isValid(value));
            assertEquals(value, ShortcutEmoji.normalize(value));
        }
    }
    @Test public void rejectsTextAndMalformedOrMultipleEmoji() {
        for (String value : new String[]{"", "abc", "干饭", "🍚🍚", "🍚文字", "1", "🇨", "🏽", "\u200D🍚", "🍚\u200D", "\uFE0F", "🍚\n🍚"}) {
            assertFalse(value, ShortcutEmoji.isValid(value));
            assertEquals(ShortcutEmoji.DEFAULT, ShortcutEmoji.normalize(value));
        }
        assertFalse(ShortcutEmoji.isValid(null));
    }
    @Test public void trimsAndRetainsEveryPreset() {
        assertEquals("🍚", ShortcutEmoji.normalize(" 🍚 "));
        for (String preset : ShortcutEmoji.options()) assertEquals(preset, ShortcutEmoji.normalize(preset));
    }
}
