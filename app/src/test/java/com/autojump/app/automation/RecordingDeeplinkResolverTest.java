package com.autojump.app.automation;

import com.autojump.app.domain.RuleAction;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RecordingDeeplinkResolverTest {
    private final RecordingDeeplinkResolver resolver = new RecordingDeeplinkResolver();

    @Test public void editingClickUsesNearestPrecedingDeeplink() {
        assertEquals("second://page", resolver.resolve(Arrays.asList(
                RuleAction.deeplink("first://page"),
                RuleAction.deeplink("second://page"),
                RuleAction.waitFor(100),
                RuleAction.click("com.target", 100, 1000, 100,
                        null, "确认", null, null, null)), 3, "fallback://page"));
    }

    @Test public void laterDeeplinkIsNotUsedWhenEditingEarlierClick() {
        assertEquals("first://page", resolver.resolve(Arrays.asList(
                RuleAction.deeplink("first://page"),
                RuleAction.click("com.target", 100, 1000, 100,
                        null, "确认", null, null, null),
                RuleAction.deeplink("later://page")), 1, "fallback://page"));
    }

    @Test public void newClickUsesLastAvailableDeeplink() {
        assertEquals("last://page", resolver.resolve(Arrays.asList(
                RuleAction.deeplink("first://page"),
                RuleAction.waitFor(100),
                RuleAction.deeplink("last://page")), -1, "fallback://page"));
    }

    @Test public void missingDeeplinkUsesDefaultFallback() {
        assertEquals("fallback://page", resolver.resolve(
                Collections.singletonList(RuleAction.waitFor(100)), -1, "fallback://page"));
    }
}
