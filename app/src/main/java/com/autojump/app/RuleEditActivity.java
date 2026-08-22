package com.autojump.app;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.autojump.app.bean.Rule;
import com.autojump.app.condition.*;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.domain.RuleCondition;
import com.autojump.app.util.SPUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 规则编辑页面（简化版）
 */
public class RuleEditActivity extends AppCompatActivity {
    private EditText etRuleName, etPriority;
    private LinearLayout llConditions, llActions;
    private Button btnAddCondition, btnAddAction, btnSave;

    private Rule mRule;
    private boolean isEdit = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rule_edit);

        etRuleName = findViewById(R.id.et_rule_name);
        etPriority = findViewById(R.id.et_priority);
        llConditions = findViewById(R.id.ll_conditions);
        llActions = findViewById(R.id.ll_actions);
        btnAddCondition = findViewById(R.id.btn_add_condition);
        btnAddAction = findViewById(R.id.btn_add_action);
        btnSave = findViewById(R.id.btn_save);

        // 判断是新增还是编辑
        String ruleId = getIntent().getStringExtra("rule_id");
        if (ruleId != null) {
            isEdit = true;
            List<Rule> rules = SPUtil.getRuleList(this);
            for (Rule r : rules) {
                if (r.getId().equals(ruleId)) {
                    mRule = r;
                    break;
                }
            }
        }

        if (mRule == null) {
            mRule = new Rule(UUID.randomUUID().toString(), "");
            mRule.setConditions(new ArrayList<>());
            mRule.setActions(new ArrayList<>());
        }

        loadRuleData();

        btnAddCondition.setOnClickListener(v -> showAddConditionDialog());
        btnAddAction.setOnClickListener(v -> showAddActionDialog());
        btnSave.setOnClickListener(v -> saveRule());
    }

    private void loadRuleData() {
        etRuleName.setText(mRule.getName());
        etPriority.setText(String.valueOf(mRule.getPriority()));

        refreshConditionList();
        refreshActionList();
    }

    private void refreshConditionList() {
        llConditions.removeAllViews();
        for (int i = 0; i < mRule.getConditions().size(); i++) {
            final int index = i;
            RuleCondition condition = mRule.getConditions().get(i);

            View itemView = LayoutInflater.from(this).inflate(R.layout.item_condition, llConditions, false);
            TextView tvDesc = itemView.findViewById(R.id.tv_condition_desc);
            Button btnRemove = itemView.findViewById(R.id.btn_remove_condition);

            tvDesc.setText(condition.getDescription());
            btnRemove.setOnClickListener(v -> {
                mRule.getConditions().remove(index);
                refreshConditionList();
            });

            llConditions.addView(itemView);
        }
    }

    private void refreshActionList() {
        llActions.removeAllViews();
        for (int i = 0; i < mRule.getActions().size(); i++) {
            final int index = i;
            RuleAction action = mRule.getActions().get(i);

            View itemView = LayoutInflater.from(this).inflate(R.layout.item_action, llActions, false);
            TextView tvAction = itemView.findViewById(R.id.tv_action);
            Button btnRemove = itemView.findViewById(R.id.btn_remove_action);

            tvAction.setText(action.getDescription());
            btnRemove.setOnClickListener(v -> {
                mRule.getActions().remove(index);
                refreshActionList();
            });

            llActions.addView(itemView);
        }
    }

    private void showAddConditionDialog() {
        String[] types = {"时间段", "WiFi", "蓝牙", "充电状态", "地理位置"};
        new AlertDialog.Builder(this)
                .setTitle("选择条件类型")
                .setItems(types, (d, which) -> {
                    switch (which) {
                        case 0: addTimeCondition(); break;
                        case 1: addWiFiCondition(); break;
                        case 2: addBluetoothCondition(); break;
                        case 3: addChargingCondition(); break;
                        case 4: addLocationCondition(); break;
                    }
                })
                .show();
    }

    private void addTimeCondition() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_time_condition, null);

        // 星期几复选框
        android.widget.CheckBox cbMon = dialogView.findViewById(R.id.cb_mon);
        android.widget.CheckBox cbTue = dialogView.findViewById(R.id.cb_tue);
        android.widget.CheckBox cbWed = dialogView.findViewById(R.id.cb_wed);
        android.widget.CheckBox cbThu = dialogView.findViewById(R.id.cb_thu);
        android.widget.CheckBox cbFri = dialogView.findViewById(R.id.cb_fri);
        android.widget.CheckBox cbSat = dialogView.findViewById(R.id.cb_sat);
        android.widget.CheckBox cbSun = dialogView.findViewById(R.id.cb_sun);

        // 时间输入框
        EditText etStartHour = dialogView.findViewById(R.id.et_start_hour);
        EditText etStartMin = dialogView.findViewById(R.id.et_start_min);
        EditText etEndHour = dialogView.findViewById(R.id.et_end_hour);
        EditText etEndMin = dialogView.findViewById(R.id.et_end_min);

        new AlertDialog.Builder(this)
                .setTitle("添加时间段条件")
                .setView(dialogView)
                .setPositiveButton("添加", (d, w) -> {
                    try {
                        // 收集选中的星期几
                        java.util.ArrayList<Integer> selectedDays = new java.util.ArrayList<>();
                        if (cbMon.isChecked()) selectedDays.add(1);
                        if (cbTue.isChecked()) selectedDays.add(2);
                        if (cbWed.isChecked()) selectedDays.add(3);
                        if (cbThu.isChecked()) selectedDays.add(4);
                        if (cbFri.isChecked()) selectedDays.add(5);
                        if (cbSat.isChecked()) selectedDays.add(6);
                        if (cbSun.isChecked()) selectedDays.add(7);

                        int[] daysArray = null;
                        if (!selectedDays.isEmpty()) {
                            daysArray = new int[selectedDays.size()];
                            for (int i = 0; i < selectedDays.size(); i++) {
                                daysArray[i] = selectedDays.get(i);
                            }
                        }

                        int startH = Integer.parseInt(etStartHour.getText().toString());
                        int startM = Integer.parseInt(etStartMin.getText().toString());
                        int endH = Integer.parseInt(etEndHour.getText().toString());
                        int endM = Integer.parseInt(etEndMin.getText().toString());

                        TimeCondition condition = new TimeCondition(daysArray, startH, startM, endH, endM);
                        mRule.getConditions().add(condition);
                        refreshConditionList();
                    } catch (Exception e) {
                        Toast.makeText(this, "输入格式错误", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addWiFiCondition() {
        EditText et = new EditText(this);
        et.setHint("输入 WiFi 名称（SSID）");
        new AlertDialog.Builder(this)
                .setTitle("添加 WiFi 条件")
                .setView(et)
                .setPositiveButton("添加", (d, w) -> {
                    String ssid = et.getText().toString().trim();
                    if (!ssid.isEmpty()) {
                        mRule.getConditions().add(new WiFiCondition(ssid));
                        refreshConditionList();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addBluetoothCondition() {
        EditText et = new EditText(this);
        et.setHint("输入蓝牙设备名称");
        new AlertDialog.Builder(this)
                .setTitle("添加蓝牙条件")
                .setView(et)
                .setPositiveButton("添加", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) {
                        mRule.getConditions().add(new BluetoothCondition(name));
                        refreshConditionList();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void addChargingCondition() {
        new AlertDialog.Builder(this)
                .setTitle("充电状态")
                .setItems(new String[]{"充电中", "未充电"}, (d, which) -> {
                    mRule.getConditions().add(new ChargingCondition(which == 0));
                    refreshConditionList();
                })
                .show();
    }

    private void addLocationCondition() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_location_condition, null);
        EditText etLat = dialogView.findViewById(R.id.et_latitude);
        EditText etLng = dialogView.findViewById(R.id.et_longitude);
        EditText etRadius = dialogView.findViewById(R.id.et_radius);
        EditText etName = dialogView.findViewById(R.id.et_place_name);
        Button btnUseCurrent = dialogView.findViewById(R.id.btn_use_current_location);

        btnUseCurrent.setOnClickListener(v -> {
            // 获取当前位置
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "请先在权限设置中授予位置权限", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (locationManager == null) {
                    Toast.makeText(this, "无法获取位置服务", Toast.LENGTH_SHORT).show();
                    return;
                }

                android.location.Location location = null;
                try {
                    android.location.Location gpsLoc = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                    android.location.Location netLoc = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);

                    if (gpsLoc != null && netLoc != null) {
                        location = gpsLoc.getTime() > netLoc.getTime() ? gpsLoc : netLoc;
                    } else if (gpsLoc != null) {
                        location = gpsLoc;
                    } else if (netLoc != null) {
                        location = netLoc;
                    }
                } catch (SecurityException e) {
                    Toast.makeText(this, "权限不足，无法获取位置", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (location != null) {
                    etLat.setText(String.valueOf(location.getLatitude()));
                    etLng.setText(String.valueOf(location.getLongitude()));
                    Toast.makeText(this, "已获取当前位置", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "未找到位置缓存，请打开地图 App 定位后重试", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "获取位置失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("添加地理位置条件")
                .setView(dialogView)
                .setPositiveButton("添加", (d, w) -> {
                    try {
                        double lat = Double.parseDouble(etLat.getText().toString());
                        double lng = Double.parseDouble(etLng.getText().toString());
                        int radius = Integer.parseInt(etRadius.getText().toString());
                        String name = etName.getText().toString().trim();

                        mRule.getConditions().add(new LocationCondition(lat, lng, radius, name));
                        refreshConditionList();
                    } catch (Exception e) {
                        Toast.makeText(this, "输入格式错误", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddActionDialog() {
        EditText et = new EditText(this);
        et.setHint("输入 deeplink（如 example://page/path");
        new AlertDialog.Builder(this)
                .setTitle("添加跳转动作")
                .setView(et)
                .setPositiveButton("添加", (d, w) -> {
                    String link = et.getText().toString().trim();
                    if (!link.isEmpty()) {
                        mRule.getActions().add(RuleAction.deeplink(link));
                        refreshActionList();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveRule() {
        String name = etRuleName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入规则名称", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int priority = Integer.parseInt(etPriority.getText().toString());
            mRule.setName(name);
            mRule.setPriority(priority);

            List<Rule> rules = SPUtil.getRuleList(this);
            if (isEdit) {
                // 更新已有规则
                for (int i = 0; i < rules.size(); i++) {
                    if (rules.get(i).getId().equals(mRule.getId())) {
                        rules.set(i, mRule);
                        break;
                    }
                }
            } else {
                // 新增规则
                rules.add(mRule);
            }

            SPUtil.saveRuleList(this, rules);
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
