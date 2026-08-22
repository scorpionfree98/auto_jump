# 2.0.0-beta.1 构建记录

构建日期：2026-08-22

## 产物

- 文件：`artifacts/auto-jump-2.0.0-beta.1-debug.apk`
- applicationId：`com.autojump.app`
- versionName：`2.0.0-beta.1`
- versionCode：`2`
- 最低 Android：API 21
- 目标 Android：API 34
- 大小：6,102,239 bytes
- SHA-256：`df9c164be37e99d52a437cd3767e3e8bc9ae8ca6d9238805f68b803255233db8`
- 签名：Android Debug 签名，仅用于开发与真机验收

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
bash docker/run_remote_gradle.sh :app:testDebugUnitTest :app:assembleDebug
```

## 自动验证结果

- JUnit：27 passed，0 failed，0 errors，0 skipped。
- Debug APK：`BUILD SUCCESSFUL`。
- Android XML：格式检查通过。
- APK 压缩内容：完整性检查通过。
- 源码、提交正文与 Git 对象：指定旧助手及厂商名称扫描为零（历史重写及对象清理后复核）。

## 发布限制

这是 Debug beta 产物，不应作为应用商店正式包。当前环境没有 ADB 或已连接的 HyperOS 设备，因此点击、系统解锁回调及厂商后台限制仍需按 [DEVICE_VALIDATION.md](DEVICE_VALIDATION.md) 完成真机验收。验收通过后应配置受保护的正式签名，构建 release APK/AAB，并将版本提升为稳定版。
