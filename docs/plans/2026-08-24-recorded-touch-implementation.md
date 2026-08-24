# 真实触摸录制回放 Implementation Plan

> 实施要求：按任务顺序采用测试驱动开发；每个阶段通过后再进入下一阶段。

**Goal:** 将当前基于无障碍节点中心的“点击录制”重构为真实 `DOWN/MOVE/UP` 触摸轨迹录制，并在录制后及规则执行时忠实回放。

**Architecture:** 新增与节点点击分离的 `RECORDED_TOUCH` 动作和 schema v4 数据。开始录制后由一次性全屏 `TYPE_ACCESSIBILITY_OVERLAY` 捕获下一次触摸，隐藏覆盖层后立即回放并保存轨迹；正式执行仅校验目标包、延迟和显示配置，不再查找或使用节点候选。

**Tech Stack:** Java 8、Android AccessibilityService、TYPE_ACCESSIBILITY_OVERLAY、GestureDescription、Gson、JUnit 4、Gradle/Android SDK 34。

---

### Task 1: 冻结设计并清理旧实验

**Files:**
- Add: `docs/architecture/*.md`
- Add: `docs/plans/2026-08-24-recorded-touch-implementation.md`
- Restore experimental edits in `AutoJumpAccessibilityService.java`, `ClickGesturePolicy.java`, `ClickGesturePolicyTest.java`

1. 用 `git diff` 确认 4px 轨迹只存在于未提交实验。
2. 用精确反向补丁移除实验，不覆盖其他改动。
3. 运行 `git diff --check`。
4. 提交架构文档。

### Task 2: 建立真实触摸领域模型和 schema v4

**Files:**
- Create: `app/src/main/java/com/autojump/app/domain/TouchPoint.java`
- Modify: `app/src/main/java/com/autojump/app/domain/ActionType.java`
- Modify: `app/src/main/java/com/autojump/app/domain/RuleAction.java`
- Modify: `app/src/main/java/com/autojump/app/data/RuleDocument.java`
- Modify: `app/src/main/java/com/autojump/app/data/RuleJsonCodec.java`
- Test: `app/src/test/java/com/autojump/app/domain/RecordedTouchActionTest.java`
- Test: `app/src/test/java/com/autojump/app/data/RuleJsonCodecTest.java`

1. 先编写失败测试：轨迹必须至少包含 DOWN/UP 对应的两个点，时间单调，坐标在 `[0,1]`，总时长有效。
2. 增加 `RECORDED_TOUCH`，保存轨迹、录制显示宽高、方向和应用版本。
3. schema 升为 v4；旧带坐标 `CLICK` 仍按旧动作读取，避免破坏现有规则，新录制只生成 `RECORDED_TOUCH`。
4. 运行领域与 codec 单元测试。

### Task 3: 实现触摸采集和轨迹压缩

**Files:**
- Create: `app/src/main/java/com/autojump/app/automation/TouchTraceRecorder.java`
- Create: `app/src/main/java/com/autojump/app/automation/TouchTracePolicy.java`
- Modify: `app/src/main/java/com/autojump/app/automation/RecordedClick.java`
- Modify: `app/src/main/java/com/autojump/app/automation/ClickRecordingStore.java`
- Test: `app/src/test/java/com/autojump/app/automation/TouchTracePolicyTest.java`

1. 编写失败测试覆盖坐标归一化、事件时间归零、重复 MOVE 去重、时长上下限和取消行为。
2. 实现纯策略，将 MotionEvent 样本转换为持久化轨迹。
3. 录制状态增加 `REPLAYING`、`CONFIRMING`；只在即时回放完成后产生可保存结果。
4. 运行相关单元测试。

### Task 4: 将录制悬浮窗升级为一次性全屏触摸捕获层

**Files:**
- Modify: `app/src/main/java/com/autojump/app/automation/RecordingOverlayController.java`
- Modify: `app/src/main/java/com/autojump/app/automation/AutoJumpAccessibilityService.java`
- Test: `app/src/test/java/com/autojump/app/automation/TouchTraceReplayPolicyTest.java`

1. 保留右上角“开始录制/取消”控制层。
2. 点击开始后隐藏控制层并显示全屏透明、可触摸的无障碍覆盖层。
3. 捕获下一次单指 `DOWN/MOVE/UP`，拒绝多指、超时、系统包切换和越界轨迹。
4. 移除捕获层，再根据轨迹生成 GestureDescription；API 26+ 用连续 stroke 近似各段时间，API 24-25 用完整 Path 和总时长降级。
5. 即时回放完成后返回编辑页；取消或派发失败时保留可读错误。
6. 运行自动化单元测试和编译。

### Task 5: 正式规则执行使用独立轨迹后端

**Files:**
- Modify: `app/src/main/java/com/autojump/app/automation/AutomationOrchestrator.java`
- Modify: `app/src/main/java/com/autojump/app/automation/AutoJumpAccessibilityService.java`
- Create: `app/src/main/java/com/autojump/app/automation/RecordedTouchResolver.java`
- Test: `app/src/test/java/com/autojump/app/automation/RecordedTouchResolverTest.java`

1. 编写失败测试：目标包不匹配时等待；延迟未到时等待；显示配置兼容时回放；方向不兼容时失败。
2. `RECORDED_TOUCH` 不调用 NodeFinder，不读取节点中心。
3. 日志记录“录制轨迹已派发”和实际轨迹时长/点数，不记录页面敏感数据。
4. 保留旧 `CLICK` 兼容执行，但 UI 不再创建混合动作。
5. 运行完整 JVM 测试。

### Task 6: 编辑页接入新录制结果

**Files:**
- Modify: `app/src/main/java/com/autojump/app/ui/ClickActionEditActivity.java`
- Modify: `app/src/main/res/layout/activity_click_action_edit.xml`
- Modify: `app/src/main/java/com/autojump/app/RuleEditActivity.java`
- Modify: `app/src/main/java/com/autojump/app/adapter/RuleAdapter.java`

1. 录制结果显示“已录制真实触摸：时长、轨迹点数、方向”，不再把节点文本和 X/Y 当权威字段。
2. 新录制动作直接保存为 `RECORDED_TOUCH`。
3. 旧节点动作仍可查看和编辑，标明“兼容模式”。
4. 运行 lint 和 debug 构建。

### Task 7: 真机验收

**Files:**
- Modify: `docs/DEVICE_VALIDATION.md`

1. 在小米 14 Pro 覆盖安装 debug APK，确认既有规则保留。
2. 打开飞书 Deeplink，点击悬浮窗开始，手动点击已知可成功的打卡按钮。
3. 确认录制层立即回放后飞书产生真实“打卡成功”。
4. 再次打开 Deeplink，由规则自动回放同一轨迹。
5. 通过飞书考勤记录确认新增结果；不能只依赖 AutoJump 日志。
6. 记录 APK SHA-256、设备版本、轨迹点数/时长和真实结果，提交实现。

