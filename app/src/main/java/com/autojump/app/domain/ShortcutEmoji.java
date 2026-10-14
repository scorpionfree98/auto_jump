package com.autojump.app.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Presets plus bounded custom emoji sequences (no plain-text icon labels). */
public final class ShortcutEmoji {
    public static final String DEFAULT = "⚡";
    private static final List<String> OPTIONS = Collections.unmodifiableList(Arrays.asList(
            DEFAULT, "🍚", "✅", "⏰", "📍", "🚗", "🏢",
            "🏠", "📱", "🔔", "🚀", "💼", "🎯"));

    private ShortcutEmoji() {}

    public static List<String> options() { return OPTIONS; }

    public static String normalize(String value) {
        if (value == null) return DEFAULT;
        String candidate = value.trim();
        return isValid(candidate) ? candidate : DEFAULT;
    }

    public static boolean isValid(String value) {
        if (value == null) return false;
        int[] points = value.trim().codePoints().toArray();
        if (points.length == 0 || points.length > 32) return false;
        if (points[0] >= 0x1F1E6 && points[0] <= 0x1F1FF) {
            return points.length == 2 && points[1] >= 0x1F1E6 && points[1] <= 0x1F1FF;
        }
        if (points[0] == '#' || points[0] == '*' || points[0] >= '0' && points[0] <= '9') {
            return points.length == 2 && points[1] == 0x20E3
                    || points.length == 3 && points[1] == 0xFE0F && points[2] == 0x20E3;
        }
        int i = 0;
        while (i < points.length) {
            if (!isBase(points[i++])) return false;
            if (i < points.length && (points[i] == 0xFE0F || points[i] == 0xFE0E)) i++;
            if (i < points.length && points[i] >= 0x1F3FB && points[i] <= 0x1F3FF) i++;
            // Subdivision flags use tag letters followed by CANCEL TAG.
            if (i < points.length && points[i] >= 0xE0020 && points[i] <= 0xE007E) {
                if (points[0] != 0x1F3F4) return false;
                while (i < points.length && points[i] >= 0xE0020 && points[i] <= 0xE007E) i++;
                return i == points.length - 1 && points[i] == 0xE007F;
            }
            if (i == points.length) return true;
            if (points[i++] != 0x200D || i == points.length) return false;
        }
        return false;
    }

    private static boolean isBase(int point) {
        if (point >= 0x1F1E6 && point <= 0x1F1FF || point >= 0x1F3FB && point <= 0x1F3FF) return false;
        return point >= 0x1F000 && point <= 0x1FAFF
                || point >= 0x2600 && point <= 0x27BF
                || point >= 0x2194 && point <= 0x2199
                || point >= 0x23E9 && point <= 0x23F3
                || point >= 0x23F8 && point <= 0x23FA
                || point == 0x00A9 || point == 0x00AE || point == 0x203C || point == 0x2049
                || point == 0x2122 || point == 0x2139 || point == 0x21A9 || point == 0x21AA
                || point == 0x231A || point == 0x231B || point == 0x2328 || point == 0x23CF
                || point == 0x24C2 || point == 0x25AA || point == 0x25AB || point == 0x25B6
                || point == 0x25C0 || point == 0x25FB || point == 0x25FC || point == 0x25FD
                || point == 0x25FE || point == 0x2934 || point == 0x2935 || point == 0x2B05
                || point == 0x2B06 || point == 0x2B07 || point == 0x2B1B || point == 0x2B1C
                || point == 0x2B50 || point == 0x2B55 || point == 0x3030 || point == 0x303D
                || point == 0x3297 || point == 0x3299;
    }
}
