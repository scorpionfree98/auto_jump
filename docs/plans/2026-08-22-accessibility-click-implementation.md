# Accessibility Click Automation Implementation Plan

> **Execution:** Implement task by task, run the specified verification, and keep each task independently reversible.

**Goal:** Refactor AutoJump into a versioned, testable automation engine that opens deeplinks and performs user-configured delayed accessibility clicks.

**Architecture:** Replace polymorphic persisted Android objects with explicit domain specifications and a versioned JSON document. A rule engine produces an execution session, while an accessibility service consumes pending click actions only for the configured target package.

**Tech Stack:** Java 8, Android SDK 34, AndroidX, Gson 2.10.1, JUnit 4, Android AccessibilityService, Docker/Gradle 8.2.

---

### Task 1: Introduce explicit versioned domain models

**Files:**
- Create: `app/src/main/java/com/autojump/app/domain/ConditionType.java`
- Create: `app/src/main/java/com/autojump/app/domain/ActionType.java`
- Create: `app/src/main/java/com/autojump/app/domain/RuleCondition.java`
- Create: `app/src/main/java/com/autojump/app/domain/RuleAction.java`
- Modify: `app/src/main/java/com/autojump/app/bean/Rule.java`
- Test: `app/src/test/java/com/autojump/app/domain/RuleModelTest.java`

**Steps:**
1. Write tests for condition factories, click selector validation, coordinate normalization and defensive list initialization.
2. Run `gradle :app:testDebugUnitTest` on the 120 Docker builder and confirm the tests initially fail.
3. Add explicit condition/action enums and plain serializable models; change `Rule` to store these models.
4. Run the unit tests and confirm they pass.
5. Commit with `refactor: add explicit automation rule models`.

### Task 2: Add versioned persistence and legacy migration

**Files:**
- Create: `app/src/main/java/com/autojump/app/data/RuleDocument.java`
- Create: `app/src/main/java/com/autojump/app/data/RuleJsonCodec.java`
- Create: `app/src/main/java/com/autojump/app/data/RuleRepository.java`
- Modify: `app/src/main/java/com/autojump/app/util/SPUtil.java`
- Test: `app/src/test/java/com/autojump/app/data/RuleJsonCodecTest.java`

**Steps:**
1. Write fixtures for the new schema, old string actions and each inferable legacy condition shape.
2. Verify decoding tests fail before the codec exists.
3. Implement schema version `2`, explicit enum serialization, validation and tolerant legacy migration.
4. Make `SPUtil` delegate rule persistence to `RuleRepository` while retaining old config compatibility.
5. Verify round-trip, migration and malformed-document tests pass.
6. Commit with `refactor: version rule persistence and migrate legacy data`.

### Task 3: Separate condition matching from action execution

**Files:**
- Create: `app/src/main/java/com/autojump/app/engine/ConditionEvaluator.java`
- Create: `app/src/main/java/com/autojump/app/engine/RuleMatcher.java`
- Create: `app/src/main/java/com/autojump/app/automation/ExecutionSession.java`
- Create: `app/src/main/java/com/autojump/app/automation/PendingExecutionStore.java`
- Create: `app/src/main/java/com/autojump/app/automation/AutomationOrchestrator.java`
- Modify: `app/src/main/java/com/autojump/app/util/RuleEngine.java`
- Test: `app/src/test/java/com/autojump/app/engine/RuleMatcherTest.java`

**Steps:**
1. Add tests for AND conditions, priority order, disabled rules, no-action rules and fallback semantics.
2. Implement `ConditionEvaluator` adapters for time, WiFi, Bluetooth, charging and location.
3. Implement deterministic matching with the documented policy: default deeplink only when no enabled rules exist.
4. Implement a persisted execution session containing ordered actions, current index, timestamps and status.
5. Verify engine tests pass and commit with `refactor: separate matching from automation execution`.

### Task 4: Implement the accessibility click executor

