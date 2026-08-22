# Auto Jump · 一键跳转

面向 Android/HyperOS 自动任务的本地规则执行器。系统或用户启动应用后，它根据时间、WiFi、蓝牙、充电和位置条件匹配规则，并按顺序执行 deeplink、等待与受约束的延迟点击。

当前版本：`2.0.0-beta.1`。36 项核心单元测试、Android Lint、Debug APK 构建和小米 14 Pro / HyperOS 3 的应用内延迟点击自检已通过；录制悬浮窗的真机回归待设备重新连接后完成，其余矩阵见 [真机验收清单](docs/DEVICE_VALIDATION.md)。

## 功能

- 多条件 AND 匹配，匹配规则按优先级从高到低执行。
- 动作序列支持 `DEEPLINK`、`WAIT`、`CLICK`，可编辑、排序和设置失败后是否继续。
- 点击动作支持延迟、超时、重试、目标包名限制、控件 ID/文字/描述选择器和相对坐标回退。
- “记录下一次点击”会使用最近的前置 deeplink（没有时使用默认 deeplink）打开目标页面；用户点击无障碍悬浮窗开始后，只记录一次目标控件、相对坐标和操作延迟，5 分钟后过期。
- 录制悬浮窗只在录制阶段显示；测试运行和正式规则执行按保存的延迟自动点击，不显示悬浮窗，也不等待用户确认。
- 密码框、可编辑输入内容、系统权限页、锁屏和桌面不会被记录或点击。
- 规则可选择“锁屏时唤醒并请求系统解锁”。认证完全交给 Android；应用不读取、保存或模拟密码、指纹和人脸。
- 权限与运行状态页可查看服务状态、待执行任务、点击记录状态和最近日志，并可停止全部待执行任务。
- 权限页提供仅点击 AutoJump 自身控件的安全自检，同时核对目标回调和成功日志，便于安装后验收无障碍服务。
- JSON 规则文件采用 schema v3；旧版数组和 schema v1/v2 会自动迁移。
- 管理首页使用 Material 卡片界面，并固定展示“默认跳转”兜底规则；没有启用自定义规则时，普通点击桌面图标会执行该链接。

## 快速使用

1. 长按应用图标，选择“设置”。
2. 新增规则，设置条件及动作顺序。
3. 如需点击，先添加打开目标页面的 deeplink，再添加点击动作。
4. 在点击动作页选择“记录下一次点击”，确认录制跳转链接；到达目标页面后点悬浮窗“开始录制”，再点击目标控件，包名、控件特征、坐标和延迟会自动回填。
5. 如希望锁屏触发，开启“锁屏时唤醒并请求系统解锁”。安全锁仍需由系统完成认证。
6. 从“权限与运行状态”检查授权及任务状态，先运行一次“安全点击测试”，再到规则页执行测试运行。

不开启无障碍服务时，普通条件匹配、deeplink 和等待动作仍可使用；点击动作会保留在待执行队列中。

## 动作执行模型

```text
应用启动
  → 匹配已启用规则
  → 建立持久化执行会话
  → 可选：请求 Android 系统解锁
  → 打开 deeplink / 等待
  → 无障碍服务在精确目标包内查找控件
  → 选择器点击，找不到时按配置使用相对坐标
  → 写入结果日志或保留失败状态
```

执行会话写入本地存储，页面切换或短暂进程重建后仍可继续。点击服务不会对未保存的目标包执行手势。

## 权限

| 权限或系统服务 | 用途 | 必需性 |
|---|---|---|
| 无障碍服务 | 执行已保存点击；按用户请求显示录制覆盖层并记录一次目标点击 | 仅点击功能需要，可随时关闭；不申请普通应用悬浮窗权限 |
| `ACCESS_WIFI_STATE` | 判断当前 WiFi SSID | 仅 WiFi 条件需要 |
| 精确/大致位置 | WiFi SSID 与位置条件读取 | 仅相关条件需要，不申请后台定位 |
| `BLUETOOTH_CONNECT` | Android 12+ 读取已配对设备 | 仅蓝牙条件需要 |
| Android 9 及以下写入存储 | 导出 JSON 到公共 Documents | 仅旧系统导出需要；新系统使用 MediaStore |

导入使用系统文件选择器，不需要读取整个外部存储。桌面快捷方式使用 AndroidX Shortcut API，不申请旧版 Launcher 安装快捷方式权限。

## 构建与验证

项目固定使用 JDK 17、Gradle 8.2、Android Gradle Plugin 8.2.2、SDK/Build Tools 34。当前工作流在 `172.31.129.120` 的 Docker 中构建：

```bash
bash docker/run_remote_gradle.sh :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

远程同步默认目录为 `/home/kmkuang/codex_build/auto_jump_click`。本地无需安装 JDK、Android SDK 或 Docker；需要能通过 SSH 访问构建机。

120 还提供常驻的 Android 9（API 28）AOSP 无界面模拟器，用于安装、页面跳转和基础 UI 回归：

```bash
bash docker/run_remote_emulator.sh start
bash docker/run_remote_emulator.sh wait
bash docker/run_remote_emulator.sh install
bash docker/run_remote_emulator.sh status
```

服务器没有暴露嵌套 KVM，因此模拟器使用软件虚拟化，首次开机和动画较慢。点击时序、录屏悬浮窗和 HyperOS 行为仍以真机验收为准。

远程 Debug 构建把 Android 调试密钥保存在构建机的固定目录 `/home/kmkuang/codex_build/autojump-signing/android`，容器重建后仍使用同一签名。该密钥只用于开发升级测试；正式发布必须使用单独、受保护且有离线备份的发布密钥。

本地有完整 Android 环境时也可运行：

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## 主要目录

```text
app/src/main/java/com/autojump/app/
├── automation/  执行会话、点击解析、无障碍服务、日志与录制
├── data/        schema v3 编解码、旧数据迁移与仓库
├── domain/      显式条件和动作模型
├── engine/      条件求值及规则匹配
├── ui/          点击动作编辑
├── MainActivity.java
├── RuleListActivity.java
├── RuleEditActivity.java
├── PermissionGuideActivity.java
└── UnlockGateActivity.java

docker/          可复现的本地/120 构建环境
docs/plans/      重构设计与实施计划
```

## 已知限制

- 普通应用不能绕过安全锁。PIN、图案、密码、指纹或人脸必须由 Android 系统认证。
- 蓝牙条件目前判断“已配对”，不等同于实时连接状态。
- 位置条件使用系统最近位置，缓存过旧时可能不匹配。
- 控件 ID/文字通常比坐标稳定；应用升级或界面变化后应重新记录并验证。
- HyperOS 的自启动、后台弹出和省电限制因机型与版本不同，需要按 [真机验收清单](docs/DEVICE_VALIDATION.md) 核验。

## 开发状态

- 自动化核心、录制阶段悬浮窗、延迟采集、权限状态和安全解锁衔接：已实现。
- 120 Docker 构建、36 项单元测试及 Android Lint：已通过。
- 120 Android 9 模拟器：规则首页、默认跳转展示、“新增规则”和空状态“创建规则”均已自动回归通过。
- Material 管理首页、固定可见的默认跳转卡片以及规则/点击/权限编辑页：已实现。
- 小米 14 Pro / HyperOS 3：安装、数据恢复、同签名覆盖升级、无障碍授权和 1 秒延迟控件 ID 点击已通过。
- 录制悬浮窗真机回归：APK 已生成，当前设备未连接，待补测。
- 其他 Android 版本、文字/描述选择器、坐标回退和完整锁屏矩阵：待继续验收。

许可证：MIT。
