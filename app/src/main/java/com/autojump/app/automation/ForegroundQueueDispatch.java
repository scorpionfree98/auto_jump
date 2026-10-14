package com.autojump.app.automation;

import java.util.function.BooleanSupplier;

/** Keeps queue persistence and foreground dispatch in one synchronous operation. */
final class ForegroundQueueDispatch {
    private ForegroundQueueDispatch() {}

    static boolean run(BooleanSupplier persist, Runnable dispatch) {
        if (!persist.getAsBoolean()) return false;
        dispatch.run();
        return true;
    }
}
