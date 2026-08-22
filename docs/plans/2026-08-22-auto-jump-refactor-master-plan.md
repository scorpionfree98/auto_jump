# AutoJump Full Refactor Master Implementation Plan

> **Execution:** Implement task by task, run the specified verification, and keep each task independently reversible.

**Goal:** Rebuild AutoJump into a reliable, versioned Android automation app that matches rules, opens deeplinks, performs user-authored delayed accessibility clicks, records click targets, and later resumes automation after system-mediated device unlock.

**Architecture:** Keep Java and Android Views for this milestone, but split the app into `domain`, `data`, `engine`, `automation`, and `ui` responsibilities. Build and unit-test in Docker on `172.31.129.120`, return APKs to the local workspace, and use a real HyperOS device for accessibility and lock-screen acceptance tests.

**Tech Stack:** Java 8 source compatibility, JDK 17 build runtime, Android SDK/Build Tools 34, AGP 8.2.x, Gradle Wrapper 8.2, AndroidX, Gson 2.10.1, JUnit 4, AccessibilityService, KeyguardManager, Docker 26.

---

## 0. Fixed decisions and safety boundary

- Local repository: `/Users/scorpion/scripts/personal_tool/auto_jump`
- Development branch: `refactor/accessibility-click`
- Remote build host: SSH alias `172.31.129.120`
- Remote build directory: `/home/kmkuang/codex_build/auto_jump_click`
- Docker image: `autojump-builder-click`
- Gradle cache volume: `autojump-gradle-cache`
- Target device: personal HyperOS phone, normal APK installation.
- Click automation is deterministic and configured by the user; it does not autonomously plan UI actions.
- Click execution is restricted to the configured target package.
- Recording captures only the next eligible click's metadata; it does not save screenshots or full page text.
- Password fields, System UI, launchers, lock screens, and permission dialogs are never recorded or clicked.
- “Automatic unlock” means wake screen, request Android system authentication, and resume after success. The app will not store/replay PIN, pattern, password, or bypass secure keyguard.

## 1. Definition of done

The refactor is complete only when all of the following are true:

1. Existing v1 rules load without crashing and are rewritten as schema v2.
2. New rules survive save, restart, export, import, and app upgrade.
3. A rule can open a deeplink, wait, click one or more targets, and report the result.
4. Click targets support view ID, exact text, content description, and normalized coordinate fallback.
5. Recording mode captures one non-sensitive click and immediately turns itself off.
6. The user can inspect and stop pending automation from the app.
7. With the phone locked, execution requests system unlock and resumes only after successful user authentication.
8. Unit tests, Android compilation, APK assembly, install, and the real-device acceptance matrix pass.
9. README, permission disclosure, migration notes, version number, and release artifact match the implementation.

---

### Task 1: Freeze and verify the development baseline

**Status:** Completed in commits `858484e` and `03af1b3`.

**Files:**
- Existing: `docs/plans/2026-08-22-accessibility-click-design.md`
- Existing: `docs/plans/2026-08-22-accessibility-click-implementation.md`
- Existing tests: `app/src/test/java/com/autojump/app/**`

**Step 1: Verify branch and worktree**

Run locally:

```bash
cd /Users/scorpion/scripts/personal_tool/auto_jump
git branch --show-current
git status --short
git log -3 --oneline
```

Expected:

- Branch is `refactor/accessibility-click`.
- Worktree is clean before the next task.
- `03af1b3` and `858484e` are present.

**Step 2: Preserve the original baseline**

Do not rewrite or force-push `main`. All work remains on the refactor branch. Each later task must end in a buildable commit so rollback is `git revert <commit>`, not history rewriting.

---

### Task 2: Make the build environment reproducible

**Files:**
- Modify: `build.gradle`
- Modify: `app/build.gradle`
- Modify: `gradle.properties`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Modify: `docker/Dockerfile`
- Create: `.java-version`

**Step 1: Align Android build versions**

- Upgrade AGP from `8.1.0` to an `8.2.x` version compatible with compile SDK 34.
- Keep Gradle at `8.2` and JDK at `17`.
- Keep `compileSdk 34`, `targetSdk 34`, and explicit `buildToolsVersion "34.0.0"`.
- Add `android.suppressUnsupportedCompileSdk=34` only if the selected AGP still emits the warning; prefer removing the cause.

