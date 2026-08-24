# 2.0.0-beta.1 构建记录

构建日期：2026-08-22

## 产物

- 文件：`artifacts/auto-jump-2.0.0-beta.1-stable-debug.apk`
- applicationId：`com.autojump.app`
- versionName：`2.0.0-beta.1`
- versionCode：`2`
- 最低 Android：API 21
- 目标 Android：API 34
- 大小：6,104,931 bytes
- SHA-256：`31f135a97d1926a2ac010bd0d46bf7c95f1857b7dbe48dd52513282a281582ee`
- 签名：固定 Android Debug 签名，仅用于开发与真机验收
- 证书 SHA-256：`34:0B:72:EE:55:A4:47:6F:1B:E2:9D:08:52:E7:2A:AA:29:58:07:60:B5:23:C6:F9:9F:6D:04:C8:93:94:33:73`

APK ZIP 完整性检查通过。`artifacts/` 被 `.gitignore` 排除，避免将二进制产物写入源码历史。

### 最新真机验收产物（2026-08-24）

- 文件：`artifacts/auto-jump-2.0.0-beta.1-truthful-click-debug.apk`
- 大小：7,191,069 bytes
- SHA-256：`0f08c1a3d0f07a53bdd1a575da46af9ac72984d115ab09c3c2e1bdc068760f4d`
- 语义修正：无障碍回调仅记录点击已派发，不再表示第三方业务已经成功。
- 验证：54 个 JVM 单元测试通过，Android Lint 无错误，APK 构建成功；小米 14 Pro 覆盖安装后安装包哈希一致、无障碍授权和规则配置保留、活动会话为 0。

### 录制触摸候选产物（2026-08-24）

- 文件：`artifacts/auto-jump-2.0.0-beta.1-recorded-touch-debug.apk`
- 大小：6,121,127 bytes
- SHA-256：`cf499ac5a3ad87846fd46fe2c3d9c02636a94536df530b5f0b5ec8ebf3c8c3f6`
- 行为：带有效录制坐标的动作在选择器匹配后始终派发 160ms 触摸手势；无坐标精确节点保留 `ACTION_CLICK`。
- 自动验证：修正远端删除同步后执行 clean 构建，56 个当前 JVM 单元测试通过，Android Lint 无错误，APK 构建成功。
- 真机状态：同坐标 160ms 诊断触摸已在飞书显示 `打卡成功`；本候选包待小米 14 Pro 重新连接后覆盖安装并做最终规则回归。

## 构建环境

- 120 服务器：`172.31.129.120`
- 远程目录：`/home/kmkuang/codex_build/auto_jump_click`
- Docker 镜像：`autojump-builder-click`
- JDK：17
- Gradle Wrapper：8.2
- Android Gradle Plugin：8.2.2
- SDK / Build Tools：34

执行命令：

```bash
bash docker/run_remote_gradle.sh clean :app:testDebugUnitTest :app:assembleDebug
```

## 自动验证结果

- JUnit：28 passed，0 failed，0 errors，0 skipped。
- Debug APK：`BUILD SUCCESSFUL`。
- Android XML：格式检查通过。
- APK 压缩内容：完整性检查通过。
- 源码、提交正文与 Git 对象：指定旧助手及厂商名称扫描为零（历史重写及对象清理后复核）。

## 真机验证结果

- 设备：小米 14 Pro（23116PN5BC / shennong）。
- 系统：HyperOS `OS3.0.307.0.WNBCNXM`，Android 16。
- 新安装、旧数据备份恢复、固定签名覆盖安装：通过。
- 无障碍服务由用户在系统设置中手动开启：通过。
- 应用内安全自检：1 秒后按固定控件 ID 派发点击，目标按钮回调和对应派发日志同时存在，完成后待执行队列为空。
- 崩溃日志：自检后为空。
- 热更新无障碍服务配置后，HyperOS 需要关闭并重新开启一次服务才能重新绑定；普通未改服务配置的覆盖安装保留授权。

## 发布限制

这是 Debug beta 产物，不应作为应用商店正式包。核心控件 ID 延迟点击已完成一台 HyperOS 真机验证，但文字/描述选择器、坐标回退、完整锁屏认证、后台限制和其他 Android 版本仍需按 [DEVICE_VALIDATION.md](DEVICE_VALIDATION.md) 验收。全部核心项通过后应配置受保护的正式签名，构建 release APK/AAB，并将版本提升为稳定版。
