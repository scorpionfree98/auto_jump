package com.autojump.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.autojump.app.util.RuleEngine;
import com.autojump.app.util.SPUtil;
import com.autojump.app.util.ShortcutUtil;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 透明无布局，不展示界面
        SPUtil.initDefaultLink(this);
        // 静态“设置”入口由 shortcuts.xml 保证；这里只恢复 deeplink 动态快捷项，
        // 同时清理旧版本可能留下的重复“设置”动态项。
        ShortcutUtil.refreshDynamicShortcut(this);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        // 兼容旧版本已经缓存的「设置」快捷方式。新安装版本会由
        // shortcuts.xml 直接打开 RuleListActivity，避免透明跳板页成为任务根页面。
        if (intent != null && intent.getBooleanExtra("open_settings", false)) {
            startActivity(new Intent(this, RuleListActivity.class));
            finish();
            return;
        }

        // 桌面/动态快捷方式明确指定的链接优先，不进入规则引擎。
        if (intent != null) {
            String targetDeepLink = intent.getStringExtra("target_deep_link");
            if (targetDeepLink != null && !targetDeepLink.trim().isEmpty()) {
                openDeepLink(targetDeepLink);
                finish();
                return;
            }
        }

        // 执行规则引擎匹配
        Log.d(TAG, "开始执行规则匹配...");
        try {
            RuleEngine.ExecutionSummary summary = RuleEngine.execute(this);
            int matchedCount = summary.getMatchedRuleCount();

            if (summary.getEnabledRuleCount() == 0) {
                // 只有完全没有启用规则时才使用默认跳转。
                String defaultLink = SPUtil.getDefaultLink(this);
                if (defaultLink != null && !defaultLink.isEmpty()) {
                    Log.d(TAG, "没有启用规则，使用默认跳转");
                    openDeepLink(defaultLink);
                } else {
                    Log.d(TAG, "没有启用规则，且无默认跳转，静默退出");
                    Toast.makeText(this, "未匹配任何规则，请长按图标进入设置", Toast.LENGTH_SHORT).show();
                }
            } else if (matchedCount == 0) {
                Log.d(TAG, "当前条件未匹配任何已启用规则，静默退出");
            } else {
                Log.d(TAG, "匹配 " + matchedCount + " 条规则");
            }
        } catch (Exception e) {
            Log.e(TAG, "规则执行失败", e);
            Toast.makeText(this, "规则执行出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        finish();
    }

    private void openDeepLink(String deepLink) {
        Intent jump = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(deepLink));
        jump.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(jump);
    }
}
