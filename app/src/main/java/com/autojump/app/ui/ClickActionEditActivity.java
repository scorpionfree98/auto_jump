package com.autojump.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
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
import com.autojump.app.automation.RecordedTouch;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.util.SPUtil;
import com.google.gson.Gson;
import java.util.UUID;

/** Edits a single guarded accessibility click action. */
public class ClickActionEditActivity extends AppCompatActivity {
    public static final String EXTRA_ACTION_JSON = "action_json";
    public static final String EXTRA_RECORDING_DEEPLINK = "recording_deeplink";
    public static final String EXTRA_RESULT_ACTION_JSON = "result_action_json";
    private static final String STATE_REQUEST_ID = "record_request_id";
    private static final String STATE_DRAFT_ID = "draft_action_id";
    private static final String STATE_RECORDED_ACTION = "recorded_touch_action";

    private EditText targetPackage;
    private EditText delay;
    private EditText timeout;
    private EditText retry;
    private EditText viewId;
    private EditText targetText;
    private EditText contentDescription;
    private EditText relativeX;
    private EditText relativeY;
    private EditText recordingDeeplink;
    private EditText repeatCount;
    private EditText repeatInterval;
    private SwitchCompat continueOnFailure;
    private TextView serviceStatus;
    private TextView touchTraceStatus;

    private final Gson gson = new Gson();
    private ClickRecordingStore recordingStore;
    private String requestId;
    private String draftActionId;
    private RuleAction recordedTouchAction;

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
        recordingDeeplink = findViewById(R.id.et_recording_deeplink);
        repeatCount = findViewById(R.id.et_repeat_count);
        repeatInterval = findViewById(R.id.et_repeat_interval);
        continueOnFailure = findViewById(R.id.switch_continue_on_failure);
        serviceStatus = findViewById(R.id.tv_service_status);
        touchTraceStatus = findViewById(R.id.tv_touch_trace_status);
        Button openSettings = findViewById(R.id.btn_open_accessibility_settings);
        Button record = findViewById(R.id.btn_record_click);
        Button save = findViewById(R.id.btn_save_click_action);

        recordingStore = new ClickRecordingStore(this);
        draftActionId = savedInstanceState == null
                ? UUID.randomUUID().toString() : savedInstanceState.getString(STATE_DRAFT_ID);
        requestId = savedInstanceState == null ? null : savedInstanceState.getString(STATE_REQUEST_ID);
        if (savedInstanceState != null) {
            String savedAction = savedInstanceState.getString(STATE_RECORDED_ACTION);
            if (!TextUtils.isEmpty(savedAction)) {
                recordedTouchAction = gson.fromJson(savedAction, RuleAction.class);
            }
        }

        if (savedInstanceState == null) {
            String suggestedDeeplink = getIntent().getStringExtra(EXTRA_RECORDING_DEEPLINK);
            recordingDeeplink.setText(TextUtils.isEmpty(suggestedDeeplink)
                    ? SPUtil.getDefaultLink(this) : suggestedDeeplink);
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
        record.setOnClickListener(v -> confirmAndPrepareRecording());
        save.setOnClickListener(v -> saveAction());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_DRAFT_ID, draftActionId);
        outState.putString(STATE_REQUEST_ID, requestId);
        if (recordedTouchAction != null) {
            outState.putString(STATE_RECORDED_ACTION, gson.toJson(recordedTouchAction));
        }
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

