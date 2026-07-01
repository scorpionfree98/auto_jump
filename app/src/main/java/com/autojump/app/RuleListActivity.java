package com.autojump.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.autojump.app.adapter.RuleAdapter;
import com.autojump.app.bean.Rule;
import com.autojump.app.util.SPUtil;
import java.util.List;

/**
 * 规则列表页面
 */
public class RuleListActivity extends AppCompatActivity implements RuleAdapter.OnRuleOperateListener {
    private RecyclerView rvRules;
    private List<Rule> mRuleList;
    private RuleAdapter mAdapter;
    private Button btnAddRule;
    private Button btnTestRun;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rule_list);

        rvRules = findViewById(R.id.rv_rules);
        btnAddRule = findViewById(R.id.btn_add_rule);
        btnTestRun = findViewById(R.id.btn_test_run);

        btnAddRule.setOnClickListener(v -> {
            // 跳转到规则编辑页面（新增）
            Intent intent = new Intent(this, RuleEditActivity.class);
            startActivity(intent);
        });

        btnTestRun.setOnClickListener(v -> {
            // 立即执行一次规则匹配（用于调试）
            testRun();
        });

        mRuleList = SPUtil.getRuleList(this);
        mAdapter = new RuleAdapter(this, mRuleList, this);
        rvRules.setLayoutManager(new LinearLayoutManager(this));
        rvRules.setAdapter(mAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从编辑页面返回后刷新列表
        mRuleList.clear();
        mRuleList.addAll(SPUtil.getRuleList(this));
        mAdapter.notifyDataSetChanged();
    }

    private void testRun() {
        new AlertDialog.Builder(this)
                .setTitle("测试运行")
                .setMessage("将立即执行一次规则匹配，检查当前状态下哪些规则会被触发。")
                .setPositiveButton("执行", (d, w) -> {
                    int matched = com.autojump.app.util.RuleEngine.executeRules(this);
                    new AlertDialog.Builder(this)
                            .setTitle("测试结果")
                            .setMessage("匹配了 " + matched + " 条规则")
                            .setPositiveButton("确定", null)
                            .show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onEdit(Rule rule) {
        Intent intent = new Intent(this, RuleEditActivity.class);
        intent.putExtra("rule_id", rule.getId());
        startActivity(intent);
    }

    @Override
    public void onDelete(Rule rule) {
        new AlertDialog.Builder(this)
                .setTitle("删除规则")
                .setMessage("确定删除「" + rule.getName() + "」？")
                .setPositiveButton("删除", (d, w) -> {
                    mRuleList.remove(rule);
                    SPUtil.saveRuleList(this, mRuleList);
                    mAdapter.notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onToggleEnabled(Rule rule) {
        rule.setEnabled(!rule.isEnabled());
        SPUtil.saveRuleList(this, mRuleList);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "权限设置");
        menu.add(0, 2, 0, "导出规则");
        menu.add(0, 3, 0, "导入规则");
        menu.add(0, 4, 0, "关于");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            startActivity(new Intent(this, PermissionGuideActivity.class));
            return true;
        } else if (item.getItemId() == 2) {
            exportRules();
            return true;
        } else if (item.getItemId() == 3) {
            importRules();
            return true;
        } else if (item.getItemId() == 4) {
            showAbout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("关于")
                .setMessage("一键跳转 v1.0\n\n基于条件自动执行 deeplink 跳转。\n\n建议：在 HyperOS 自动任务中设置每 15 分钟启动本应用，实现自动化跳转。")
                .setPositiveButton("确定", null)
                .show();
    }

    private void exportRules() {
        try {
            List<Rule> rules = SPUtil.getRuleList(this);
            if (rules.isEmpty()) {
                Toast.makeText(this, "没有规则可导出", Toast.LENGTH_SHORT).show();
                return;
            }

            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(rules);

            // 保存到外部存储（Documents 目录）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "autojump_rules_" + System.currentTimeMillis() + ".json");
                values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json");
                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOCUMENTS);

                android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Files.getContentUri("external"), values);
                if (uri != null) {
                    java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os != null) {
                        os.write(json.getBytes());
                        os.close();
                        Toast.makeText(this, "已导出到 Documents 目录", Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                // Android 9 及以下
                java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS);
                if (!dir.exists()) dir.mkdirs();
                java.io.File file = new java.io.File(dir, "autojump_rules_" + System.currentTimeMillis() + ".json");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                fos.write(json.getBytes());
                fos.close();
                Toast.makeText(this, "已导出到: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("导出失败")
                    .setMessage(e.getMessage())
                    .setPositiveButton("确定", null)
                    .show();
        }
    }

    private void importRules() {
        // 使用文件选择器
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        try {
            startActivityForResult(intent, REQUEST_CODE_IMPORT);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private static final int REQUEST_CODE_IMPORT = 1001;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_IMPORT && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                try {
                    java.io.InputStream is = getContentResolver().openInputStream(uri);
                    if (is != null) {
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();

                        String json = sb.toString();
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<Rule>>() {}.getType();
                        List<Rule> importedRules = gson.fromJson(json, type);

                        if (importedRules != null && !importedRules.isEmpty()) {
                            new AlertDialog.Builder(this)
                                    .setTitle("导入确认")
                                    .setMessage("找到 " + importedRules.size() + " 条规则，是否覆盖当前规则？")
                                    .setPositiveButton("覆盖", (d, w) -> {
                                        SPUtil.saveRuleList(this, importedRules);
                                        mRuleList.clear();
                                        mRuleList.addAll(importedRules);
                                        mAdapter.notifyDataSetChanged();
                                        Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();
                                    })
                                    .setNegativeButton("追加", (d, w) -> {
                                        List<Rule> current = SPUtil.getRuleList(this);
                                        current.addAll(importedRules);
                                        SPUtil.saveRuleList(this, current);
                                        mRuleList.clear();
                                        mRuleList.addAll(current);
                                        mAdapter.notifyDataSetChanged();
                                        Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();
                                    })
                                    .setNeutralButton("取消", null)
                                    .show();
                        } else {
                            Toast.makeText(this, "文件格式错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    new AlertDialog.Builder(this)
                            .setTitle("导入失败")
                            .setMessage(e.getMessage())
                            .setPositiveButton("确定", null)
                            .show();
                }
            }
        }
    }
}
