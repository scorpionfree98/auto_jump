package cn.local.jiojio;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.List;

/** Device-only probe: native controls in the owned P1Test draft and calculator, never attendance. */
public final class SafeUiProbeInstrumentation extends Instrumentation {
    private Bundle args;
    private UiAutomation ui;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        args = arguments == null ? new Bundle() : arguments;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_CANCELED;
        try {
            if (!"cn.local.toolkit.runtime".equals(getTargetContext().getPackageName())) {
                throw new IllegalStateException("Only the isolated runtime test package is allowed");
            }
            ui = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            android.accessibilityservice.AccessibilityServiceInfo info = ui.getServiceInfo();
            info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            ui.setServiceInfo(info);
            AccessibilityNodeInfo root = ui.getRootInActiveWindow();
            require(root != null && "cn.local.toolkit".contentEquals(root.getPackageName()), "Not in toolkit");
            require(!root.findAccessibilityNodeInfosByText("P1Test").isEmpty(), "Not in owned test draft");
            List<AccessibilityNodeInfo> edits = root.findAccessibilityNodeInfosByViewId("cn.local.toolkit:id/btn_edit_action");
            require(edits.size() > 1, "Recorded test action is not visible");
            require(edits.get(1).performAction(AccessibilityNodeInfo.ACTION_CLICK), "Native edit action rejected");
            waitText("前往页面并录制", "cn.local.toolkit");
            if ("record_static".equals(args.getString("mode"))) {
                clickText("前往页面并录制", "cn.local.toolkit");
                clickText("打开并准备", "cn.local.toolkit");
                waitText("汇率转换", "com.miui.calculator");
                clickText("开始录制", "cn.local.toolkit");
                SystemClock.sleep(400);
                require("com.miui.calculator".contentEquals(ui.getRootInActiveWindow().getPackageName()), "Unsafe recording target");
                long down = SystemClock.uptimeMillis();
                inject(down, down, MotionEvent.ACTION_DOWN, 558, 2684);
                SystemClock.sleep(40);
                inject(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, 558, 2684);
                SystemClock.sleep(40);
                inject(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, 558, 2684);
                SystemClock.sleep(80);
                inject(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 558, 2684);
                waitText("已生效，完成", "cn.local.toolkit");
                result.putString("stream", "PASS: native draft edit, calculator capture with stationary MOVE samples, replay confirmation. Target business result still requires separate verification.\n");
            } else {
                result.putString("stream", "PASS: native P1Test edit action opened the recording editor without suppressing accessibility services.\n");
            }
            code = Activity.RESULT_OK;
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
        }
        finish(code, result);
    }

    private void inject(long down, long now, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, now, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { require(ui.injectInputEvent(event, true), "Input injection rejected"); }
        finally { event.recycle(); }
    }

    private AccessibilityNodeInfo waitText(String text, String pkg) {
        long deadline = SystemClock.elapsedRealtime() + 6000;
        do {
            List<AccessibilityNodeInfo> roots = new ArrayList<>();
            AccessibilityNodeInfo active = ui.getRootInActiveWindow();
            if (active != null) roots.add(active);
            for (AccessibilityWindowInfo window : ui.getWindows()) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null) roots.add(root);
            }
            for (AccessibilityNodeInfo root : roots) {
                if (!pkg.contentEquals(root.getPackageName())) continue;
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                    if (text.contentEquals(node.getText()) && node.isVisibleToUser()) return node;
                }
            }
            SystemClock.sleep(150);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Expected safe UI text not found: " + text);
    }

    private void clickText(String text, String pkg) {
        AccessibilityNodeInfo node = waitText(text, pkg);
        require(node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Native control rejected: " + text);
        SystemClock.sleep(300);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
