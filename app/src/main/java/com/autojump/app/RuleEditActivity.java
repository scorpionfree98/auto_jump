package com.autojump.app;
import cn.local.jiojio.R;
import cn.local.jiojio.BuildConfig;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import com.autojump.app.bean.Rule;
import com.autojump.app.automation.RecordingDeeplinkResolver;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.domain.RuleCondition;
import com.autojump.app.domain.ShortcutEmoji;
import com.autojump.app.ui.ClickActionEditActivity;
import com.autojump.app.util.SPUtil;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Creates and edits a deterministic condition/action rule. */
public class RuleEditActivity extends AppCompatActivity {
    private static final int REQUEST_CLICK_ACTION = 2001;
    private static final String STATE_RULE = "draft_rule";
    private static final String STATE_IS_EDIT = "is_edit";
    private static final String STATE_PENDING_ACTION = "pending_action";
    private static final String STATE_INITIAL_DRAFT = "initial_draft";

    private EditText ruleName;
    private EditText priority;
    private LinearLayout conditionsContainer;
    private LinearLayout actionsContainer;
    private SwitchCompat requestUnlock;
    private SwitchCompat menuShortcut;
    private SwitchCompat defaultInstruction;
    private EditText shortcutRank;
    private Button shortcutIcon;
    private Rule rule;
    private boolean isEdit;
    private int pendingActionIndex = -1;
    private String initialDraftSignature;
    private final Gson gson = new Gson();
    private final RecordingDeeplinkResolver recordingDeeplinkResolver =
            new RecordingDeeplinkResolver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rule_edit);
        findViewById(R.id.btn_page_back).setOnClickListener(v -> requestExit());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { requestExit(); }
        });
        setTitle("编辑指令");

        ruleName = findViewById(R.id.et_rule_name);
        priority = findViewById(R.id.et_priority);
        conditionsContainer = findViewById(R.id.ll_conditions);
        actionsContainer = findViewById(R.id.ll_actions);
        requestUnlock = findViewById(R.id.switch_request_unlock);
        menuShortcut = findViewById(R.id.switch_menu_shortcut);
        defaultInstruction = findViewById(R.id.switch_default_instruction);
        shortcutRank = findViewById(R.id.et_shortcut_rank);
        shortcutIcon = findViewById(R.id.btn_shortcut_icon);
        Button addCondition = findViewById(R.id.btn_add_condition);
        Button addAction = findViewById(R.id.btn_add_action);
        Button save = findViewById(R.id.btn_save);

        if (savedInstanceState != null) {
            rule = (Rule) savedInstanceState.getSerializable(STATE_RULE);
            isEdit = savedInstanceState.getBoolean(STATE_IS_EDIT);
            pendingActionIndex = savedInstanceState.getInt(STATE_PENDING_ACTION, -1);
        } else {
            loadRule();
        }
        if (rule == null) rule = new Rule(UUID.randomUUID().toString(), "");
        showRule();
        initialDraftSignature = savedInstanceState == null
                ? draftSignature() : savedInstanceState.getString(STATE_INITIAL_DRAFT, draftSignature());
        ((TextView) findViewById(R.id.tv_editor_title)).setText(isEdit ? "编辑指令" : "创建你的指令");
        if (!rule.getConditions().isEmpty()) {
            ((Button) findViewById(R.id.btn_advanced_rule)).setText(
                    "高级设置 · 已有 " + rule.getConditions().size() + " 个运行条件");
        }

        addCondition.setOnClickListener(v -> showAddConditionDialog());
        addAction.setOnClickListener(v -> showAddActionDialog());
        shortcutIcon.setOnClickListener(v -> showShortcutIconDialog());
        save.setOnClickListener(v -> saveRule());
        findViewById(R.id.btn_quick_link).setOnClickListener(v -> showDeeplinkDialog(
                !rule.getActions().isEmpty() && rule.getActions().get(0).getType() == ActionType.DEEPLINK ? 0 : -1));
        findViewById(R.id.btn_quick_record).setOnClickListener(v -> {
            if (rule.getActions().isEmpty()) {
                Toast.makeText(this, "先设置要打开的页面，再录制点击", Toast.LENGTH_LONG).show();
            } else launchClickEditor(-1);
        });
        findViewById(R.id.btn_quick_wait).setOnClickListener(v -> showWaitDialog(-1));
        findViewById(R.id.btn_advanced_rule).setOnClickListener(v -> {
            View advanced = findViewById(R.id.advanced_rule);
            advanced.setVisibility(advanced.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
        findViewById(R.id.btn_save_test).setOnClickListener(v -> {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("保存并实际运行")
                    .setMessage("会打开目标页面并执行保存的操作，包括打卡点击。")
                    .setPositiveButton("保存并运行", (d, w) -> saveRule(true))
                    .setNegativeButton("取消", null).show();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_RULE, rule);
        outState.putBoolean(STATE_IS_EDIT, isEdit);
        outState.putInt(STATE_PENDING_ACTION, pendingActionIndex);
        outState.putString(STATE_INITIAL_DRAFT, initialDraftSignature);
    }

    private void requestExit() {
        if (initialDraftSignature.equals(draftSignature())) {
            finish();
            return;
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("保存修改？")
                .setMessage("当前指令有尚未保存的修改。")
                .setPositiveButton("保存", (dialog, which) -> saveRule())
                .setNegativeButton("不保存", (dialog, which) -> finish())
                .setNeutralButton("继续编辑", null)
                .show();
    }

    private String draftSignature() {
        return gson.toJson(rule) + '\n'
                + ruleName.getText() + '\n'
                + priority.getText() + '\n'
                + requestUnlock.isChecked() + '\n'
                + menuShortcut.isChecked() + '\n'
                + defaultInstruction.isChecked() + '\n'
                + shortcutRank.getText();
    }

    private void loadRule() {
        String ruleId = getIntent().getStringExtra("rule_id");
        if (ruleId != null) {
            for (Rule candidate : SPUtil.getRuleList(this)) {
                if (ruleId.equals(candidate.getId())) {
                    rule = candidate;
                    isEdit = true;
                    break;
                }
            }
        }
        if (rule == null) {
            rule = new Rule(UUID.randomUUID().toString(), "");
            isEdit = false;
        }
    }

    private void showRule() {
        ruleName.setText(rule.getName());
        priority.setText(String.valueOf(rule.getPriority()));
        requestUnlock.setChecked(rule.isRequestUnlockBeforeRun());
        menuShortcut.setChecked(rule.isShowInShortcutMenu());
        defaultInstruction.setChecked(rule.getId().equals(new com.autojump.app.data.InstructionSettings(this).defaultId()));
        shortcutRank.setText(String.valueOf(rule.getShortcutRank()));
        updateShortcutIconButton();
        refreshConditions();
        refreshActions();
    }

    private void showShortcutIconDialog() {
        List<String> options = ShortcutEmoji.options();
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) labels[i] = options.get(i) + "  使用这个图标";
        int checked = Math.max(0, options.indexOf(rule.getShortcutEmoji()));
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("选择指令图标")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    rule.setShortcutEmoji(options.get(which));
                    updateShortcutIconButton();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateShortcutIconButton() {
        shortcutIcon.setText("指令图标  " + rule.getShortcutEmoji());
    }

    private void refreshConditions() {
        conditionsContainer.removeAllViews();
        List<RuleCondition> conditions = rule.getConditions();
        for (int i = 0; i < conditions.size(); i++) {
            final int index = i;
            View item = LayoutInflater.from(this)
                    .inflate(R.layout.item_condition, conditionsContainer, false);
            TextView description = item.findViewById(R.id.tv_condition_desc);
            Button remove = item.findViewById(R.id.btn_remove_condition);
            description.setText(conditions.get(i).getDescription());
            remove.setOnClickListener(v -> {
                rule.getConditions().remove(index);
                refreshConditions();
            });
            conditionsContainer.addView(item);
        }
    }

    private void refreshActions() {
        actionsContainer.removeAllViews();
        List<RuleAction> actions = rule.getActions();
        if (actions.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有操作。先添加要打开的页面，再录制点击。\n也可以只打开页面，直接保存。");
            empty.setPadding(0, 16, 0, 16);
            actionsContainer.addView(empty);
        }
        for (int i = 0; i < actions.size(); i++) {
            final int index = i;
            View item = LayoutInflater.from(this)
                    .inflate(R.layout.item_action, actionsContainer, false);
            TextView description = item.findViewById(R.id.tv_action);
            Button edit = item.findViewById(R.id.btn_edit_action);
            Button up = item.findViewById(R.id.btn_move_up);
            Button down = item.findViewById(R.id.btn_move_down);
            Button remove = item.findViewById(R.id.btn_remove_action);
            description.setText((i + 1) + ". " + com.autojump.app.ui.ActionLabels.describe(actions.get(i)));
            edit.setOnClickListener(v -> editAction(index));
            up.setEnabled(i > 0);
            up.setOnClickListener(v -> moveAction(index, -1));
            down.setEnabled(i < actions.size() - 1);
            down.setOnClickListener(v -> moveAction(index, 1));
            remove.setOnClickListener(v -> {
                rule.getActions().remove(index);
                refreshActions();
            });
            actionsContainer.addView(item);
        }
    }

    private void showAddConditionDialog() {
        String[] types = {"时间段", "WiFi", "蓝牙", "充电状态", "地理位置"};
        new AlertDialog.Builder(this)
                .setTitle("选择条件类型")
                .setItems(types, (dialog, which) -> {
                    if (which == 0) addTimeCondition();
                    else if (which == 1) addWifiCondition();
                    else if (which == 2) addBluetoothCondition();
                    else if (which == 3) addChargingCondition();
                    else if (which == 4) addLocationCondition();
                })
                .show();
    }

    private void addTimeCondition() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_time_condition, null);
        CheckBox[] days = {
                view.findViewById(R.id.cb_mon), view.findViewById(R.id.cb_tue),
                view.findViewById(R.id.cb_wed), view.findViewById(R.id.cb_thu),
                view.findViewById(R.id.cb_fri), view.findViewById(R.id.cb_sat),
                view.findViewById(R.id.cb_sun)
        };
        EditText startHour = view.findViewById(R.id.et_start_hour);
        EditText startMinute = view.findViewById(R.id.et_start_min);
        EditText endHour = view.findViewById(R.id.et_end_hour);
        EditText endMinute = view.findViewById(R.id.et_end_min);
        new AlertDialog.Builder(this)
                .setTitle("添加时间段条件")
                .setView(view)
                .setPositiveButton("添加", (dialog, which) -> {
                    try {
                        ArrayList<Integer> selected = new ArrayList<>();
                        for (int i = 0; i < days.length; i++) {
                            if (days[i].isChecked()) selected.add(i + 1);
                        }
                        int[] selectedDays = selected.isEmpty() ? null : new int[selected.size()];
                        if (selectedDays != null) {
                            for (int i = 0; i < selected.size(); i++) selectedDays[i] = selected.get(i);
                        }
                        rule.getConditions().add(RuleCondition.time(selectedDays,
                                integer(startHour, "开始小时"), integer(startMinute, "开始分钟"),
                                integer(endHour, "结束小时"), integer(endMinute, "结束分钟")));
                        refreshConditions();
                    } catch (IllegalArgumentException error) {
                        showError(error);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addWifiCondition() {
        EditText input = new EditText(this);
        input.setHint("输入 WiFi 名称（SSID）");
        new AlertDialog.Builder(this)
                .setTitle("添加 WiFi 条件")
                .setView(input)
                .setPositiveButton("添加", (dialog, which) -> addConditionSafely(
                        () -> RuleCondition.wifi(input.getText().toString())))
                .setNegativeButton("取消", null)
                .show();
    }

    private void addBluetoothCondition() {
        EditText input = new EditText(this);
        input.setHint("输入蓝牙设备名称");
        new AlertDialog.Builder(this)
                .setTitle("添加蓝牙条件")
                .setView(input)
                .setPositiveButton("添加", (dialog, which) -> addConditionSafely(
                        () -> RuleCondition.bluetooth(input.getText().toString())))
                .setNegativeButton("取消", null)
                .show();
    }

    private void addChargingCondition() {
        new AlertDialog.Builder(this)
                .setTitle("充电状态")
                .setItems(new String[]{"充电中", "未充电"}, (dialog, which) -> {
                    rule.getConditions().add(RuleCondition.charging(which == 0));
                    refreshConditions();
                })
                .show();
    }

    private void addLocationCondition() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_location_condition, null);
        EditText latitude = view.findViewById(R.id.et_latitude);
        EditText longitude = view.findViewById(R.id.et_longitude);
        EditText radius = view.findViewById(R.id.et_radius);
        EditText name = view.findViewById(R.id.et_place_name);
        Button current = view.findViewById(R.id.btn_use_current_location);
        current.setOnClickListener(v -> fillCurrentLocation(latitude, longitude));
        new AlertDialog.Builder(this)
                .setTitle("添加地理位置条件")
                .setView(view)
                .setPositiveButton("添加", (dialog, which) -> {
                    try {
                        rule.getConditions().add(RuleCondition.location(
                                decimal(latitude, "纬度"), decimal(longitude, "经度"),
                                integer(radius, "范围半径"), name.getText().toString()));
                        refreshConditions();
                    } catch (IllegalArgumentException error) {
                        showError(error);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void fillCurrentLocation(EditText latitude, EditText longitude) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "请先在权限设置中授予位置权限", Toast.LENGTH_SHORT).show();
            return;
        }
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            Toast.makeText(this, "无法获取位置服务", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Location gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location best = gps == null ? network : network == null ? gps
                    : gps.getTime() >= network.getTime() ? gps : network;
            if (best == null) {
                Toast.makeText(this, "暂无位置缓存，请定位后重试", Toast.LENGTH_SHORT).show();
                return;
            }
            latitude.setText(String.valueOf(best.getLatitude()));
            longitude.setText(String.valueOf(best.getLongitude()));
            Toast.makeText(this, "已填入当前位置", Toast.LENGTH_SHORT).show();
        } catch (SecurityException error) {
            Toast.makeText(this, "位置权限不足", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAddActionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("选择动作类型")
                .setItems(new String[]{"打开 deeplink", "等待", "点击"}, (dialog, which) -> {
                    if (which == 0) showDeeplinkDialog(-1);
                    else if (which == 1) showWaitDialog(-1);
                    else if (which == 2) launchClickEditor(-1);
                })
                .show();
    }

    private void editAction(int index) {
        RuleAction action = rule.getActions().get(index);
        if (action.getType() == ActionType.DEEPLINK) showDeeplinkDialog(index);
        else if (action.getType() == ActionType.WAIT) showWaitDialog(index);
        else if (action.getType() == ActionType.CLICK
                || action.getType() == ActionType.RECORDED_TOUCH) launchClickEditor(index);
    }

    private void showDeeplinkDialog(int index) {
        EditText input = new EditText(this);
        input.setHint("如 example://page/path");
        if (index >= 0) input.setText(rule.getActions().get(index).getDeeplink());
        else input.setText(SPUtil.getDefaultLink(this));
        new AlertDialog.Builder(this)
                .setTitle(index >= 0 ? "修改目标页面" : "打开哪个页面")
                .setMessage("已填入默认链接，可以直接保存或粘贴其他应用链接。")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    try {
                        replaceOrAddAction(index, RuleAction.deeplink(input.getText().toString()));
                    } catch (IllegalArgumentException error) {
                        showError(error);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showWaitDialog(int index) {
        EditText input = new EditText(this);
        input.setHint("等待毫秒数，如 1500");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (index >= 0) input.setText(String.valueOf(rule.getActions().get(index).getWaitDurationMs()));
        new AlertDialog.Builder(this)
                .setTitle(index >= 0 ? "编辑等待动作" : "添加等待动作")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    try {
                        replaceOrAddAction(index, RuleAction.waitFor(longInteger(input, "等待时间")));
                    } catch (IllegalArgumentException error) {
                        showError(error);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void launchClickEditor(int index) {
        pendingActionIndex = index;
        Intent intent = new Intent(this, ClickActionEditActivity.class);
        intent.putExtra(ClickActionEditActivity.EXTRA_RECORDING_DEEPLINK,
                recordingDeeplinkBefore(index));
        if (index >= 0) {
            intent.putExtra(ClickActionEditActivity.EXTRA_ACTION_JSON,
                    gson.toJson(rule.getActions().get(index)));
        }
        startActivityForResult(intent, REQUEST_CLICK_ACTION);
    }

    private String recordingDeeplinkBefore(int index) {
        return recordingDeeplinkResolver.resolve(
                rule.getActions(), index, SPUtil.getDefaultLink(this));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CLICK_ACTION || resultCode != Activity.RESULT_OK || data == null) {
            if (requestCode == REQUEST_CLICK_ACTION) pendingActionIndex = -1;
            return;
        }
        try {
            String actionsJson = data.getStringExtra(
                    ClickActionEditActivity.EXTRA_RESULT_ACTIONS_JSON);
            if (actionsJson != null) {
                RuleAction[] actions = gson.fromJson(actionsJson, RuleAction[].class);
                if (actions == null || actions.length == 0) {
                    throw new IllegalArgumentException("录制动作数据无效");
                }
                for (RuleAction action : actions) validateClickAction(action);
                replaceOrAddActions(pendingActionIndex, Arrays.asList(actions));
            } else {
                String json = data.getStringExtra(
                        ClickActionEditActivity.EXTRA_RESULT_ACTION_JSON);
                RuleAction action = gson.fromJson(json, RuleAction.class);
                validateClickAction(action);
                replaceOrAddAction(pendingActionIndex, action);
            }
        } catch (RuntimeException error) {
            Toast.makeText(this, "无法保存点击动作", Toast.LENGTH_LONG).show();
        } finally {
            pendingActionIndex = -1;
        }
    }

    private void validateClickAction(RuleAction action) {
        if (action == null || (action.getType() != ActionType.CLICK
                && action.getType() != ActionType.RECORDED_TOUCH)) {
            throw new IllegalArgumentException("点击动作数据无效");
        }
    }

    private void replaceOrAddActions(int index, List<RuleAction> actions) {
        if (index >= 0 && index < rule.getActions().size()) {
            rule.getActions().remove(index);
            rule.getActions().addAll(index, actions);
        } else {
            rule.getActions().addAll(actions);
        }
        refreshActions();
    }

    private void replaceOrAddAction(int index, RuleAction action) {
        if (index >= 0 && index < rule.getActions().size()) rule.getActions().set(index, action);
        else rule.getActions().add(action);
        refreshActions();
    }

    private void moveAction(int index, int offset) {
        int target = index + offset;
        if (target < 0 || target >= rule.getActions().size()) return;
        Collections.swap(rule.getActions(), index, target);
        refreshActions();
    }

    private void saveRule() {
        saveRule(false);
    }

    private void saveRule(boolean runNow) {
        String name = ruleName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入规则名称", Toast.LENGTH_SHORT).show();
            return;
        }
        if (rule.getActions().isEmpty()) {
            Toast.makeText(this, "请至少添加一个动作", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            rule.setName(name);
            rule.setPriority(integer(priority, "优先级"));
            rule.setRequestUnlockBeforeRun(requestUnlock.isChecked());
            rule.setShowInShortcutMenu(menuShortcut.isChecked());
            rule.setShortcutRank(integer(shortcutRank, "菜单排序"));
            List<Rule> rules = SPUtil.getRuleList(this);
            boolean replaced = false;
            if (isEdit) {
                for (int i = 0; i < rules.size(); i++) {
                    if (rule.getId().equals(rules.get(i).getId())) {
                        rules.set(i, rule);
                        replaced = true;
                        break;
                    }
                }
            }
            if (!replaced) rules.add(rule);
            SPUtil.saveRuleList(this, rules);
            com.autojump.app.data.InstructionSettings settings = new com.autojump.app.data.InstructionSettings(this);
            if (defaultInstruction.isChecked()) settings.setDefaultId(rule.getId());
            else if (rule.getId().equals(settings.defaultId())) settings.setDefaultId("");
            com.autojump.app.util.ShortcutUtil.refreshDynamicShortcut(this);
            Toast.makeText(this, "指令已保存", Toast.LENGTH_SHORT).show();
            if (runNow) {
                Context app = getApplicationContext();
                com.autojump.app.automation.AccessibilityAwareRuleRunner.enqueue(app, rule,
                        (queued, message) -> {
                            if (!queued) Toast.makeText(app, message, Toast.LENGTH_LONG).show();
                        });
            }
            finish();
        } catch (IllegalArgumentException error) {
            showError(error);
        }
    }

    private interface ConditionFactory { RuleCondition create(); }

    private void addConditionSafely(ConditionFactory factory) {
        try {
            rule.getConditions().add(factory.create());
            refreshConditions();
        } catch (IllegalArgumentException error) {
            showError(error);
        }
    }

    private int integer(EditText field, String label) {
        try {
            return Integer.parseInt(required(field, label));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + "格式错误");
        }
    }

    private long longInteger(EditText field, String label) {
        try {
            return Long.parseLong(required(field, label));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + "格式错误");
        }
    }

    private double decimal(EditText field, String label) {
        try {
            return Double.parseDouble(required(field, label));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + "格式错误");
        }
    }

    private String required(EditText field, String label) {
        String value = field.getText().toString().trim();
        if (value.isEmpty()) throw new IllegalArgumentException("请输入" + label);
        return value;
    }

    private void showError(IllegalArgumentException error) {
        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
    }
}
