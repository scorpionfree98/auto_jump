#!/usr/bin/env bash
set -euo pipefail
# Run inside the emulator container (or against its explicit ADB server).
# Build with :app:assembleDebug :app:assembleDebugAndroidTest
# -PruntimeTestRunner=cn.local.jiojio.QueueRuntimeInstrumentation
# -PqueueRuntimeOnly=true on x86 emulators (no map/native-library acceptance).
: "${AUTOMATION_ADB_BIN:?Set the emulator adb binary path}"
: "${AUTOMATION_ADB_HOST:?Set its adb server host}"
: "${AUTOMATION_ADB_PORT:?Set its adb server port}"
: "${AUTOMATION_DEVICE_SERIAL:?Set the emulator serial explicitly}"
: "${AUTOMATION_APP_APK:?Set app-debug.apk path}"
: "${AUTOMATION_TEST_APK:?Set app-debug-androidTest.apk path}"
: "${AUTOMATION_TEST_PACKAGE:=cn.local.toolkit.runtime.test}"
if [[ "$AUTOMATION_TEST_PACKAGE" != cn.local.toolkit.runtime.test ]]; then
  echo 'Runtime tests must target the isolated runtime application ID.' >&2
  exit 2
fi
automation_adb=("$AUTOMATION_ADB_BIN" -H "$AUTOMATION_ADB_HOST" -P "$AUTOMATION_ADB_PORT" -s "$AUTOMATION_DEVICE_SERIAL")
automation_is_emulator=$("${automation_adb[@]}" shell getprop ro.kernel.qemu | tr -d '\r')
if [[ "$automation_is_emulator" != 1 ]]; then
  echo 'Refusing to install a debug test target on a physical phone.' >&2
  exit 2
fi
"${automation_adb[@]}" install -r "$AUTOMATION_APP_APK"
"${automation_adb[@]}" install -r "$AUTOMATION_TEST_APK"
# No uninstall, data clearing or production configuration injection.
automation_result=$("${automation_adb[@]}" shell am instrument -r -w "$AUTOMATION_TEST_PACKAGE/cn.local.jiojio.QueueRuntimeInstrumentation" | tr -d '\r')
printf '%s\n' "$automation_result"
if [[ "$automation_result" != *"INSTRUMENTATION_CODE: -1"* || "$automation_result" != *"PASS "* ]]; then
  echo 'Runtime acceptance did not report success.' >&2
  exit 1
fi
