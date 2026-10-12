package com.autojump.app.shizuku;

import java.util.LinkedHashSet;
import java.util.Set;

/** Preserves other enabled accessibility services while adding this app's component. */
public final class AccessibilityServiceListPolicy {
    private AccessibilityServiceListPolicy() {}

    public static Set<String> parse(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.trim().isEmpty() || "null".equals(value.trim())) {
            return result;
        }
        for (String item : value.split(":")) {
            String trimmed = item == null ? "" : item.trim();
            if (!trimmed.isEmpty() && !"null".equals(trimmed)) result.add(trimmed);
        }
        return result;
    }

    public static boolean contains(String value, String component) {
        return component != null && parse(value).contains(component);
    }

    public static String join(Set<String> values) {
        StringBuilder result = new StringBuilder();
        if (values == null) return "";
        for (String value : values) {
            if (value == null || value.trim().isEmpty() || "null".equals(value.trim())) continue;
            if (result.length() > 0) result.append(':');
            result.append(value.trim());
        }
        return result.toString();
    }
}
