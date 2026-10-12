package com.autojump.app.shizuku;

import android.content.Context;
import android.os.RemoteException;
import androidx.annotation.Keep;
import cn.local.jiojio.BuildConfig;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Minimal Shizuku user service. It accepts only one validated accessibility component. */
public final class ShizukuAccessibilityBridgeService
        extends IShizukuAccessibilityBridge.Stub {
    private static final long COMMAND_TIMEOUT_SECONDS = 5L;

    public ShizukuAccessibilityBridgeService() {}

    @Keep public ShizukuAccessibilityBridgeService(Context ignored) {}

    @Override public String restoreAccessibility(String componentName) throws RemoteException {
        String expected = BuildConfig.APPLICATION_ID + "/"
                + "com.autojump.app.automation.AutoJumpAccessibilityService";
        if (!expected.equals(componentName)) {
            throw new RemoteException("无障碍组件格式无效");
        }
        try {
            String before = run("settings", "get", "secure",
                    "enabled_accessibility_services").trim();
            Set<String> enabled = AccessibilityServiceListPolicy.parse(before);
            boolean alreadyEnabled = enabled.contains(componentName);
            enabled.add(componentName);
            run("settings", "put", "secure", "enabled_accessibility_services",
                    AccessibilityServiceListPolicy.join(enabled));
            run("settings", "put", "secure", "accessibility_enabled", "1");

            String verifiedServices = run("settings", "get", "secure",
                    "enabled_accessibility_services").trim();
            String verifiedFlag = run("settings", "get", "secure",
                    "accessibility_enabled").trim();
            if (!AccessibilityServiceListPolicy.contains(verifiedServices, componentName)
                    || !"1".equals(verifiedFlag)) {
                throw new IllegalStateException("系统设置回读未确认恢复结果");
            }
            return alreadyEnabled ? "ALREADY_ENABLED" : "RESTORED";
        } catch (Exception error) {
            RemoteException remote = new RemoteException(error.getMessage() == null
                    ? "Shizuku 恢复失败" : error.getMessage());
            remote.initCause(error);
            throw remote;
        }
    }

    @Override public void destroy() {
        System.exit(0);
    }

    private String run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("系统设置命令超时");
        }
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) output.append('\n');
                output.append(line);
            }
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(output.length() == 0
                    ? "系统设置命令失败：" + process.exitValue() : output.toString());
        }
        return output.toString();
    }

}
