package com.autojump.app;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.autojump.app.automation.AutomationOrchestrator;
import com.autojump.app.automation.ExecutionLogStore;
import com.autojump.app.automation.ExecutionSession;
import com.autojump.app.automation.PendingExecutionStore;

/** Lets Android's trusted keyguard authenticate before a queued rule continues. */
public class UnlockGateActivity extends AppCompatActivity {
    public static final String EXTRA_SESSION_ID = "session_id";

    private String sessionId;
    private TextView message;
    private boolean requestInFlight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showAboveLockScreen();
        setContentView(R.layout.activity_unlock_gate);
        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        message = findViewById(R.id.tv_unlock_message);
        findViewById(R.id.btn_request_system_unlock).setOnClickListener(v -> requestSystemUnlock());
        findViewById(R.id.btn_cancel_unlock).setOnClickListener(v -> {
            markWaiting("用户暂未完成系统解锁");
            finish();
        });

        if (sessionId == null || new PendingExecutionStore(this).findById(sessionId) == null) {
            finish();
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
        message.setText("请在系统界面完成解锁。认证信息只由 Android 系统处理。");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() {
                    requestInFlight = false;
                    resumeIfUnlocked();
                }

                @Override public void onDismissCancelled() {
                    requestInFlight = false;
                    markWaiting("系统解锁已取消，可点击按钮重试");
                }

                @Override public void onDismissError() {
                    requestInFlight = false;
                    markWaiting("系统暂时无法发起解锁，可点击按钮重试");
                }
            });
        } else {
            requestInFlight = false;
            message.setText("请先完成系统解锁，解锁成功后规则会继续执行。");
        }
    }

    private boolean resumeIfUnlocked() {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguard != null && keyguard.isKeyguardLocked()) return false;
        new AutomationOrchestrator(this).resumeAfterUnlock(this, sessionId);
        finish();
        return true;
    }

    private void markWaiting(String reason) {
        message.setText(reason);
        ExecutionSession session = new PendingExecutionStore(this).findById(sessionId);
        if (session != null) new ExecutionLogStore(this).append(session, "WAITING", reason);
    }
}
