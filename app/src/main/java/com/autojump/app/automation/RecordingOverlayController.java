package com.autojump.app.automation;

import android.annotation.TargetApi;
import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Owns the accessibility overlay shown only while a click recording waits to start. */
final class RecordingOverlayController {
    interface Listener {
        void onStart(String requestId, String targetPackage);
        void onCancel(String requestId);
    }

    private final AccessibilityService service;
    private final WindowManager windowManager;
    private View overlay;
    private String requestId;
    private String targetPackage;

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

    void hide() {
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

    private View createView(Listener listener) {
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
        start.setOnClickListener(view -> listener.onStart(requestId, targetPackage));
        actions.addView(start);

        Button cancel = new Button(service);
        cancel.setText("取消");
        cancel.setOnClickListener(view -> listener.onCancel(requestId));
        actions.addView(cancel);
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

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }
}
