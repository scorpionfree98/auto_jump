package com.autojump.app;
import cn.local.jiojio.R;
import cn.local.jiojio.BuildConfig;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import com.autojump.app.automation.AutomationOrchestrator;
import com.autojump.app.automation.ExecutionLogStore;
import com.autojump.app.automation.ExecutionSession;
import com.autojump.app.automation.PendingExecutionStore;

/** Lets Android's trusted keyguard authenticate before a queued rule continues. */
public class UnlockGateActivity extends AppCompatActivity {
    public static final String EXTRA_SESSION_ID = "session_id";

    private String sessionId;
    private boolean requestInFlight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showAboveLockScreen();
        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);

        if (sessionId == null || new PendingExecutionStore(this).findById(sessionId) == null) {
            finishSilently();
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (resumeIfUnlocked()) return;
        if (!requestInFlight) requestSystemUnlock();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String nextSessionId = intent.getStringExtra(EXTRA_SESSION_ID);
        if (nextSessionId != null) sessionId = nextSessionId;
        requestInFlight = false;
        if (!resumeIfUnlocked()) requestSystemUnlock();
    }

    private void showAboveLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
    }

    private void requestSystemUnlock() {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguard == null || !keyguard.isKeyguardLocked()) {
            resumeIfUnlocked();
            return;
        }
        requestInFlight = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() {
                    requestInFlight = false;
                    resumeIfUnlocked();
                }

                @Override public void onDismissCancelled() {
                    requestInFlight = false;
                    markWaiting("系统解锁已取消，任务将在解锁后自动继续");
                    finishSilently();
                }

                @Override public void onDismissError() {
                    requestInFlight = false;
                    markWaiting("系统暂时无法发起解锁，任务将在解锁后自动继续");
                    finishSilently();
                }
            });
        } else {
            requestInFlight = false;
            markWaiting("请完成系统解锁，任务将在解锁后自动继续");
            finishSilently();
        }
    }

    private boolean resumeIfUnlocked() {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguard != null && keyguard.isKeyguardLocked()) return false;
        new AutomationOrchestrator(this).resumeAfterUnlock(this, sessionId);
        finishSilently();
        return true;
    }

    private void markWaiting(String reason) {
        ExecutionSession session = new PendingExecutionStore(this).findById(sessionId);
        if (session != null) new ExecutionLogStore(this).append(session, "WAITING", reason);
    }

    private void finishSilently() {
        finish();
    }
}
