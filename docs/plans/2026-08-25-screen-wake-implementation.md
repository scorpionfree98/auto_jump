# Screen Wake Implementation Plan

**Goal:** 锁屏规则触发后可靠点亮屏幕，并在用户完成系统认证后续跑原自动化会话。

**Architecture:** 新增一个小型亮屏策略与 Android 亮屏控制器。`AutomationOrchestrator` 在启动现有透明解锁活动前调用控制器；WakeLock 使用固定超时兜底，现有解锁和续跑状态机保持不变。

**Tech Stack:** Android Java、PowerManager、KeyguardManager、JUnit 4、Gradle

---

### Task 1: 定义亮屏策略

**Files:**
- Create: `app/src/main/java/com/autojump/app/automation/ScreenWakePolicy.java`
- Test: `app/src/test/java/com/autojump/app/automation/ScreenWakePolicyTest.java`

1. 先写单元测试，覆盖熄屏唤醒、亮屏跳过和超时值。
2. 运行目标测试，确认实现前失败。
3. 添加最小策略实现。
4. 再次运行目标测试，确认通过。

### Task 2: 接入 Android 亮屏能力

**Files:**
- Create: `app/src/main/java/com/autojump/app/automation/ScreenWakeController.java`
- Modify: `app/src/main/java/com/autojump/app/automation/AutomationOrchestrator.java`
- Modify: `app/src/main/AndroidManifest.xml`

1. 声明普通权限 `android.permission.WAKE_LOCK`。
2. 控制器只在设备非交互状态时申请 `SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP`。
3. 使用超时版本 `acquire(timeout)` 防止持锁泄漏。
4. 在透明解锁活动启动前调用控制器。

### Task 3: 构建与真机回归

**Files:**
- Output: `app/build/outputs/apk/debug/app-debug.apk`

1. 运行全量 JVM 测试与 Android Lint。
2. 构建并覆盖安装 Debug APK。
3. 锁屏触发规则，确认亮屏和系统认证出现。
4. 解锁后确认原规则继续进入飞书并派发录制点击。

### Task 4: 修复真机录制等待反馈

**Files:**
- Modify: `app/src/main/java/com/autojump/app/automation/RecordingOverlayController.java`
- Modify: `app/src/main/java/com/autojump/app/ui/ClickActionEditActivity.java`
- Test: `app/src/test/java/com/autojump/app/automation/RecordingOverlayControllerTest.java`

1. 将透明捕获层的等待时间从 30 秒延长到 2 分钟，但不超过录制请求总 TTL。
2. 录制失败返回编辑页后，在轨迹状态区域持久显示原因，兼容系统屏蔽 Toast 的情况。
3. 重跑测试、Lint、构建和小米真机录制。
