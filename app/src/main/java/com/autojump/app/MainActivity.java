package com.autojump.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.autojump.app.util.RuleEngine;
import com.autojump.app.util.SPUtil;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 透明无布局，不展示界面
        SPUtil.initDefaultLink(this);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        // 从「设置」快捷进入配置页
        if (intent != null && intent.getBooleanExtra("open_settings", false)) {
            startActivity(new Intent(this, RuleListActivity.class));
            finish();
            return;
        }

        // 执行规则引擎匹配
        Log.d(TAG, "开始执行规则匹配...");
        try {
            int matchedCount = RuleEngine.executeRules(this);

            if (matchedCount == 0) {
                // 无规则匹配时，使用默认跳转（向后兼容）
                String defaultLink = SPUtil.getDefaultLink(this);
                if (defaultLink != null && !defaultLink.isEmpty()) {
                    Log.d(TAG, "无规则匹配，使用默认跳转");
                    Intent jump = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(defaultLink));
                    jump.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(jump);
                } else {
                    Log.d(TAG, "无规则匹配，且无默认跳转，静默退出");
                    // 给用户一个提示，引导去配置
                    Toast.makeText(this, "未匹配任何规则，请长按图标进入设置", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d(TAG, "匹配 " + matchedCount + " 条规则");
            }
        } catch (Exception e) {
            Log.e(TAG, "规则执行失败", e);
            Toast.makeText(this, "规则执行出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        finish();
    }
}
