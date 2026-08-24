package com.autojump.app.automation;

import android.annotation.TargetApi;
import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Owns the accessibility overlay shown only while a click recording waits to start. */
final class RecordingOverlayController {
    interface Listener {
        void onStart(String requestId, String targetPackage);
        void onCancel(String requestId);
    }

    interface CaptureListener {
        void onCaptured(List<TouchTracePolicy.RawPoint> samples, int width, int height,
                        int orientation);
        void onRejected(String reason);
    }

    interface ConfirmationListener {
        void onConfirm(String requestId);
        void onRetry(String requestId);
        void onCancel(String requestId);
    }

    private static final long CAPTURE_TIMEOUT_MS = 30_000L;

    private final AccessibilityService service;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View overlay;
    private String requestId;
    private String targetPackage;
    private Runnable captureTimeout;

    RecordingOverlayController(AccessibilityService service) {
        this.service = service;
        this.windowManager = (WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);
    }

    boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && windowManager != null;
    }

    void show(String nextRequestId, String nextTargetPackage, Listener listener) {
        if (!isSupported() || nextRequestId == null || nextTargetPackage == null) return;
        if (overlay != null && nextRequestId.equals(requestId)
                && nextTargetPackage.equals(targetPackage)) return;
        hide();

        requestId = nextRequestId;
        targetPackage = nextTargetPackage;
        overlay = createView(listener);
        try {
            windowManager.addView(overlay, layoutParams());
        } catch (RuntimeException error) {
            overlay = null;
            requestId = null;
            targetPackage = null;
            throw error;
        }
    }

    boolean isShowingFor(String expectedRequestId, String expectedPackage) {
        return overlay != null && requestId != null && requestId.equals(expectedRequestId)
                && targetPackage != null && targetPackage.equals(expectedPackage);
    }

    void captureNextGesture(String nextRequestId, String nextTargetPackage,
                            CaptureListener listener) {
        if (!isSupported() || nextRequestId == null || nextTargetPackage == null) {
            listener.onRejected("当前系统不支持真实触摸录制");
            return;
        }
        hide();
        requestId = nextRequestId;
        targetPackage = nextTargetPackage;
        overlay = createCaptureView(listener);
        try {
            windowManager.addView(overlay, captureLayoutParams());
            captureTimeout = () -> finishRejected(listener, "等待触摸超时，请重新录制");
            handler.postDelayed(captureTimeout, CAPTURE_TIMEOUT_MS);
        } catch (RuntimeException error) {
            hide();
            listener.onRejected("无法启动真实触摸录制层");
        }
    }

    void showConfirmation(String nextRequestId, String nextTargetPackage,
                          ConfirmationListener listener) {
        if (!isSupported() || nextRequestId == null || nextTargetPackage == null) return;
        hide();
        requestId = nextRequestId;
        targetPackage = nextTargetPackage;
        overlay = createConfirmationView(listener);
        try {
            windowManager.addView(overlay, layoutParams());
        } catch (RuntimeException error) {
            hide();
            listener.onCancel(nextRequestId);
        }
    }

    void hide() {
        cancelCaptureTimeout();
        if (overlay != null) {
            try {
                windowManager.removeView(overlay);
            } catch (RuntimeException ignored) {
                // The system may already have removed the accessibility window.
            }
        }
        overlay = null;
        requestId = null;
        targetPackage = null;
    }

    private View createCaptureView(CaptureListener listener) {
        View capture = new View(service);
        capture.setBackgroundColor(Color.TRANSPARENT);
        List<TouchTracePolicy.RawPoint> samples = new ArrayList<>();
        boolean[] active = {false};
        capture.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (event.getPointerCount() != 1) {
                    finishRejected(listener, "只支持录制单指触摸");
                    return true;
                }
                samples.clear();
                active[0] = true;
                addCurrentSample(samples, event);
                return true;
            }
            if (!active[0]) return true;
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                active[0] = false;
                finishRejected(listener, "只支持录制单指触摸");
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                addHistoricalSamples(samples, event);
                addCurrentSample(samples, event);
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                addHistoricalSamples(samples, event);
                addCurrentSample(samples, event);
                active[0] = false;
                int width = Math.max(1, view.getWidth());
                int height = Math.max(1, view.getHeight());
                int orientation = service.getResources().getConfiguration().orientation;
                List<TouchTracePolicy.RawPoint> result = new ArrayList<>(samples);
                hide();
                listener.onCaptured(result, width, height, orientation);
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                active[0] = false;
                finishRejected(listener, "触摸录制被系统取消");
                return true;
            }
            return true;
        });
        return capture;
    }

    private View createConfirmationView(ConfirmationListener listener) {
        final String confirmationRequestId = requestId;
        final boolean[] actionTaken = {false};
        int padding = dp(12);
        LinearLayout root = new LinearLayout(service);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setElevation(dp(8));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(248, 255, 255, 255));
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(79, 70, 229));
        root.setBackground(background);

        TextView message = new TextView(service);
        message.setText("刚才的操作成功了吗？");
        message.setTextColor(Color.rgb(35, 35, 35));
        message.setTextSize(14);
        root.addView(message);

        LinearLayout actions = new LinearLayout(service);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(6);
        root.addView(actions, actionParams);

        Button confirm = new Button(service);
        confirm.setText("成功，保存");
        actions.addView(confirm);
        Button retry = new Button(service);
        retry.setText("重新录制");
        actions.addView(retry);
        Button cancel = new Button(service);
        cancel.setText("取消");
        actions.addView(cancel);

        confirm.setOnClickListener(view -> finishConfirmation(actionTaken,
                () -> listener.onConfirm(confirmationRequestId)));
        retry.setOnClickListener(view -> finishConfirmation(actionTaken,
                () -> listener.onRetry(confirmationRequestId)));
        cancel.setOnClickListener(view -> finishConfirmation(actionTaken,
                () -> listener.onCancel(confirmationRequestId)));
        return root;
    }

    private void finishConfirmation(boolean[] actionTaken, Runnable callback) {
        if (actionTaken[0]) return;
        actionTaken[0] = true;
        hide();
        callback.run();
    }

    private void addHistoricalSamples(List<TouchTracePolicy.RawPoint> samples, MotionEvent event) {
        for (int index = 0; index < event.getHistorySize(); index++) {
            samples.add(new TouchTracePolicy.RawPoint(event.getHistoricalX(0, index),
                    event.getHistoricalY(0, index), event.getHistoricalEventTime(index)));
        }
    }

    private void addCurrentSample(List<TouchTracePolicy.RawPoint> samples, MotionEvent event) {
        samples.add(new TouchTracePolicy.RawPoint(event.getX(0), event.getY(0),
                event.getEventTime()));
    }

    private void finishRejected(CaptureListener listener, String reason) {
        hide();
        listener.onRejected(reason);
    }

    private void cancelCaptureTimeout() {
        if (captureTimeout != null) handler.removeCallbacks(captureTimeout);
        captureTimeout = null;
    }

    private View createView(Listener listener) {
        final String clickRequestId = requestId;
        final String clickTargetPackage = targetPackage;
        final boolean[] actionTaken = {false};
        int padding = dp(12);
        LinearLayout root = new LinearLayout(service);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setElevation(dp(8));
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(245, 255, 255, 255));
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(98, 0, 238));
        root.setBackground(background);

        TextView message = new TextView(service);
        message.setText("准备记录目标点击");
        message.setTextColor(Color.rgb(40, 40, 40));
        message.setTextSize(14);
        root.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(service);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(6);
        root.addView(actions, actionParams);

        Button start = new Button(service);
        start.setText("开始录制");
        start.setMinWidth(dp(132));
        start.setMinimumWidth(dp(132));
        start.setMinHeight(dp(56));
        start.setMinimumHeight(dp(56));
        actions.addView(start);

        Button cancel = new Button(service);
        cancel.setText("取消");
        cancel.setMinHeight(dp(56));
        cancel.setMinimumHeight(dp(56));
        actions.addView(cancel);

        start.setOnClickListener(view -> {
            if (actionTaken[0]) return;
            actionTaken[0] = true;
            start.setEnabled(false);
            cancel.setEnabled(false);
            listener.onStart(clickRequestId, clickTargetPackage);
        });
        cancel.setOnClickListener(view -> {
            if (actionTaken[0]) return;
            actionTaken[0] = true;
            start.setEnabled(false);
            cancel.setEnabled(false);
            listener.onCancel(clickRequestId);
        });
        return root;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private WindowManager.LayoutParams layoutParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(16);
        params.y = dp(96);
        return params;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private WindowManager.LayoutParams captureLayoutParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }
}
