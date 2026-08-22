package com.autojump.app.automation;

import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleAction;
import java.util.List;

/** Selects the nearest deeplink that precedes the click being recorded. */
public class RecordingDeeplinkResolver {
    public String resolve(List<RuleAction> actions, int clickIndex, String fallback) {
        if (actions == null || actions.isEmpty()) return fallback;
        int start = clickIndex >= 0 ? clickIndex - 1 : actions.size() - 1;
        start = Math.min(start, actions.size() - 1);
        for (int i = start; i >= 0; i--) {
            RuleAction action = actions.get(i);
            if (action != null && action.getType() == ActionType.DEEPLINK
                    && action.getDeeplink() != null
                    && !action.getDeeplink().trim().isEmpty()) {
                return action.getDeeplink();
            }
        }
        return fallback;
    }
}
