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
import cn.local.jiojio.R;
import com.autojump.app.automation.AccessibilityServiceState;
import com.autojump.app.automation.ClickRecordingStore;
import com.autojump.app.automation.RecordedClick;
import com.autojump.app.automation.RecordedTouch;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.util.SPUtil;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Edits a single guarded accessibility click action. */
public class ClickActionEditActivity extends AppCompatActivity {
    public static final String EXTRA_ACTION_JSON = "action_json";
    public static final String EXTRA_RECORDING_DEEPLINK = "recording_deeplink";
    public static final String EXTRA_RESULT_ACTION_JSON = "result_action_json";
    public static final String EXTRA_RESULT_ACTIONS_JSON = "result_actions_json";
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
    private EditText screenTolerance, offsetX, offsetY, pageSettle;
    private SwitchCompat continueOnFailure;
    private TextView serviceStatus;
    private TextView touchTraceStatus;

    private final Gson gson = new Gson();
    private ClickRecordingStore recordingStore;
    private String requestId;
    private String draftActionId;
    private RuleAction recordedTouchAction;
    private final List<RuleAction> recordedTouchActions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_click_action_edit);
        findViewById(R.id.btn_page_back).setOnClickListener(v -> finish());
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
        screenTolerance = findViewById(R.id.et_screen_tolerance);
        offsetX = findViewById(R.id.et_replay_offset_x);
        offsetY = findViewById(R.id.et_replay_offset_y);
        pageSettle = findViewById(R.id.et_page_settle);
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
                RuleAction[] savedActions = gson.fromJson(savedAction, RuleAction[].class);
                if (savedActions != null) recordedTouchActions.addAll(Arrays.asList(savedActions));
                recordedTouchAction = recordedTouchActions.isEmpty()
                        ? null : recordedTouchActions.get(0);
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
        findViewById(R.id.btn_preview_replay).setOnClickListener(v -> previewReplay());
        findViewById(R.id.btn_advanced_click).setOnClickListener(v -> {
            android.view.View advanced = findViewById(R.id.advanced_click);
            advanced.setVisibility(advanced.getVisibility() == android.view.View.VISIBLE
                    ? android.view.View.GONE : android.view.View.VISIBLE);
        });
        if (recordedTouchAction == null && !TextUtils.isEmpty(text(targetPackage))) {
            findViewById(R.id.advanced_click).setVisibility(android.view.View.VISIBLE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_DRAFT_ID, draftActionId);
        outState.putString(STATE_REQUEST_ID, requestId);
        if (recordedTouchAction != null) {
            outState.putString(STATE_RECORDED_ACTION, gson.toJson(recordedTouchActions));
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
            if (!state.getRecordedTouches().isEmpty()) {
                populate(state.getRecordedTouches());
                Toast.makeText(this, "已记录 " + state.getRecordedTouches().size()
                        + " 个触摸动作", Toast.LENGTH_SHORT).show();
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
        populate(touch == null ? Collections.emptyList() : Collections.singletonList(touch));
    }

    private void populate(List<RecordedTouch> touches) {
        if (touches == null || touches.isEmpty()) return;
        recordedTouchActions.clear();
        for (RecordedTouch touch : touches) {
            recordedTouchActions.add(RuleAction.recordedTouch(touch.getTargetPackage(),
                    touch.getDelayBeforeMs(), RuleAction.DEFAULT_TIMEOUT_MS,
                    touch.getDisplayWidth(), touch.getDisplayHeight(), touch.getOrientation(),
                    touch.getTargetAppVersion(), touch.getTrace()));
        }
        recordedTouchAction = recordedTouchActions.get(0);
        setText(targetPackage, recordedTouchAction.getTargetPackage());
        delay.setText(String.valueOf(recordedTouchAction.getDelayBeforeMs()));
        timeout.setText(String.valueOf(RuleAction.DEFAULT_TIMEOUT_MS));
        screenTolerance.setText("10"); offsetX.setText("0"); offsetY.setText("0"); pageSettle.setText("1000");
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
            recordedTouchActions.clear();
            recordedTouchActions.add(action);
            repeatCount.setText(String.valueOf(action.getRepeatCount()));
            repeatInterval.setText(String.valueOf(action.getRepeatIntervalMs()));
            screenTolerance.setText(String.valueOf(action.getScreenTolerancePercent()));
            offsetX.setText(String.valueOf(action.getReplayOffsetXPx()));
            offsetY.setText(String.valueOf(action.getReplayOffsetYPx()));
            pageSettle.setText(String.valueOf(action.getPageSettleMs()));
            updateTouchTraceStatus();
        }
    }

    private void updateTouchTraceStatus() {
        if (recordedTouchAction == null) {
            touchTraceStatus.setText("尚未录制真实触摸轨迹");
            return;
        }
        int count = recordedTouchActions.isEmpty() ? 1 : recordedTouchActions.size();
        if (count == 1) {
            touchTraceStatus.setText("已录制 1 个触摸动作 ✓\n可以调整等待时间和点击次数，然后保存。");
        } else {
            touchTraceStatus.setText("已连续录制 " + count
                    + " 个触摸动作 ✓\n保存后可在指令时间线逐条调整顺序和延迟。");
        }
        repeatCount.setEnabled(count == 1);
        repeatInterval.setEnabled(count == 1);
    }

    private void saveAction() {
        try {
            RuleAction action;
            if (recordedTouchAction != null) {
                List<RuleAction> actions = buildRecordedActions();
                Intent result = new Intent();
                result.putExtra(EXTRA_RESULT_ACTIONS_JSON, gson.toJson(actions));
                if (actions.size() == 1) {
                    result.putExtra(EXTRA_RESULT_ACTION_JSON, gson.toJson(actions.get(0)));
                }
                setResult(Activity.RESULT_OK, result);
                finish();
                return;
            } else {
                if (TextUtils.isEmpty(text(targetPackage))) {
                    throw new IllegalArgumentException("请先录制一次点击，再保存");
                }
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

    private List<RuleAction> buildRecordedActions() {
        if (recordedTouchActions.isEmpty()) {
            throw new IllegalArgumentException("请先录制一次点击，再保存");
        }
        String packageName = requiredText(targetPackage, "请输入目标应用包名");
        long actionTimeout = parseLong(timeout, "点击超时");
        List<RuleAction> actions = new ArrayList<>();
        for (int index = 0; index < recordedTouchActions.size(); index++) {
            RuleAction source = recordedTouchActions.get(index);
            long recordedDelay = index == 0 ? parseLong(delay, "点击延迟")
                    : source.getDelayBeforeMs();
            RuleAction action = RuleAction.recordedTouch(packageName, recordedDelay,
                    actionTimeout, source.getRecordedDisplayWidth(),
                    source.getRecordedDisplayHeight(), source.getRecordedOrientation(),
                    source.getTargetAppVersion(), source.getTouchTrace());
            if (recordedTouchActions.size() == 1) {
                action.setRepeatCount(parseInteger(repeatCount, "回放次数"));
                action.setRepeatIntervalMs(parseLong(repeatInterval, "回放间隔"));
            }
            applyReplaySettings(action);
            action.setContinueOnFailure(continueOnFailure.isChecked());
            actions.add(action);
        }
        return actions;
    }

    private void applyReplaySettings(RuleAction action) {
        action.setScreenTolerancePercent(parseInteger(screenTolerance,"比例容差"));
        try { action.setReplayOffsets(Double.parseDouble(text(offsetX)), Double.parseDouble(text(offsetY))); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("请输入有效的 X/Y 偏移"); }
        action.setPageSettleMs(parseLong(pageSettle,"页面稳定等待"));
    }

    private void previewReplay() {
        if (recordedTouchAction == null) { Toast.makeText(this,"请先录制点击",Toast.LENGTH_SHORT).show(); return; }
        try {
            RuleAction draft = gson.fromJson(gson.toJson(recordedTouchAction),RuleAction.class);
            applyReplaySettings(draft);
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width=metrics.widthPixels, height=metrics.heightPixels;
            int status=getResources().getIdentifier("status_bar_height","dimen","android");
            height=com.autojump.app.automation.RecordedViewportPolicy.height(draft.getRecordedDisplayWidth(),
                    draft.getRecordedDisplayHeight(),width,height,status==0?0:getResources().getDimensionPixelSize(status));
            java.util.List<com.autojump.app.domain.TouchPoint> mapped=
                    com.autojump.app.automation.RecordedTouchResolver.mappedTrace(draft,width,height);
            com.autojump.app.domain.TouchPoint p=mapped.get(0);
            String position="当前回放空间："+width+" × "+height+"\n校准后落点："
                    +Math.round(p.getRelativeX()*width)+", "+Math.round(p.getRelativeY()*height)+" px\n灰色为原落点，绿色为校准落点。仅为示意，不会触发点击。";
            android.widget.LinearLayout panel=new android.widget.LinearLayout(this);
            panel.setOrientation(android.widget.LinearLayout.VERTICAL);
            int padding=Math.round(20*metrics.density);panel.setPadding(padding,0,padding,0);
            TextView label=new TextView(this);label.setText(position);panel.addView(label);
            panel.addView(new RecordedTouchPreviewView(this,draft.getTouchTrace(),mapped,width,height),
                    new android.widget.LinearLayout.LayoutParams(-1,Math.round(260*metrics.density)));
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("校准落点预览")
                    .setView(panel).setPositiveButton("返回调整",null).show();
        } catch (IllegalArgumentException error) { Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show(); }
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