**Step 2: Generate the Gradle Wrapper inside Docker**

Run on the remote build host:

```bash
cd /home/kmkuang/codex_build/auto_jump_click
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  autojump-builder-click \
  gradle wrapper --gradle-version 8.2 --distribution-type bin
```

Expected: wrapper scripts, JAR, and properties are created.

**Step 3: Pin the Java runtime**

Write `17` to `.java-version`. Keep Java source/target compatibility at 1.8 unless an Android API requires a later language level.

**Step 4: Verify the wrapper**

Run in Docker:

```bash
./gradlew --version
./gradlew :app:tasks --no-daemon
```

Expected: Gradle 8.2, JVM 17, and Android tasks listed.

**Step 5: Commit**

```bash
git add build.gradle app/build.gradle gradle.properties docker/Dockerfile \
  gradlew gradlew.bat gradle/wrapper .java-version
git commit -m "build: make Android toolchain reproducible"
```

---

### Task 3: Standardize local-to-120 synchronization

**Files:**
- Create: `docker/sync_to_builder.sh`
- Create: `docker/run_remote_gradle.sh`
- Modify: `.gitignore`

**Step 1: Add a safe synchronization script**

The script must:

- Use fixed host `172.31.129.120` and fixed destination `/home/kmkuang/codex_build/auto_jump_click`.
- Refuse to run if the local project root does not contain `settings.gradle` and `app/build.gradle`.
- Create the remote directory before syncing.
- Exclude `.git`, `.gradle`, all `build` directories, APK/AAB files, IDE files, and local secrets.
- Avoid `--delete` until the destination path has been validated exactly.

Core command:

```bash
rsync -az \
  --exclude .git \
  --exclude .gradle \
  --exclude build \
  --exclude '*/build' \
  --exclude '*.apk' \
  --exclude '*.aab' \
  ./ 172.31.129.120:/home/kmkuang/codex_build/auto_jump_click/
```

**Step 2: Add a remote Gradle wrapper**

`docker/run_remote_gradle.sh` should accept only Gradle task arguments, sync first, then run:

```bash
ssh 172.31.129.120 \
  'cd /home/kmkuang/codex_build/auto_jump_click &&
   docker run --rm --network=host
     -v /home/kmkuang/codex_build/auto_jump_click:/workspace
     -v autojump-gradle-cache:/root/.gradle
     -w /workspace autojump-builder-click
     ./gradlew <tasks> --no-daemon'
```

Reject shell metacharacters in user-supplied task names; do not accept arbitrary remote commands.

**Step 3: Smoke test synchronization**

Run:

```bash
bash docker/sync_to_builder.sh
bash docker/run_remote_gradle.sh :app:testDebugUnitTest
```

Expected: remote source matches the local worktree and all existing 11 tests pass.

**Step 4: Commit**

```bash
git add docker/sync_to_builder.sh docker/run_remote_gradle.sh .gitignore
git commit -m "build: add repeatable remote Docker workflow"
```

---

### Task 4: Establish the build and test baseline

**Files:**
- Modify only if necessary: `app/build.gradle`
- Generated remotely: `app/build/test-results/**`
- Generated remotely: `app/build/outputs/apk/debug/app-debug.apk`

**Step 1: Run static checks**

```bash
git diff --check
bash -n docker/*.sh
```

Expected: no whitespace errors and all shell scripts parse.

**Step 2: Run unit tests**

```bash
bash docker/run_remote_gradle.sh :app:testDebugUnitTest
```

Expected: 11 current tests pass before accessibility work begins.

**Step 3: Assemble a baseline APK**

```bash
bash docker/run_remote_gradle.sh :app:assembleDebug
```

Expected: `app/build/outputs/apk/debug/app-debug.apk` exists remotely.

**Step 4: Inspect the APK**

Inside the Docker container run:

