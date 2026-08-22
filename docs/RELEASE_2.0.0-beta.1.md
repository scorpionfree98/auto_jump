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
- 应用内安全自检：1 秒后按固定控件 ID 点击成功，成功日志与目标回调一致，完成后待执行队列为空。
- 崩溃日志：自检后为空。
- 热更新无障碍服务配置后，HyperOS 需要关闭并重新开启一次服务才能重新绑定；普通未改服务配置的覆盖安装保留授权。

## 发布限制

这是 Debug beta 产物，不应作为应用商店正式包。核心控件 ID 延迟点击已完成一台 HyperOS 真机验证，但文字/描述选择器、坐标回退、完整锁屏认证、后台限制和其他 Android 版本仍需按 [DEVICE_VALIDATION.md](DEVICE_VALIDATION.md) 验收。全部核心项通过后应配置受保护的正式签名，构建 release APK/AAB，并将版本提升为稳定版。
