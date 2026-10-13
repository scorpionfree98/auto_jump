package com.autojump.app.shizuku;

/** Serializes connection and terminal events from one binder recovery attempt. */
public final class RecoveryAttempt {
    private boolean connectionHandled;
    private boolean finished;
    public synchronized boolean claimConnection() {
        if (finished || connectionHandled) return false;
        connectionHandled = true;
        return true;
    }
    public synchronized boolean finish() {
        if (finished) return false;
        finished = true;
        return true;
    }
}
