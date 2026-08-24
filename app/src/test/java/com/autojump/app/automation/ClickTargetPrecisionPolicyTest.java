package com.autojump.app.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClickTargetPrecisionPolicyTest {
    private final ClickTargetPrecisionPolicy policy = new ClickTargetPrecisionPolicy();

    @Test
    public void nativeDirectClickableNodeRemainsPrecise() {
        assertTrue(policy.isPrecise(true, 1d, false));
    }

    @Test
    public void smallNativeClickableAncestorRemainsPrecise() {
        assertTrue(policy.isPrecise(false, 0.20d, false));
    }

    @Test
    public void oversizedNativeClickableAncestorIsImprecise() {
        assertFalse(policy.isPrecise(false, 0.50d, false));
    }

    @Test
    public void WebViewMatchIsImpreciseEvenWhenReportedClickable() {
        assertFalse(policy.isPrecise(true, 0.05d, true));
    }

    @Test
    public void gestureDurationMatchesSuccessfulDeviceTouch() {
        assertEquals(160L, policy.gestureDurationMs());
    }
}