```bash
aapt dump badging app/build/outputs/apk/debug/app-debug.apk
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

Record package name, version, min SDK, target SDK, APK size, and SHA-256 in the development log.

---

### Task 5: Implement and test click-target resolution

**Files:**
- Create: `app/src/main/java/com/autojump/app/automation/ClickTargetResolver.java`
- Create: `app/src/main/java/com/autojump/app/automation/ClickResolution.java`
- Test: `app/src/test/java/com/autojump/app/automation/ClickTargetResolverTest.java`

**Step 1: Write failing pure-Java tests**

Cover:

- Target package mismatch returns `IGNORE`.
- View ID has priority over text and description.
- Exact text has priority over coordinate fallback.
- Coordinates are accepted only when both X/Y are in `[0,1]`.
- Before `delayBeforeMs`, resolution returns `WAIT`.
- Before timeout, missing nodes return `RETRY`.
- At timeout, a valid coordinate returns `GESTURE`.
- At timeout without fallback, resolution returns `FAIL`.

Run:

```bash
bash docker/run_remote_gradle.sh :app:testDebugUnitTest \
  --tests com.autojump.app.automation.ClickTargetResolverTest
```

Expected: FAIL because resolver classes do not yet exist.

**Step 2: Implement the resolver as pure logic**

The resolver must not depend directly on `AccessibilityNodeInfo`; it receives normalized candidate metadata. This keeps selector priority and timeout behavior unit-testable.

**Step 3: Run tests**

Expected: all resolver tests pass, followed by the complete unit suite.

**Step 4: Commit**

```bash
git add app/src/main/java/com/autojump/app/automation \
  app/src/test/java/com/autojump/app/automation
git commit -m "test: define accessibility click resolution"
```

---

### Task 6: Implement the AccessibilityService executor

**Files:**
- Create: `app/src/main/java/com/autojump/app/automation/AutoJumpAccessibilityService.java`
- Create: `app/src/main/java/com/autojump/app/automation/AccessibilityNodeFinder.java`
- Create: `app/src/main/java/com/autojump/app/automation/ExecutionLogStore.java`
- Create: `app/src/main/res/xml/accessibility_service_config.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/autojump/app/automation/ExecutionSessionTest.java`

**Step 1: Register the service narrowly**

Manifest requirements:

- Permission: `android.permission.BIND_ACCESSIBILITY_SERVICE`.
- `android:exported="true"` because the system binds the service.
- Metadata references `@xml/accessibility_service_config`.
- No custom app may bind directly because the service permission is system protected.

Service configuration:

- Events: window state changes, window content changes, and view clicked.
- Feedback type: generic.
- `canRetrieveWindowContent="true"`.
- `canPerformGestures="true"`.
- Do not request key filtering, fingerprint gestures, or unrelated capabilities.

**Step 2: Implement node lookup**

Order:

1. `findAccessibilityNodeInfosByViewId`.
2. Exact visible text match.
3. Exact content-description match.
4. Walk upward to the nearest enabled and clickable parent.
5. Never click password nodes or nodes owned by disallowed packages.

**Step 3: Implement non-blocking scheduling**

- Use the service main `Handler`; never call `Thread.sleep`.
- Schedule at `max(session.notBefore, action start + delay)`.
- Retry at `retryIntervalMs` until `timeoutMs`.
- Cancel prior callbacks when the session is stopped or the service disconnects.

**Step 4: Implement coordinate fallback**

- Convert normalized coordinates using the active display bounds.
- Build a short `GestureDescription` tap.
- Record callback success or cancellation.
- Never dispatch on System UI, launcher, lockscreen, package installer, or permission controller.

**Step 5: Advance the action queue**

- Success: advance and execute the next wait/deeplink/click.
- Failure with `continueOnFailure=true`: log and advance.
- Failure otherwise: mark session failed and retain the error for UI display.
- Completed sessions are removed from pending storage but retained in a bounded execution log.

**Step 6: Verify**

```bash
bash docker/run_remote_gradle.sh :app:testDebugUnitTest :app:assembleDebug
```

Expected: tests and APK build pass.

**Step 7: Commit**

```bash
git commit -am "feat: execute delayed accessibility clicks"
```

Include new files explicitly if `-a` does not stage them.

---

### Task 7: Add one-shot click recording

**Files:**
- Create: `app/src/main/java/com/autojump/app/automation/ClickRecordingStore.java`
- Create: `app/src/main/java/com/autojump/app/automation/RecordedClick.java`
- Create: `app/src/test/java/com/autojump/app/automation/ClickRecordingStoreTest.java`
- Modify: `app/src/main/java/com/autojump/app/automation/AutoJumpAccessibilityService.java`

**Step 1: Define recording state**

Store:

- Request ID.
- Creation and expiry timestamps; default expiry five minutes.
- Originating draft/action ID.
- Status: `IDLE`, `ARMED`, `CAPTURED`, `EXPIRED`, `REJECTED`.

**Step 2: Write failing tests**

Cover expiry, one-shot consumption, stale request rejection, password rejection, System UI rejection, and normalized coordinate calculation.

**Step 3: Capture only eligible `TYPE_VIEW_CLICKED` events**

Capture:

- Event package.
- View ID resource name.
- Short exact text only when non-sensitive.
- Content description.
- Node bounds converted to center X/Y in `[0,1]`.

Immediately leave `ARMED` state after success or explicit rejection. Do not keep recording subsequent clicks.

**Step 4: Verify and commit**

```bash
bash docker/run_remote_gradle.sh :app:testDebugUnitTest :app:assembleDebug
git commit -am "feat: record one safe click target"
```

---

### Task 8: Build click-action editing and recording UI

**Files:**
- Create: `app/src/main/java/com/autojump/app/ui/ClickActionEditActivity.java`
- Create: `app/src/main/res/layout/activity_click_action_edit.xml`
- Modify: `app/src/main/java/com/autojump/app/RuleEditActivity.java`
- Modify: `app/src/main/res/layout/activity_rule_edit.xml`
- Modify: `app/src/main/res/layout/item_action.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Step 1: Add typed action choices**

