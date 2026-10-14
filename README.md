# 工具合集

Android 本地工具与快捷指令执行器。应用名称固定为“工具合集”，正式主仓库为 `auto_jump`，应用 ID 为 `cn.local.toolkit`。

当前版本：`0.2.3`（versionCode 5）。

## 主要能力

### 快捷指令

- 点击桌面图标，立即执行已启用且条件匹配的默认指令。
- 长按桌面图标可进入“工具合集入口”和“自动任务管理”。
- 可由小米系统自动任务等外部自动化工具启动，不在应用内重复实现定时器。
- 条件支持时间、WiFi、蓝牙、充电和位置，多个条件按 AND 关系判断。
- 动作支持 URL scheme、等待、点击和录制触摸回放，按顺序串行执行。
- 支持屏幕唤醒与 Android 系统解锁衔接；不会读取、保存或模拟 PIN、图案、密码、指纹和人脸。
- 执行结果区分“动作已提交”和“业务成功”；没有目标应用的业务反馈时不会报告成功。
- 任务有明确超时和终态，不做无限重试。

### 工具入口

- 高德地图选点、收藏和历史记录。
- 模拟位置授权检查、启动与停止。
- 设备信息、传感器、相机、麦克风、震动、声音和网络短样本测试。

高德 Key 不写入仓库。构建时使用 `-PAMAP_API_KEY=<本地Key>`，或仅在未提交的 `local.properties` 中配置：

```properties
AMAP_API_KEY=your-local-key
```

## 快捷指令使用

1. 长按应用图标，选择“自动任务管理”。
2. 新建或编辑指令，设置触发条件和动作顺序。
3. 需要打开第三方页面时，先添加 URL scheme 动作。
4. 需要录制点击时，在点击动作页开始录制，到目标页面完成触摸采集后保存。
5. 测试运行时以目标应用的明确业务反馈为准。

不开启无障碍服务时，条件匹配、URL scheme 和等待仍可使用；点击与触摸回放需要开启“工具合集自动点击”。

## 执行模型

```text
桌面图标 / 系统自动任务 / 手动测试
  → 条件匹配与任务入队
  → 可选唤醒和系统解锁
  → 打开 URL scheme
  → 按配置延迟执行录制触摸
  → 写入动作结果与终态
```

针对 HyperOS 在目标应用打开后暂停来源进程的问题，录制触摸会在跳转前提交给 Android 无障碍框架，由系统按配置延迟执行。该路径已在小米 14 Pro 上完成一次真实签到验证；这不是对所有目标应用和页面版本的普遍承诺。

## 构建

需要 JDK 17、Android SDK 35、Gradle 8.10.2 和 Android Gradle Plugin 8.6.1。

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Release 构建可选读取本地 `signing/release-key.properties`。`signing/`、keystore、Key、用户配置、APK、设备日志和备份均被 `.gitignore` 排除。

### GitHub Actions 自动构建

`.github/workflows/android-build.yml` 会在相关代码 push、Pull Request 和手动触发时运行单元测试、Release Lint，并上传 Debug 与 Release APK，产物保留 14 天。

未配置签名时，Release APK 是未签名产物；如果需要生成可覆盖正式安装包的签名 APK，请在仓库的 Actions Secrets 中配置：

- `ANDROID_RELEASE_KEYSTORE_BASE64`：release keystore 的 Base64 内容
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`
- `AMAP_API_KEY`：可选，仅在 push 或手动构建中注入

签名 Secrets 不会提供给 Pull Request。密钥文件和密码不会写入仓库或上传到构建产物。

## 目录

```text
app/src/main/java/cn/local/jiojio/       工具入口、地图与硬件工具
app/src/main/java/com/autojump/app/      自动任务管理与快捷指令 UI
app/src/main/java/com/autojump/app/automation/
                                         队列、执行会话、无障碍与录制回放
app/src/main/java/com/autojump/app/data/ 规则持久化、导入导出与迁移
app/src/test/                            单元测试
docker/                                  Server120 构建与模拟器脚本
docs/                                    设计、验证记录与待办
```

## 当前边界

- Shizuku 是可选增强，仅用于在用户明确授权后辅助恢复本应用无障碍服务，不是核心执行依赖，也不承诺重启后永久有效。
- 已支持一次连续录制最多 8 个点击或滑动；保存后生成独立动作，可在指令时间线逐条排序和调整延迟。该能力仍需目标真机验收。
- 分身能力保持独立实验，不合入本仓库主应用。
- 普通应用不能绕过 Android 安全锁，安全认证仍由系统和用户完成。
- 坐标及录制触摸会受分辨率、系统栏和目标页面布局变化影响，目标页面升级后应重新验证。

许可证：MIT。
