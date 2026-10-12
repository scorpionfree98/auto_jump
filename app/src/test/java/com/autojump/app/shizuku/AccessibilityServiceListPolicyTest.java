package com.autojump.app.shizuku;

import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccessibilityServiceListPolicyTest {
    @Test public void preservesOtherServicesAndOrder() {
        Set<String> services = AccessibilityServiceListPolicy.parse(
                "one.pkg/.Service:two.pkg/two.Service");
        services.add("cn.local.toolkit/com.autojump.Service");

        assertEquals("one.pkg/.Service:two.pkg/two.Service:"
                + "cn.local.toolkit/com.autojump.Service",
                AccessibilityServiceListPolicy.join(services));
    }

    @Test public void ignoresNullAndEmptySettingsValues() {
        assertTrue(AccessibilityServiceListPolicy.parse("null").isEmpty());
        assertTrue(AccessibilityServiceListPolicy.parse("  ").isEmpty());
        assertFalse(AccessibilityServiceListPolicy.contains(
                "other/.Service", "target/.Service"));
    }
}
