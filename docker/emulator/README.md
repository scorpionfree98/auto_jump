# 120 Android emulator

The server does not expose `/dev/kvm`, so this emulator deliberately runs with
software acceleration. It is intended for install, launch, UI smoke tests and
basic accessibility regression checks. Timing and gesture feel still require a
physical-device pass.

The runtime uses Google's emulator container as its base, replaces the Google
APIs system disk with the smaller Android 9 / API 28 AOSP x86_64 image, and adds
`-accel off`. The AOSP image avoids Google setup services exceeding Android's
watchdog timeout under software-only virtualization.

Validated on 2026-08-22: the debug APK installs successfully, the rule home
shows the default deeplink, and both rule creation entry points open and remain
on `RuleEditActivity` without a fatal exception.

From the project root, build and start on `172.31.129.120`:

```bash
bash docker/run_remote_emulator.sh build
bash docker/run_remote_emulator.sh start
bash docker/run_remote_emulator.sh wait
bash docker/run_remote_emulator.sh install
```

Use ADB inside the container through the helper:

```bash
bash docker/run_remote_emulator.sh status
bash docker/run_remote_emulator.sh adb shell wm size
bash docker/run_remote_emulator.sh adb shell am start -n com.autojump.app/.RuleListActivity
```