    private void confirmAndPrepareRecording() {
        if (!isAccessibilityServiceEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("需要开启自动点击服务")
                    .setMessage("服务只执行你保存的规则，并用于记录你主动选择的下一次点击。")
                    .setPositiveButton("去开启", (dialog, which) -> openAccessibilitySettings())
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        String deeplink = text(recordingDeeplink);
        if (deeplink == null || Uri.parse(deeplink).getScheme() == null) {
            Toast.makeText(this, "请输入有效的录制跳转链接", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("前往目标应用录制")
                .setMessage("应用会先打开上面的跳转链接。进入目标页面后，请点悬浮窗的“开始录制”，再点击目标控件；两次点击之间的时间会自动保存为延迟。测试和正式执行时不会显示悬浮窗。")
                .setPositiveButton("打开并准备", (dialog, which) -> prepareRecording(deeplink))
                .setNegativeButton("取消", null)
                .show();
    }

    private void prepareRecording(String deeplink) {
        ClickRecordingStore.State state = recordingStore.prepare(
                draftActionId, deeplink, System.currentTimeMillis());
        requestId = state.getRequestId();
        Intent launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(deeplink));
        try {
            startActivity(launchIntent);
        } catch (RuntimeException error) {
            recordingStore.reject(requestId, ClickRecordingStore.Status.REJECTED,
                    "无法打开录制跳转链接");
            applyRecordingResultIfAvailable();
        }
    }

    private void applyRecordingResultIfAvailable() {
        if (requestId == null) return;
        ClickRecordingStore.State state = recordingStore.get();
        if (!requestId.equals(state.getRequestId())) return;
        if (state.getStatus() == ClickRecordingStore.Status.CAPTURED) {
            if (state.getRecordedTouch() != null) {
                populate(state.getRecordedTouch());
                Toast.makeText(this, "已记录真实触摸轨迹", Toast.LENGTH_SHORT).show();
            } else {
                populate(state.getRecordedClick());
                Toast.makeText(this, "已读取旧版点击记录", Toast.LENGTH_SHORT).show();
            }
            clearRecordingRequest();
        } else if (state.getStatus() == ClickRecordingStore.Status.REJECTED
                || state.getStatus() == ClickRecordingStore.Status.EXPIRED) {
            String message = state.getRejectionReason();
            String failureMessage = TextUtils.isEmpty(message) ? "本次记录未完成" : message;
            Toast.makeText(this, failureMessage, Toast.LENGTH_LONG).show();
            clearRecordingRequest();
            touchTraceStatus.setText("录制未完成：" + failureMessage + "。请重新录制。");
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
        delay.setText(String.valueOf(click.getDelayBeforeMs()));
    }

    private void populate(RecordedTouch touch) {
        if (touch == null) return;
        recordedTouchAction = RuleAction.recordedTouch(touch.getTargetPackage(),
                touch.getDelayBeforeMs(), RuleAction.DEFAULT_TIMEOUT_MS,
                touch.getDisplayWidth(), touch.getDisplayHeight(), touch.getOrientation(),
                touch.getTargetAppVersion(), touch.getTrace());
        setText(targetPackage, touch.getTargetPackage());
        delay.setText(String.valueOf(touch.getDelayBeforeMs()));
        timeout.setText(String.valueOf(RuleAction.DEFAULT_TIMEOUT_MS));
        viewId.setText("");
        targetText.setText("");
        contentDescription.setText("");
        relativeX.setText("");
        relativeY.setText("");
        updateTouchTraceStatus();
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
        if (action.getType() == ActionType.RECORDED_TOUCH) {
            recordedTouchAction = action;
            repeatCount.setText(String.valueOf(action.getRepeatCount()));
            repeatInterval.setText(String.valueOf(action.getRepeatIntervalMs()));
            updateTouchTraceStatus();
        }
    }

    private void updateTouchTraceStatus() {
        if (recordedTouchAction == null) {
            touchTraceStatus.setText("尚未录制真实触摸轨迹");
            return;
        }
        touchTraceStatus.setText("已录制真实触摸："
                + recordedTouchAction.getTouchDurationMs() + "ms · "
                + recordedTouchAction.getTouchTrace().size() + " 个轨迹点 · "
                + recordedTouchAction.getRecordedDisplayWidth() + "×"
                + recordedTouchAction.getRecordedDisplayHeight());
    }

    private void saveAction() {
        try {
            RuleAction action;
            if (recordedTouchAction != null) {
                action = RuleAction.recordedTouch(
                        requiredText(targetPackage, "请输入目标应用包名"),
                        parseLong(delay, "点击延迟"), parseLong(timeout, "点击超时"),
                        recordedTouchAction.getRecordedDisplayWidth(),
                        recordedTouchAction.getRecordedDisplayHeight(),
                        recordedTouchAction.getRecordedOrientation(),
                        recordedTouchAction.getTargetAppVersion(),
                        recordedTouchAction.getTouchTrace());
                action.setRepeatCount(parseInteger(repeatCount, "回放次数"));
                action.setRepeatIntervalMs(parseLong(repeatInterval, "回放间隔"));
            } else {
                action = RuleAction.click(
                        requiredText(targetPackage, "请输入目标应用包名"),
                        parseLong(delay, "点击延迟"),
                        parseLong(timeout, "点击超时"),
                        parseLong(retry, "重试间隔"),
                        text(viewId), text(targetText), text(contentDescription),
                        parseNullableDouble(relativeX, "相对 X"),
                        parseNullableDouble(relativeY, "相对 Y"));
            }
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

    private int parseInteger(EditText field, String label) {
        try {
            return Integer.parseInt(requiredText(field, "请输入" + label));
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