The rule editor offers:

- Open deeplink.
- Wait.
- Click.

Each action can be edited, deleted, moved up, or moved down.

**Step 2: Build the click editor**

Fields:

- Target package, required.
- Delay milliseconds, default 1000.
- Timeout milliseconds, default 15000.
- Retry interval, default 500.
- Optional view ID, text, and content description.
- Optional normalized X/Y fallback.
- Continue-on-failure switch.

Validation must happen before returning a `RuleAction`.

**Step 3: Add “record next click” flow**

1. Confirm the accessibility service is enabled.
2. Show disclosure that exactly one click will be captured.
3. Arm `ClickRecordingStore`.
4. Let the user switch to the target app and click once.
5. On return, populate the captured fields.
6. Ask the user to review before saving.

**Step 4: Preserve unsaved rule edits**

Use saved-instance state or a draft repository so process recreation and external app switching do not discard the rule draft.

**Step 5: Verify and commit**

Build the APK, visually inspect the editor on a device, then commit:

```bash
git commit -am "feat: edit and record click actions"
```

---

### Task 9: Rebuild settings, disclosure, status, and stop controls

**Files:**
- Modify: `app/src/main/java/com/autojump/app/PermissionGuideActivity.java`
- Modify: `app/src/main/res/layout/activity_permission_guide.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/autojump/app/RuleListActivity.java`
- Modify: `app/src/main/res/layout/activity_rule_list.xml`

**Step 1: Add prominent disclosure before accessibility settings**

The in-app disclosure must state:

- The service can inspect visible UI metadata and perform configured clicks.
- Data remains local and is not uploaded.
- Recording captures one click target, not screenshots or passwords.
- The user can stop pending automation and disable the service at any time.

Require an affirmative button before opening `Settings.ACTION_ACCESSIBILITY_SETTINGS`.

**Step 2: Show operational status**

Display:

- Accessibility service enabled/disabled.
- Pending session count and current action.
- Recording armed/captured/expired.
- Last execution result and error.

**Step 3: Add controls**

- Stop current automation.
- Clear completed logs.
- Open accessibility settings.
- Open app permission settings.

**Step 4: Verify and commit**

Run tests/build and commit `feat: add accessibility disclosure and controls`.

---

### Task 10: Finish routing, shortcuts, permissions, and migration UX

