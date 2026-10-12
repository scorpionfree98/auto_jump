package com.autojump.app.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Supported emoji glyphs used to distinguish launcher shortcuts. */
public final class ShortcutEmoji {
    public static final String DEFAULT = "⚡";
    private static final List<String> OPTIONS = Collections.unmodifiableList(Arrays.asList(
            DEFAULT, "✅", "⏰", "📍", "🚗", "🏢",
            "🏠", "📱", "🔔", "🚀", "💼", "🎯"));

    private ShortcutEmoji() {}

    public static List<String> options() { return OPTIONS; }

    public static String normalize(String value) {
        if (value == null) return DEFAULT;
        String candidate = value.trim();
        return OPTIONS.contains(candidate) ? candidate : DEFAULT;
    }
}
