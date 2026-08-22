# Build baseline

Verified on 2026-08-22 after the accessibility click self-test was added.

## Source

- Branch: `refactor/accessibility-click`
- Local workspace: `/Users/scorpion/scripts/personal_tool/auto_jump`
- Remote workspace: `/home/kmkuang/codex_build/auto_jump_click`

## Toolchain

- Docker host: `172.31.129.120`
- Docker: 26.1.4
- Image: `autojump-builder-click`
- JDK: 17.0.19
- Gradle Wrapper: 8.2
- Android Gradle Plugin: 8.2.2
- Compile/target SDK: 34
- Build Tools: 34.0.0

## Verification

Commands:

```bash
bash docker/run_remote_gradle.sh clean :app:testDebugUnitTest :app:assembleDebug
```

Results:

- Unit tests: 28 passed, 0 failed, 0 errors, 0 skipped.
- APK assembly: successful.
- Package: `com.autojump.app`
- Version: `2.0.0-beta.1` (`versionCode` 2)
- Minimum SDK: 21
- Target SDK: 34
- APK size: 6,104,931 bytes
- APK SHA-256: `31f135a97d1926a2ac010bd0d46bf7c95f1857b7dbe48dd52513282a281582ee`
- Debug certificate SHA-256: `34:0B:72:EE:55:A4:47:6F:1B:E2:9D:08:52:E7:2A:AA:29:58:07:60:B5:23:C6:F9:9F:6D:04:C8:93:94:33:73`

## Stable development signing

The remote runner mounts `/home/kmkuang/codex_build/autojump-signing/android` as the container's Android home. Two consecutive builds, including a clean build, produced the same keystore hash, certificate fingerprint, and APK signature. A same-signature `adb install -r` preserved the first-install time and application preferences.

The local Docker runner uses the named volume `autojump-android-home` for the same reason. Neither development key is a production release key. The production key must live outside Git and have protected backups.

The APK hash is a development baseline only. It changes when source, resources, signing,
or the Android toolchain changes.