**Files:**
- Modify: `app/src/main/java/com/autojump/app/MainActivity.java`
- Modify: `app/src/main/java/com/autojump/app/util/ShortcutUtil.java`
- Modify: `app/src/main/res/xml/shortcuts.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/autojump/app/RuleListActivity.java`
- Test: `app/src/test/java/com/autojump/app/data/RuleJsonCodecTest.java`

**Step 1: Enforce entry priority**

1. Open settings request.
2. Explicit shortcut deeplink.
3. Resume an authenticated pending session.
4. Run the rule engine.
5. Use default deeplink only when no enabled rule exists.

All deeplinks must be parsed, resolvable, and caught on failure.

**Step 2: Make shortcuts package independent**

Avoid hard-coded `com.autojump.app` target package in static shortcut XML. Route settings through exported `MainActivity` extras or generate only dynamic shortcuts at runtime.

**Step 3: Modernize permissions**

- Remove legacy external storage permissions when using SAF/MediaStore.
- Apply appropriate `maxSdkVersion` to legacy Bluetooth permissions.
- Request only `BLUETOOTH_CONNECT` where bonded devices require it.
- Keep location permission contextual to WiFi/location conditions.
- Do not request overlay, media projection, or package-wide visibility for click automation.

**Step 4: Harden import**

- Set a reasonable input-size limit.
- Validate schema and action fields.
- Regenerate duplicate IDs when appending.
- Show migration warnings instead of silently dropping invalid items.

**Step 5: Verify and commit**

Run the full suite and commit `fix: harden routing permissions and rule migration`.

---

### Task 11: Add safe unlock-and-resume support

**Files:**
- Create: `app/src/main/java/com/autojump/app/automation/DeviceGate.java`
- Create: `app/src/main/java/com/autojump/app/automation/UnlockActivity.java`
- Create: `app/src/main/res/layout/activity_unlock.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/autojump/app/automation/AutomationOrchestrator.java`
- Test: `app/src/test/java/com/autojump/app/automation/DeviceGateStateTest.java`

**Step 1: Model the device gate**

States:

- `UNLOCKED`: continue immediately.
- `LOCKED_TRUSTED_OR_INSECURE`: request keyguard dismissal.
- `LOCKED_SECURE`: show system authentication.
- `AUTHENTICATING`: keep the execution session paused.
- `CANCELLED` or `FAILED`: leave the session stopped with a visible reason.

**Step 2: Wake and display safely**

Use activity APIs such as `setShowWhenLocked(true)` and `setTurnScreenOn(true)` where supported. Avoid long-lived wake locks; if a short wake lock is required on older devices, release it in every lifecycle path.

**Step 3: Request system authentication**

Use `KeyguardManager.requestDismissKeyguard` when possible. If the secure keyguard requires credentials, use a system authentication UI; never draw a custom PIN/pattern form and never store credentials.

**Step 4: Resume only after success**

Keep the `ExecutionSession` pending. On successful dismissal/authentication, re-read the session from storage and continue from the same action index. Cancellation must not execute clicks.

**Step 5: Verify and commit**

Unit-test the state machine, compile, then run the lock-screen real-device matrix before committing `feat: resume automation after system unlock`.

---

### Task 12: Prepare real-device validation

**Environment:**

- One HyperOS phone matching the intended deployment version.
- USB or wireless ADB enabled, or manual APK installation if ADB is unavailable.
- A harmless target test app/page with stable controls; do not initially test against payment, settings, or account-security screens.

**Step 1: Return the APK from 120**

```bash
rsync -az \
  172.31.129.120:/home/kmkuang/codex_build/auto_jump_click/app/build/outputs/apk/debug/app-debug.apk \
  /Users/scorpion/scripts/personal_tool/auto_jump/artifacts/autojump-debug.apk
```

Create `artifacts/` locally and keep it ignored by Git.

**Step 2: Install**

If ADB is available:

```bash
adb install -r /Users/scorpion/scripts/personal_tool/auto_jump/artifacts/autojump-debug.apk
```

Expected: `Success`. Otherwise install manually and record the build SHA-256.

**Step 3: Enable accessibility manually**

The app opens Android Accessibility Settings. The user reviews the disclosure and manually enables AutoJump. Do not use ADB to silently grant or enable the service for acceptance testing.

**Step 4: Capture logs**

