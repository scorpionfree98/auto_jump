package com.autojump.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.autojump.app.automation.AccessibilityClickSelfTest;
import com.autojump.app.automation.AccessibilityServiceState;
import com.autojump.app.automation.AutomationOrchestrator;
import com.autojump.app.automation.ClickRecordingStore;
import com.autojump.app.automation.ExecutionLogStore;
import com.autojump.app.automation.ExecutionSession;
import com.autojump.app.automation.PendingExecutionStore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Explains optional permissions and gives the user control over automation state. */
public class PermissionGuideActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_LOCATION = 1001;
    private static final int REQUEST_CODE_BLUETOOTH = 1002;
    private static final long CLICK_TEST_RESULT_TIMEOUT_MS = 7_000L;

    private TextView accessibilityStatus;
    private TextView clickTestStatus;
    private TextView locationStatus;
    private TextView bluetoothStatus;
    private TextView pendingStatus;
    private TextView recordingStatus;
    private TextView recentLogs;
    private Button requestLocation;
    private Button requestBluetooth;
    private Button runClickTest;
    private Button clickTestTarget;

    private PendingExecutionStore pendingStore;
    private ClickRecordingStore recordingStore;
    private ExecutionLogStore logStore;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String clickTestSessionId;
    private boolean clickTestTargetObserved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_guide);
        setTitle("权限与运行状态");

        accessibilityStatus = findViewById(R.id.tv_accessibility_status);
        clickTestStatus = findViewById(R.id.tv_click_test_status);
        locationStatus = findViewById(R.id.tv_location_status);
        bluetoothStatus = findViewById(R.id.tv_bluetooth_status);
        pendingStatus = findViewById(R.id.tv_pending_status);
        recordingStatus = findViewById(R.id.tv_recording_status);
        recentLogs = findViewById(R.id.tv_recent_logs);
        requestLocation = findViewById(R.id.btn_request_location);
        requestBluetooth = findViewById(R.id.btn_request_bluetooth);
        runClickTest = findViewById(R.id.btn_run_click_test);
        clickTestTarget = findViewById(R.id.btn_click_test_target);

        pendingStore = new PendingExecutionStore(this);
        recordingStore = new ClickRecordingStore(this);
        logStore = new ExecutionLogStore(this);

        findViewById(R.id.btn_open_accessibility_settings).setOnClickListener(
                v -> openAccessibilitySettings());
        runClickTest.setOnClickListener(v -> runClickSelfTest());
        clickTestTarget.setOnClickListener(v -> onClickTestTargetObserved());
        requestLocation.setOnClickListener(v -> requestLocationPermission());
        requestBluetooth.setOnClickListener(v -> requestBluetoothPermission());
        findViewById(R.id.btn_app_settings).setOnClickListener(v -> openAppSettings());
        findViewById(R.id.btn_stop_automation).setOnClickListener(v -> confirmStopAutomation());
        findViewById(R.id.btn_clear_logs).setOnClickListener(v -> {
            logStore.clear();
            updateStatus();
            Toast.makeText(this, "运行记录已清空", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        accessibilityStatus.setText(AccessibilityServiceState.isEnabled(this)
                ? "自动点击服务：已开启 ✓" : "自动点击服务：未开启");

        boolean hasLocation = hasLocationPermission();
        locationStatus.setText(hasLocation ? "已授权 ✓" : "未授权");
        requestLocation.setEnabled(!hasLocation);

        boolean hasBluetooth = hasBluetoothPermission();
        bluetoothStatus.setText(hasBluetooth ? "已授权 ✓" : "未授权");
        requestBluetooth.setEnabled(!hasBluetooth);

        showPendingSessions(pendingStore.getAll());
        showRecording(recordingStore.get());
        showLogs(logStore.getAll());
    }

    private void runClickSelfTest() {
        if (!AccessibilityServiceState.isEnabled(this)) {
            clickTestStatus.setText("请先开启自动点击服务，再运行安全测试。");
            return;
        }
        clearStaleClickSelfTests();
        clickTestTargetObserved = false;
        clickTestTarget.setVisibility(View.VISIBLE);
        runClickTest.setEnabled(false);
        clickTestStatus.setText("测试已开始：1 秒后点击下方专用目标…");

        String viewId = getPackageName() + ":id/btn_click_test_target";
        ExecutionSession session = new AutomationOrchestrator(this).enqueue(this,
                AccessibilityClickSelfTest.createRule(getPackageName(), viewId));
        clickTestSessionId = session.getId();
        handler.postDelayed(() -> finishClickSelfTest(session.getId(), true),
                CLICK_TEST_RESULT_TIMEOUT_MS);
    }

    private void clearStaleClickSelfTests() {
        for (ExecutionSession session : pendingStore.getAll()) {
            if (AccessibilityClickSelfTest.RULE_ID.equals(session.getRuleId())) {
                pendingStore.remove(session.getId());
            }
        }
    }

    private void onClickTestTargetObserved() {
        if (clickTestSessionId == null) return;
        clickTestTargetObserved = true;
        clickTestStatus.setText("目标已收到点击，正在确认执行记录…");
        String sessionId = clickTestSessionId;
        handler.postDelayed(() -> finishClickSelfTest(sessionId, false), 250L);
    }

    private void finishClickSelfTest(String sessionId, boolean timedOut) {
        if (sessionId == null || !sessionId.equals(clickTestSessionId)) return;
        if (clickTestTargetObserved && hasSuccessfulExecutionLog(sessionId)) {
            clickTestStatus.setText("安全点击测试通过 ✓（延迟、识别和点击均正常）");
            resetClickTestControls();
            updateStatus();
            return;
        }

        ExecutionSession session = pendingStore.findById(sessionId);
        if (session != null && session.getStatus() == ExecutionSession.Status.FAILED) {
            clickTestStatus.setText("安全点击测试失败：" + safe(session.getLastError(), "点击未完成"));
            resetClickTestControls();
            updateStatus();
            return;
        }
        if (timedOut) {
            clickTestStatus.setText(clickTestTargetObserved
                    ? "安全点击测试未通过：未找到对应的成功执行记录。"
                    : "安全点击测试超时：专用目标未被点击。");
            resetClickTestControls();
            updateStatus();
        }
    }

    private boolean hasSuccessfulExecutionLog(String sessionId) {
        for (ExecutionLogStore.Entry entry : logStore.getAll()) {
            if (sessionId.equals(entry.getSessionId())
                    && ("DISPATCHED".equals(entry.getStatus())
                    || "COMPLETED".equals(entry.getStatus()))) {
                return true;
            }
        }
        return false;
    }

    private void resetClickTestControls() {
        clickTestSessionId = null;
        clickTestTargetObserved = false;
        clickTestTarget.setVisibility(View.GONE);
        runClickTest.setEnabled(true);
    }

    private void showPendingSessions(List<ExecutionSession> sessions) {
        if (sessions.isEmpty()) {
            pendingStatus.setText("没有待执行或失败的任务");
            return;
        }
        StringBuilder text = new StringBuilder("共 ").append(sessions.size()).append(" 条\n");
        for (int i = 0; i < sessions.size() && i < 5; i++) {
            ExecutionSession session = sessions.get(i);
            if (i > 0) text.append('\n');
            text.append("• ").append(safe(session.getRuleName(), "未命名规则"))
                    .append("：").append(statusName(session.getStatus()))
                    .append("，动作 ").append(session.getCurrentActionIndex() + 1)
                    .append('/').append(session.getActions().size());
            if (session.getLastError() != null && !session.getLastError().trim().isEmpty()) {
                text.append("（").append(session.getLastError()).append("）");
            }
        }
        if (sessions.size() > 5) text.append("\n…另有 ").append(sessions.size() - 5).append(" 条");
        pendingStatus.setText(text.toString());
    }

    private void showRecording(ClickRecordingStore.State state) {
        switch (state.getStatus()) {
            case WAITING_START:
                recordingStatus.setText("已打开目标应用，等待点击悬浮窗开始录制");
                break;
            case ARMED:
                long seconds = Math.max(0, (state.getExpiresAt() - System.currentTimeMillis()) / 1000);
                recordingStatus.setText("录制已开始，正在等待目标点击，约 " + seconds + " 秒后过期");
                break;
            case CAPTURED:
                recordingStatus.setText("已捕获点击，返回编辑页即可使用");
                break;
            case REJECTED:
            case EXPIRED:
                recordingStatus.setText(safe(state.getRejectionReason(), "上次记录未完成"));
                break;
            default:
                recordingStatus.setText("未在记录点击");
        }
    }

    private void showLogs(List<ExecutionLogStore.Entry> entries) {
        if (entries.isEmpty()) {
            recentLogs.setText("暂无运行记录");
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < entries.size() && i < 5; i++) {
            ExecutionLogStore.Entry entry = entries.get(i);
            if (i > 0) text.append('\n');
            text.append(format.format(new Date(entry.getTimestamp())))
                    .append(" · ").append(safe(entry.getRuleName(), "未命名规则"))
                    .append(" · ").append(safe(entry.getMessage(), entry.getStatus()));
        }
        recentLogs.setText(text.toString());
    }

    private String statusName(ExecutionSession.Status status) {
        if (status == null) return "等待";
        switch (status) {
            case RUNNING: return "执行中";
            case WAITING: return "等待中";
            case COMPLETED: return "已完成";
            case FAILED: return "失败";
            case CANCELLED: return "已取消";
            default: return "待执行";
        }
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQUEST_CODE_LOCATION);
        }
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_CODE_BLUETOOTH);
        }
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (RuntimeException error) {
            Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private void confirmStopAutomation() {
        new AlertDialog.Builder(this)
                .setTitle("停止全部自动化？")
                .setMessage("将删除所有待执行、等待中和失败的任务，并取消当前点击记录。已保存的规则不会删除。")
                .setPositiveButton("停止并清空", (dialog, which) -> {
                    pendingStore.clear();
                    recordingStore.clear();
                    updateStatus();
                    Toast.makeText(this, "已停止全部待执行任务", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateStatus();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
