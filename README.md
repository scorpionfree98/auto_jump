# Auto Jump · 一键跳转

面向 Android/HyperOS 自动任务的本地规则执行器。系统或用户启动应用后，它根据时间、WiFi、蓝牙、充电和位置条件匹配规则，并按顺序执行 deeplink、等待与受约束的延迟点击。

当前版本：`2.0.0-beta.1`。核心单元测试和 Debug APK 构建已通过；HyperOS 真机矩阵仍需连接设备验证，详见 [真机验收清单](docs/DEVICE_VALIDATION.md)。

## 功能

- 多条件 AND 匹配，匹配规则按优先级从高到低执行。
- 动作序列支持 `DEEPLINK`、`WAIT`、`CLICK`，可编辑、排序和设置失败后是否继续。
- 点击动作支持延迟、超时、重试、目标包名限制、控件 ID/文字/描述选择器和相对坐标回退。
- “记录下一次点击”只记录一次目标控件及相对坐标，5 分钟后过期。
- 密码框、可编辑输入内容、系统权限页、锁屏和桌面不会被记录或点击。
- 规则可选择“锁屏时唤醒并请求系统解锁”。认证完全交给 Android；应用不读取、保存或模拟密码、指纹和人脸。
- 权限与运行状态页可查看服务状态、待执行任务、点击记录状态和最近日志，并可停止全部待执行任务。
- JSON 规则文件采用 schema v3；旧版数组和 schema v1/v2 会自动迁移。

## 快速使用

1. 长按应用图标，选择“设置”。
2. 新增规则，设置条件及动作顺序。
3. 如需点击，先添加打开目标页面的 deeplink，再添加点击动作。
4. 在点击动作页填写目标应用包名、延迟等参数；可开启无障碍服务后选择“记录下一次点击”。
5. 如希望锁屏触发，开启“锁屏时唤醒并请求系统解锁”。安全锁仍需由系统完成认证。
6. 从“权限与运行状态”检查授权及任务状态，然后在规则页执行测试运行。

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
| 无障碍服务 | 执行已保存点击；按用户请求记录一次目标点击 | 仅点击功能需要，可随时关闭 |
| `ACCESS_WIFI_STATE` | 判断当前 WiFi SSID | 仅 WiFi 条件需要 |
| 精确/大致位置 | WiFi SSID 与位置条件读取 | 仅相关条件需要，不申请后台定位 |
| `BLUETOOTH_CONNECT` | Android 12+ 读取已配对设备 | 仅蓝牙条件需要 |
| Android 9 及以下写入存储 | 导出 JSON 到公共 Documents | 仅旧系统导出需要；新系统使用 MediaStore |

导入使用系统文件选择器，不需要读取整个外部存储。桌面快捷方式使用 AndroidX Shortcut API，不申请旧版 Launcher 安装快捷方式权限。

## 构建与验证

项目固定使用 JDK 17、Gradle 8.2、Android Gradle Plugin 8.2.2、SDK/Build Tools 34。当前工作流在 `172.31.129.120` 的 Docker 中构建：

```bash
bash docker/run_remote_gradle.sh :app:testDebugUnitTest :app:assembleDebug
```

远程同步默认目录为 `/home/kmkuang/codex_build/auto_jump_click`。本地无需安装 JDK、Android SDK 或 Docker；需要能通过 SSH 访问构建机。

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

- 自动化核心、点击录制、权限状态和安全解锁衔接：已实现。
- 120 Docker 单测及 Debug APK：已通过。
- HyperOS 真机测试：未执行，当前环境没有 ADB 和已连接设备。

许可证：MIT。
