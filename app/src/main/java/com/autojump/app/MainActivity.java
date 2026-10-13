package com.autojump.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.autojump.app.bean.Rule;
import com.autojump.app.data.InstructionSettings;
import com.autojump.app.data.RuleRepository;
import com.autojump.app.automation.AccessibilityAwareRuleRunner;
import com.autojump.app.util.ShortcutUtil;

/** Transparent launcher: ID lookup never falls back to an unrelated instruction. */
public class MainActivity extends AppCompatActivity {
    public static final String ACTION_RUN = "cn.local.toolkit.RUN_INSTRUCTION";
    @Override protected void onCreate(Bundle state) { super.onCreate(state); handle(getIntent()); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handle(intent); }
    private void handle(Intent intent) {
        ShortcutUtil.refreshDynamicShortcut(this);
        if (intent != null && intent.getBooleanExtra("open_settings", false)) {
            openManagement(); finish(); return;
        }
        boolean explicit = intent != null && (ACTION_RUN.equals(intent.getAction()) || intent.hasExtra("rule_id"));
        String id = explicit ? intent.getStringExtra("rule_id") : new InstructionSettings(this).defaultId();
        Rule selected = null;
        for (Rule rule : new RuleRepository(this).getRules()) if (rule.getId().equals(id)) { selected = rule; break; }
        if (selected == null || !selected.isEnabled() || selected.getActions().isEmpty()) {
            Toast.makeText(this, explicit ? "指令不存在或已停用" : "请先选择有效的默认指令", Toast.LENGTH_LONG).show();
            if (!explicit) openManagement();
        } else {
            runWhenAccessibilityReady(selected);
            return;
        }
        finish();
    }

    private void runWhenAccessibilityReady(Rule rule) {
        // Post beyond the launcher transaction. Xiaomi may suspend this process as soon as
        // the target app becomes foreground, so recovery and queue persistence must finish
        // before the deeplink is dispatched.
        new Handler(Looper.getMainLooper()).post(() ->
                AccessibilityAwareRuleRunner.enqueue(this, rule, (queued, message) -> {
                    if (!queued) {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        startActivity(new Intent(this, PermissionGuideActivity.class));
                    }
                    finish();
                }));
    }
    private void openManagement() {
        // Keep management outside the transient launcher task. Otherwise Android may
        // bring its previous top activity forward instead of running the launcher.
        startActivity(new Intent(this, RuleListActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
