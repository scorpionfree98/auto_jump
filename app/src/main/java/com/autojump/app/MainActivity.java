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
import com.autojump.app.automation.AutomationOrchestrator;
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
            // Do not open another app while the launcher's ActivityThread transaction is
            // still running. Some Xiaomi builds suspend that transaction as soon as the
            // target app becomes foreground, which also postpones accessibility callbacks
            // and recorded-touch timers until this app is opened again.
            Rule selectedRule = selected;
            android.content.Context application = getApplicationContext();
            finish();
            new Handler(Looper.getMainLooper()).post(() ->
                    new AutomationOrchestrator(application).enqueue(application, selectedRule));
            return;
        }
        finish();
    }
    private void openManagement() {
        // Keep management outside the transient launcher task. Otherwise Android may
        // bring its previous top activity forward instead of running the launcher.
        startActivity(new Intent(this, RuleListActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
