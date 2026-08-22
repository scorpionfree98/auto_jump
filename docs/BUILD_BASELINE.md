# Build baseline

Verified on 2026-08-22 before adding the accessibility service.

## Source

- Branch: `refactor/accessibility-click`
- Baseline commit: `31df1aa`
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
bash docker/run_remote_gradle.sh :app:testDebugUnitTest
bash docker/run_remote_gradle.sh :app:assembleDebug
```

Results:

- Unit tests: 11 passed, 0 failed.
- APK assembly: successful.
- Package: `com.autojump.app`
- Version: `1.0` (`versionCode` 1)
- Minimum SDK: 21
- Target SDK: 34
- APK size: 6,066,966 bytes
- APK SHA-256: `0c1ef05d501daf66f1f4e180d440874b1d05b0fb787d8e63b9c06355e21ecbf8`

The APK hash is a development baseline only. It changes when source, resources, signing,
or the Android toolchain changes.
