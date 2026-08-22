package com.autojump.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import com.autojump.app.R;
import com.autojump.app.automation.AccessibilityServiceState;
import com.autojump.app.automation.ClickRecordingStore;
import com.autojump.app.automation.RecordedClick;
import com.autojump.app.domain.RuleAction;
import com.google.gson.Gson;
import java.util.UUID;

/** Edits a single guarded accessibility click action. */
public class ClickActionEditActivity extends AppCompatActivity {
    public static final String EXTRA_ACTION_JSON = "action_json";
    public static final String EXTRA_RESULT_ACTION_JSON = "result_action_json";
    private static final String STATE_REQUEST_ID = "record_request_id";
    private static final String STATE_DRAFT_ID = "draft_action_id";

    private EditText targetPackage;
    private EditText delay;
    private EditText timeout;
    private EditText retry;
    private EditText viewId;
    private EditText targetText;
    private EditText contentDescription;
    private EditText relativeX;
    private EditText relativeY;
    private SwitchCompat continueOnFailure;
    private TextView serviceStatus;

    private final Gson gson = new Gson();
    private ClickRecordingStore recordingStore;
    private String requestId;
    private String draftActionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_click_action_edit);
        setTitle("编辑点击动作");

        targetPackage = findViewById(R.id.et_target_package);
        delay = findViewById(R.id.et_click_delay);
        timeout = findViewById(R.id.et_click_timeout);
        retry = findViewById(R.id.et_retry_interval);
        viewId = findViewById(R.id.et_target_view_id);
        targetText = findViewById(R.id.et_target_text);
        contentDescription = findViewById(R.id.et_target_description);
        relativeX = findViewById(R.id.et_relative_x);
        relativeY = findViewById(R.id.et_relative_y);
        continueOnFailure = findViewById(R.id.switch_continue_on_failure);
        serviceStatus = findViewById(R.id.tv_service_status);
        Button openSettings = findViewById(R.id.btn_open_accessibility_settings);
        Button record = findViewById(R.id.btn_record_click);
        Button save = findViewById(R.id.btn_save_click_action);

        recordingStore = new ClickRecordingStore(this);
        draftActionId = savedInstanceState == null
                ? UUID.randomUUID().toString() : savedInstanceState.getString(STATE_DRAFT_ID);
        requestId = savedInstanceState == null ? null : savedInstanceState.getString(STATE_REQUEST_ID);

        if (savedInstanceState == null) {
            String json = getIntent().getStringExtra(EXTRA_ACTION_JSON);
            if (!TextUtils.isEmpty(json)) {
                try {
                    populate(gson.fromJson(json, RuleAction.class));
                } catch (RuntimeException error) {
                    Toast.makeText(this, "无法读取原点击动作", Toast.LENGTH_SHORT).show();
                }
            }
        }

        openSettings.setOnClickListener(v -> openAccessibilitySettings());
        record.setOnClickListener(v -> confirmAndArmRecording());
        save.setOnClickListener(v -> saveAction());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_DRAFT_ID, draftActionId);
        outState.putString(STATE_REQUEST_ID, requestId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
        applyRecordingResultIfAvailable();
    }

    private void updateServiceStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        serviceStatus.setText(enabled ? "自动点击服务：已开启 ✓" : "自动点击服务：未开启");
    }

    private boolean isAccessibilityServiceEnabled() {
        return AccessibilityServiceState.isEnabled(this);
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (RuntimeException error) {
            Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmAndArmRecording() {
        if (!isAccessibilityServiceEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("需要开启自动点击服务")
                    .setMessage("服务只执行你保存的规则，并用于记录你主动选择的下一次点击。")
                    .setPositiveButton("去开启", (dialog, which) -> openAccessibilitySettings())
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("记录下一次点击")
                .setMessage("接下来 5 分钟内，你在目标应用中的下一次普通点击会被记录为控件特征和相对坐标。密码框、输入内容、系统界面和桌面不会记录。")
                .setPositiveButton("开始记录", (dialog, which) -> armRecording())
                .setNegativeButton("取消", null)
                .show();
    }

    private void armRecording() {
        ClickRecordingStore.State state = recordingStore.arm(draftActionId, System.currentTimeMillis());
        requestId = state.getRequestId();
        Toast.makeText(this, "已开始记录，请点击目标控件", Toast.LENGTH_LONG).show();

        String packageName = text(targetPackage);
        if (packageName == null) return;
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent == null) {
            Toast.makeText(this, "未找到目标应用，请手动切换后点击", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(launchIntent);
        } catch (RuntimeException error) {
            Toast.makeText(this, "无法打开目标应用，请手动切换", Toast.LENGTH_LONG).show();
        }
    }

    private void applyRecordingResultIfAvailable() {
        if (requestId == null) return;
        ClickRecordingStore.State state = recordingStore.get();
        if (!requestId.equals(state.getRequestId())) return;
        if (state.getStatus() == ClickRecordingStore.Status.CAPTURED) {
            populate(state.getRecordedClick());
            Toast.makeText(this, "已记录目标点击", Toast.LENGTH_SHORT).show();
            clearRecordingRequest();
        } else if (state.getStatus() == ClickRecordingStore.Status.REJECTED
                || state.getStatus() == ClickRecordingStore.Status.EXPIRED) {
            String message = state.getRejectionReason();
            Toast.makeText(this, TextUtils.isEmpty(message) ? "本次记录未完成" : message,
                    Toast.LENGTH_LONG).show();
            clearRecordingRequest();
        }
    }

    private void clearRecordingRequest() {
        recordingStore.clear();
        requestId = null;
    }

    private void populate(RecordedClick click) {
        if (click == null) return;
        setText(targetPackage, click.getTargetPackage());
        setText(viewId, click.getViewId());
        setText(targetText, click.getText());
        setText(contentDescription, click.getContentDescription());
        relativeX.setText(String.valueOf(click.getRelativeX()));
        relativeY.setText(String.valueOf(click.getRelativeY()));
    }

    private void populate(RuleAction action) {
        if (action == null) return;
        setText(targetPackage, action.getTargetPackage());
        delay.setText(String.valueOf(action.getDelayBeforeMs()));
        timeout.setText(String.valueOf(action.getTimeoutMs()));
        retry.setText(String.valueOf(action.getRetryIntervalMs()));
        setText(viewId, action.getViewId());
        setText(targetText, action.getText());
        setText(contentDescription, action.getContentDescription());
        if (action.getRelativeX() != null) relativeX.setText(String.valueOf(action.getRelativeX()));
        if (action.getRelativeY() != null) relativeY.setText(String.valueOf(action.getRelativeY()));
        continueOnFailure.setChecked(action.isContinueOnFailure());
    }

    private void saveAction() {
        try {
            RuleAction action = RuleAction.click(
                    requiredText(targetPackage, "请输入目标应用包名"),
                    parseLong(delay, "点击延迟"),
                    parseLong(timeout, "点击超时"),
                    parseLong(retry, "重试间隔"),
                    text(viewId), text(targetText), text(contentDescription),
                    parseNullableDouble(relativeX, "相对 X"),
                    parseNullableDouble(relativeY, "相对 Y"));
            action.setContinueOnFailure(continueOnFailure.isChecked());
            Intent result = new Intent();
            result.putExtra(EXTRA_RESULT_ACTION_JSON, gson.toJson(action));
            setResult(Activity.RESULT_OK, result);
            finish();
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private long parseLong(EditText field, String label) {
        String value = requiredText(field, "请输入" + label);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + "格式错误");
        }
    }

    private Double parseNullableDouble(EditText field, String label) {
        String value = text(field);
        if (value == null) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + "格式错误");
        }
    }

    private String requiredText(EditText field, String message) {
        String value = text(field);
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }

    private String text(EditText field) {
        String value = field.getText().toString().trim();
        return value.isEmpty() ? null : value;
    }

    private void setText(EditText field, String value) {
        field.setText(value == null ? "" : value);
    }
}
