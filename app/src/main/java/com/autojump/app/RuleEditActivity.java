package com.autojump.app;

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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import com.autojump.app.bean.Rule;
import com.autojump.app.automation.RecordingDeeplinkResolver;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.domain.RuleCondition;
import com.autojump.app.ui.ClickActionEditActivity;
import com.autojump.app.util.SPUtil;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Creates and edits a deterministic condition/action rule. */
public class RuleEditActivity extends AppCompatActivity {
    private static final int REQUEST_CLICK_ACTION = 2001;
    private static final String STATE_RULE = "draft_rule";
    private static final String STATE_IS_EDIT = "is_edit";
    private static final String STATE_PENDING_ACTION = "pending_action";

    private EditText ruleName;
    private EditText priority;
    private LinearLayout conditionsContainer;
    private LinearLayout actionsContainer;
    private SwitchCompat requestUnlock;
    private Rule rule;
    private boolean isEdit;
    private int pendingActionIndex = -1;
    private final Gson gson = new Gson();
    private final RecordingDeeplinkResolver recordingDeeplinkResolver =
            new RecordingDeeplinkResolver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rule_edit);
        setTitle("规则编辑");

        ruleName = findViewById(R.id.et_rule_name);
        priority = findViewById(R.id.et_priority);
        conditionsContainer = findViewById(R.id.ll_conditions);
        actionsContainer = findViewById(R.id.ll_actions);
        requestUnlock = findViewById(R.id.switch_request_unlock);
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

        addCondition.setOnClickListener(v -> showAddConditionDialog());
        addAction.setOnClickListener(v -> showAddActionDialog());
        save.setOnClickListener(v -> saveRule());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_RULE, rule);
        outState.putBoolean(STATE_IS_EDIT, isEdit);
        outState.putInt(STATE_PENDING_ACTION, pendingActionIndex);
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
        refreshConditions();
        refreshActions();
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
        for (int i = 0; i < actions.size(); i++) {
            final int index = i;
            View item = LayoutInflater.from(this)
                    .inflate(R.layout.item_action, actionsContainer, false);
            TextView description = item.findViewById(R.id.tv_action);
            Button edit = item.findViewById(R.id.btn_edit_action);
            Button up = item.findViewById(R.id.btn_move_up);
            Button down = item.findViewById(R.id.btn_move_down);
            Button remove = item.findViewById(R.id.btn_remove_action);
            description.setText((i + 1) + ". " + actions.get(i).getDescription());
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
        else if (action.getType() == ActionType.CLICK) launchClickEditor(index);
    }

    private void showDeeplinkDialog(int index) {
        EditText input = new EditText(this);
        input.setHint("如 example://page/path");
        if (index >= 0) input.setText(rule.getActions().get(index).getDeeplink());
        new AlertDialog.Builder(this)
                .setTitle(index >= 0 ? "编辑 deeplink" : "添加 deeplink")
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
            String json = data.getStringExtra(ClickActionEditActivity.EXTRA_RESULT_ACTION_JSON);
            RuleAction action = gson.fromJson(json, RuleAction.class);
            if (action == null || action.getType() != ActionType.CLICK) {
                throw new IllegalArgumentException("点击动作数据无效");
            }
            replaceOrAddAction(pendingActionIndex, action);
        } catch (RuntimeException error) {
            Toast.makeText(this, "无法保存点击动作", Toast.LENGTH_LONG).show();
        } finally {
            pendingActionIndex = -1;
        }
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
            Toast.makeText(this, "规则已保存", Toast.LENGTH_SHORT).show();
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
