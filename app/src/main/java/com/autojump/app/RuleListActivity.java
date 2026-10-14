package com.autojump.app;
import cn.local.jiojio.R;
import cn.local.jiojio.BuildConfig;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.autojump.app.adapter.RuleAdapter;
import com.autojump.app.automation.AccessibilityAwareRuleRunner;
import com.autojump.app.automation.AccessibilityServiceState;
import com.autojump.app.bean.Rule;
import com.autojump.app.data.RuleRepository;
import com.autojump.app.util.SPUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
    private TextView homeSummary;
    private TextView defaultDeeplink;
    private View emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rule_list);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        rvRules = findViewById(R.id.rv_rules);
        btnAddRule = findViewById(R.id.btn_add_rule);
        btnTestRun = findViewById(R.id.btn_test_run);
        homeSummary = findViewById(R.id.tv_home_summary);
        defaultDeeplink = findViewById(R.id.tv_default_deeplink);
        emptyState = findViewById(R.id.empty_state);
        Button emptyAddRule = findViewById(R.id.btn_empty_add_rule);
        Button editDefaultLink = findViewById(R.id.btn_edit_default_link);

        btnAddRule.setOnClickListener(v -> openNewRule());
        emptyAddRule.setOnClickListener(v -> openNewRule());
        editDefaultLink.setOnClickListener(v -> chooseDefault());
        findViewById(R.id.btn_tools).setOnClickListener(v -> startActivity(new Intent(this, cn.local.jiojio.ToolboxActivity.class)));
        findViewById(R.id.btn_shortcut_retry).setOnClickListener(v -> {
            com.autojump.app.util.ShortcutUtil.retry(this);
            updateHomeState();
        });
        findViewById(R.id.btn_permissions).setOnClickListener(v ->
                startActivity(new Intent(this, PermissionGuideActivity.class)));

        btnTestRun.setOnClickListener(v -> {
            // 立即执行一次规则匹配（用于调试）
            testRun();
        });

        mRuleList = SPUtil.getRuleList(this);
        mAdapter = new RuleAdapter(this, mRuleList, this);
        rvRules.setLayoutManager(new LinearLayoutManager(this));
        rvRules.setAdapter(mAdapter);
        updateHomeState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从编辑页面返回后刷新列表
        mRuleList.clear();
        mRuleList.addAll(SPUtil.getRuleList(this));
        mAdapter.notifyDataSetChanged();
        updateHomeState();
    }

    private void openNewRule() {
        startActivity(new Intent(this, RuleEditActivity.class));
    }

    private void updateHomeState() {
        int enabledCount = 0;
        for (Rule rule : mRuleList) if (rule.isEnabled()) enabledCount++;
        boolean serviceEnabled = AccessibilityServiceState.isEnabled(this);
        homeSummary.setText(serviceEnabled
                ? "● 自动点击已开启\n" + enabledCount + " 条指令已启用，可以运行"
                : "● 自动点击未开启\n打开页面后无法自动点击，请先授权");
        homeSummary.setTextColor(androidx.core.content.ContextCompat.getColor(this,
                serviceEnabled ? R.color.success : R.color.warning));
        homeSummary.setOnClickListener(v -> startActivity(new Intent(this, PermissionGuideActivity.class)));
        String defaultId = new com.autojump.app.data.InstructionSettings(this).defaultId();
        Rule chosen = null;
        for (Rule r : mRuleList) if (r.getId().equals(defaultId)) chosen = r;
        defaultDeeplink.setText(chosen == null ? "尚未选择默认指令" : chosen.getName()
                + (chosen.isEnabled() ? " · 普通点击即执行" : " · 已停用，请重新选择"));
        ((TextView)findViewById(R.id.tv_shortcut_status)).setText(com.autojump.app.util.ShortcutUtil.status(this));
        boolean empty = mRuleList.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvRules.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showDefaultLinkEditor() {
        EditText input = new EditText(this);
        input.setHint("如 example://page/path");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(SPUtil.getDefaultLink(this));
        input.setSelectAllOnFocus(true);
        new MaterialAlertDialogBuilder(this)
                .setTitle("编辑默认跳转")
                .setMessage("仅在没有启用任何自定义规则时使用。")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String link = input.getText().toString().trim();
                    if (link.isEmpty() || Uri.parse(link).getScheme() == null) {
                        Toast.makeText(this, "请输入有效的 deeplink", Toast.LENGTH_LONG).show();
                        return;
                    }
                    SPUtil.setDefaultLink(this, link);
                    defaultDeeplink.setText(link);
                    Toast.makeText(this, "默认跳转已保存", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void testRun() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("运行默认指令")
                .setMessage("与点击桌面图标相同：仅运行你选定的默认指令，轮到执行时检查条件。")
                .setPositiveButton("执行", (d, w) -> {
                    startActivity(new Intent(this, cn.local.jiojio.MainActivity.class));
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onRun(Rule rule) {
        java.lang.ref.WeakReference<RuleListActivity> owner = new java.lang.ref.WeakReference<>(this);
        new MaterialAlertDialogBuilder(this)
                .setTitle("运行「" + rule.getName() + "」")
                .setMessage("加入串行队列，轮到执行时检查条件。目标页面可能实际提交打卡。")
                .setPositiveButton("运行", (d, w) ->
                        AccessibilityAwareRuleRunner.enqueue(this, rule, (queued, message) -> {
                            RuleListActivity page = owner.get();
                            if (page == null || page.isFinishing() || page.isDestroyed()) return;
                            if (!queued) {
                                if (RunConditionDialog.handles(message)) {
                                    RunConditionDialog.show(page, rule, message, null);
                                    return;
                                }
                                Toast.makeText(page, message, Toast.LENGTH_LONG).show();
                                if (!message.contains("已合并")) page.startActivity(
                                        new Intent(page, PermissionGuideActivity.class));
                            } else {
                                page.updateHomeState();
                            }
                        }))
                .setNegativeButton("取消", null).show();
    }

    @Override
    public void onEdit(Rule rule) {
        Intent intent = new Intent(this, RuleEditActivity.class);
        intent.putExtra("rule_id", rule.getId());
        startActivity(intent);
    }

    @Override
    public void onDelete(Rule rule) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除规则")
                .setMessage("确定删除「" + rule.getName() + "」？")
                .setPositiveButton("删除", (d, w) -> {
                    mRuleList.remove(rule);
                    SPUtil.saveRuleList(this, mRuleList);
                    com.autojump.app.util.ShortcutUtil.refreshDynamicShortcut(this);
                    mAdapter.notifyDataSetChanged();
                    updateHomeState();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onToggleEnabled(Rule rule, boolean enabled) {
        rule.setEnabled(enabled);
        SPUtil.saveRuleList(this, mRuleList);
        com.autojump.app.util.ShortcutUtil.refreshDynamicShortcut(this);
        updateHomeState();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "权限与运行状态");
        menu.add(0, 2, 0, "导出规则");
        menu.add(0, 3, 0, "导入规则");
        menu.add(0, 4, 0, "关于");
        menu.add(0, 5, 0, "固定指令到桌面");
        menu.add(0, 6, 0, "恢复导入前的指令");
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
        } else if (item.getItemId() == 5) {
            java.util.ArrayList<Rule> valid = new java.util.ArrayList<>();
            for (Rule r : mRuleList) if (r.isEnabled() && !r.getActions().isEmpty()) valid.add(r);
            String[] names = new String[valid.size()];
            for (int i = 0; i < names.length; i++) names[i] = valid.get(i).getName();
            new MaterialAlertDialogBuilder(this).setTitle("固定指令到桌面")
                    .setItems(names, (d, i) -> { com.autojump.app.util.ShortcutUtil.pin(this, valid.get(i)); updateHomeState(); })
                    .setNegativeButton("取消", null).show();
            return true;
        } else if (item.getItemId() == 6) {
            String backup = getSharedPreferences("instruction_import_backup", MODE_PRIVATE)
                    .getString("previous_rules", "");
            if (backup.isEmpty()) { Toast.makeText(this, "没有导入备份", Toast.LENGTH_SHORT).show(); return true; }
            new MaterialAlertDialogBuilder(this).setTitle("恢复导入前的指令")
                    .setMessage("将替换当前指令。恢复后不会自动执行，请重新选择默认指令。")
                    .setPositiveButton("恢复", (d,w) -> {
                        SPUtil.saveRuleList(this, new RuleRepository(this).importRules(backup));
                        new com.autojump.app.data.InstructionSettings(this).setDefaultId("");
                        mRuleList.clear(); mRuleList.addAll(SPUtil.getRuleList(this));
                        com.autojump.app.util.ShortcutUtil.retry(this);
                        mAdapter.notifyDataSetChanged(); updateHomeState();
                    }).setNegativeButton("取消", null).show();
            return true;
        } else if (item.getItemId() == 4) {
            showAbout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("关于")
                .setMessage("工具合集 v" + BuildConfig.VERSION_NAME
                        + "\n\n按条件执行 deeplink、等待和你明确保存的点击动作。自动点击服务可随时关闭，待执行任务也可在“权限与运行状态”中清空。"
                        + "\n\n建议：在 HyperOS 自动任务中按需启动本应用。")
                .setPositiveButton("确定", null)
                .show();
    }

    private void chooseDefault() {
        java.util.ArrayList<Rule> enabled = new java.util.ArrayList<>();
        for (Rule r : mRuleList) if (r.isEnabled() && !r.getActions().isEmpty()) enabled.add(r);
        String[] names = new String[enabled.size()];
        for (int i = 0; i < names.length; i++) names[i] = enabled.get(i).getName();
        new MaterialAlertDialogBuilder(this).setTitle("选择默认指令")
                .setItems(names, (d, index) -> {
                    new com.autojump.app.data.InstructionSettings(this).setDefaultId(enabled.get(index).getId());
                    updateHomeState();
                }).setNegativeButton("取消", null)
                .setNeutralButton("不设默认", (d,w) -> {
                    new com.autojump.app.data.InstructionSettings(this).setDefaultId(""); updateHomeState();
                }).show();
    }

    private void exportRules() {
        try {
            List<Rule> rules = SPUtil.getRuleList(this);
            if (rules.isEmpty()) {
                Toast.makeText(this, "没有规则可导出", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent destination = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            destination.addCategory(Intent.CATEGORY_OPENABLE);
            destination.setType("application/json");
            destination.putExtra(Intent.EXTRA_TITLE, "toolkit_instructions_" + System.currentTimeMillis() + ".json");
            startActivityForResult(destination, REQUEST_CODE_EXPORT);
            return;

        } catch (Exception e) {
            new MaterialAlertDialogBuilder(this)
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

    private String importPreview(List<Rule> rules) {
        StringBuilder text=new StringBuilder("找到 ").append(rules.size()).append(" 条指令：\n");
        for(int i=0;i<rules.size() && i<6;i++) {
            Rule r=rules.get(i);text.append("• ").append(r.getName()).append(" · ").append(r.getActions().size())
                    .append(" 个动作 / ").append(r.getConditions().size()).append(" 个条件\n");
        }
        if(rules.size()>6)text.append("…另有 ").append(rules.size()-6).append(" 条\n");
        return text.append("\n导入不会执行或发布快捷方式，默认指令需重新选择。是否覆盖当前指令？").toString();
    }

    private static final int REQUEST_CODE_IMPORT = 1001;
    private static final int REQUEST_CODE_EXPORT = 1002;
    private void backupAndPrepareImport(List<Rule> imported, boolean replace) {
        String backup = new RuleRepository(this).exportRules(SPUtil.getRuleList(this));
        getSharedPreferences("instruction_import_backup", MODE_PRIVATE).edit().putString("previous_rules", backup).commit();
        new com.autojump.app.data.InstructionSettings(this).setDefaultId("");
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        if (!replace) for (Rule old : SPUtil.getRuleList(this)) ids.add(old.getId());
        for (Rule rule : imported) {
            rule.setShowInShortcutMenu(false);
            if (ids.contains(rule.getId())) rule.setId(java.util.UUID.randomUUID().toString());
            ids.add(rule.getId());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_EXPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (java.io.OutputStream stream = getContentResolver().openOutputStream(data.getData())) {
                if (stream == null) throw new java.io.IOException("无法写入所选文件");
                stream.write(new RuleRepository(this).exportRules(SPUtil.getRuleList(this))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                Toast.makeText(this, "指令已导出", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show(); }
            return;
        }
        if (requestCode == REQUEST_CODE_IMPORT && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
                    if (is == null) throw new java.io.IOException("无法读取所选文件");
                    if (is != null) {
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                                is, java.nio.charset.StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                            if (sb.length() > 4 * 1024 * 1024) throw new java.io.IOException("文件超过 4 MB");
                        }
                        reader.close();

                        String json = sb.toString();
                        List<Rule> importedRules = new RuleRepository(this).importRules(json);

                        if (importedRules != null && !importedRules.isEmpty()) {
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle("导入确认")
                                    .setMessage(importPreview(importedRules))
                                    .setPositiveButton("覆盖", (d, w) -> {
                                        backupAndPrepareImport(importedRules, true);
                                        SPUtil.saveRuleList(this, importedRules);
                                        com.autojump.app.util.ShortcutUtil.retry(this);
                                        mRuleList.clear();
                                        mRuleList.addAll(importedRules);
                                        mAdapter.notifyDataSetChanged();
                                        updateHomeState();
                                        Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();
                                    })
                                    .setNegativeButton("追加", (d, w) -> {
                                        backupAndPrepareImport(importedRules, false);
                                        List<Rule> current = SPUtil.getRuleList(this);
                                        current.addAll(importedRules);
                                        SPUtil.saveRuleList(this, current);
                                        com.autojump.app.util.ShortcutUtil.retry(this);
                                        mRuleList.clear();
                                        mRuleList.addAll(current);
                                        mAdapter.notifyDataSetChanged();
                                        updateHomeState();
                                        Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();
                                    })
                                    .setNeutralButton("取消", null)
                                    .show();
                        } else {
                            Toast.makeText(this, "文件格式错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("导入失败")
                            .setMessage(e.getMessage())
                            .setPositiveButton("确定", null)
                            .show();
                }
            }
        }
    }
}