```bash
adb logcat -c
adb logcat -s MainActivity RuleEngine AutomationOrchestrator AutoJumpAccessibility
```

Do not log deeplink query secrets, passwords, complete UI trees, or captured sensitive text.

---

### Task 13: Execute the real-device acceptance matrix

Run and record each case:

| Case | Expected result |
|---|---|
| Rule condition does not match | No deeplink and no click |
| Deeplink-only rule | Target app opens once |
| Selector click | Correct visible control is clicked after delay |
| Coordinate fallback | Correct coordinate is clicked only in target package |
| Target package mismatch | No click; retry then fail visibly |
| Missing node before timeout | Retry without UI freeze |
| Missing node at timeout | Fallback or explicit failure according to config |
| Two ordered clicks | Both run in order with configured delays |
| Stop button during delay | Pending callbacks are cancelled |
| Service disabled | Rule reports setup required; no silent failure |
| Record next click | Exactly one eligible click is captured |
| Password/system click during recording | Capture is rejected and no sensitive value stored |
| Screen locked, user authenticates | Session resumes from the same action |
| Screen locked, user cancels | No click runs; session remains stopped/failed |
| Rotation/process recreation | Draft and pending session remain consistent |
| Import v1 backup | Migrated rules display and execute correctly |

Any failure blocks release and receives a regression test where feasible.

---

### Task 14: Release hardening and documentation

**Files:**
- Modify: `app/build.gradle`
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: `FEATURES.md`
- Modify: `RULE_ENGINE.md`
- Create: `LICENSE`
- Create: `docs/PRIVACY.md`
- Create: `docs/ACCESSIBILITY_DISCLOSURE.md`
- Decide and either create or remove references to: `docker/Dockerfile.repack`

**Step 1: Version the release**

- Increment `versionCode`.
- Set a version name reflecting the accessibility automation milestone.
- Ensure the About dialog reads values from `BuildConfig`, not hard-coded text.

**Step 2: Document the actual behavior**

Document setup, rule/action semantics, recording, stopping, data storage, unlock limitations, permissions, HyperOS settings, known target-app fragility, and rollback/export instructions.

**Step 3: Resolve broken tooling claims**

Either implement and test `docker/Dockerfile.repack`, or remove `repack_apk.sh` and all documentation claiming it works. Do not ship a dead command.

**Step 4: Run the release gate**

```bash
git diff --check
bash -n docker/*.sh
bash docker/run_remote_gradle.sh clean :app:testDebugUnitTest :app:assembleDebug
```

Expected: clean test/build, no uncommitted source mutation, APK present, and real-device matrix signed off.

**Step 5: Produce artifacts**

Return to local `artifacts/`:

- Debug APK for acceptance.
- Release APK only after signing configuration is provided.
- SHA-256 file.
- Build/test summary.

**Step 6: Final commit**

```bash
git add app/build.gradle README.md CHANGELOG.md FEATURES.md RULE_ENGINE.md \
  LICENSE docs
git commit -m "release: document accessibility automation milestone"
```

Do not merge to `main` or push without explicit user approval.

---

## Execution checkpoints

Execution should pause for review after these batches:

1. **Environment checkpoint:** Tasks 2-4; wrapper, remote scripts, tests, baseline APK.
2. **Click engine checkpoint:** Tasks 5-6; unit-tested service and compiled APK.
3. **Recording/UI checkpoint:** Tasks 7-10; editable click automation and disclosure.
4. **Unlock checkpoint:** Task 11; system-mediated unlock/resume only.
5. **Device checkpoint:** Tasks 12-13; HyperOS acceptance results.
6. **Release checkpoint:** Task 14; final artifacts and merge decision.

At every checkpoint report:

- Commits created.
- Files and behavior changed.
- Exact test/build commands and results.
- Known limitations or failures.
- Whether the worktree is clean.

## Rollback strategy

- Revert individual task commits in reverse order.
- Schema v2 remains readable even if later UI/service work is reverted.
- Before any schema v3 change, add backward-compatibility fixtures and export a v2 backup.
- Never reset or rewrite `main`.
- Do not delete remote build caches as part of normal rollback; they are not source state.
- APK artifacts are identified by commit SHA and SHA-256 so device builds can be traced precisely.