**Files:**
- Create: `app/src/main/java/com/autojump/app/automation/AutoJumpAccessibilityService.java`
- Create: `app/src/main/java/com/autojump/app/automation/ClickTargetResolver.java`
- Create: `app/src/main/res/xml/accessibility_service_config.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/autojump/app/automation/ClickTargetResolverTest.java`

**Steps:**
1. Test selector precedence, package filtering, timeout and coordinate fallback decisions using pure resolver inputs.
2. Register an accessibility service restricted to required event types with gesture capability.
3. Consume only active sessions whose target package matches the foreground package.
4. Apply delay, retries and timeout without blocking the main thread; click a matching node or dispatch a normalized-coordinate gesture.
5. Persist success/failure and continue the action queue.
6. Verify unit tests and compile, then commit with `feat: execute delayed accessibility clicks`.

### Task 5: Add one-shot click recording

**Files:**
- Create: `app/src/main/java/com/autojump/app/automation/ClickRecordingStore.java`
- Create: `app/src/main/java/com/autojump/app/ui/ClickActionEditActivity.java`
- Create: `app/src/main/res/layout/activity_click_action_edit.xml`
- Modify: `app/src/main/java/com/autojump/app/automation/AutoJumpAccessibilityService.java`

**Steps:**
1. Add a one-shot recording state with expiry and originating request ID.
2. Capture package, view ID, text/content description and normalized bounds from the next eligible click event.
3. Reject password fields, lockscreen/system UI packages and expired recording requests.
4. Return the captured target to the editor, allow delay/timeout/retry edits, and clear recording state immediately.
5. Compile and manually verify the state flow, then commit with `feat: record click targets from accessibility events`.

### Task 6: Rebuild rule editing around typed actions

**Files:**
- Modify: `app/src/main/java/com/autojump/app/RuleEditActivity.java`
- Modify: `app/src/main/java/com/autojump/app/RuleListActivity.java`
- Modify: `app/src/main/java/com/autojump/app/adapter/RuleAdapter.java`
- Modify: `app/src/main/res/layout/activity_rule_edit.xml`
- Modify: `app/src/main/res/layout/item_action.xml`

**Steps:**
1. Replace condition subclass construction with explicit `RuleCondition` factories.
2. Let users add deeplink, wait and click actions and edit/remove/reorder each action.
3. Clear recycled switch listeners before rebinding and persist the requested checked state directly.
4. Validate times, coordinates, radius, deeplinks and non-empty action lists before saving.
5. Compile and commit with `refactor: rebuild rule editor for typed automation actions`.

### Task 7: Fix entry routing, permission disclosure and operational controls

**Files:**
- Modify: `app/src/main/java/com/autojump/app/MainActivity.java`
- Modify: `app/src/main/java/com/autojump/app/PermissionGuideActivity.java`
- Modify: `app/src/main/java/com/autojump/app/util/ShortcutUtil.java`
- Modify: `app/src/main/res/xml/shortcuts.xml`
- Modify: `app/src/main/res/layout/activity_permission_guide.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Steps:**
1. Route explicit shortcut deeplinks before rule execution and use a package-independent settings intent.
2. Add prominent accessibility disclosure, affirmative consent and a system settings shortcut.
3. Display service status, pending execution status, stop control and recording status.
4. Remove obsolete storage permission claims and align location/Bluetooth permission guidance with actual behavior.
5. Compile and commit with `feat: add safe accessibility setup and execution controls`.

### Task 8: Build, test and document on the 120 server

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: `app/build.gradle`
- Create: `LICENSE`
- Create: `docker/Dockerfile.repack` only if repack support remains documented.

**Steps:**
1. Synchronize the working tree to a temporary directory on `172.31.129.120` without copying `.git` or build outputs.
2. Run unit tests in the Docker builder; fix all failures.
3. Run `:app:assembleDebug`, inspect the APK path and verify the source tree remains clean.
4. Update version, README, accessibility disclosure, setup steps, limitations and migration notes.
5. Run shell syntax checks and the full unit-test/build sequence again.
6. Commit with `docs: document accessibility click automation release`.
