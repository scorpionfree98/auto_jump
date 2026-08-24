# Truthful Click Results Implementation Plan

**Goal:** Report click dispatch accurately without presenting it as third-party business success.

**Architecture:** Put click-result status and messages in a small pure Java value object so both accessibility callbacks use one definition and JVM tests can verify it. Keep session completion separate from click dispatch evidence, and let the in-app self-test combine a real button callback with the dispatch log.

**Tech Stack:** Java, Android AccessibilityService, JUnit 4, Gradle, Docker.

---

### Task 1: Define dispatch evidence

**Files:**
- Create: `app/src/main/java/com/autojump/app/automation/ClickDispatchResult.java`
- Create: `app/src/test/java/com/autojump/app/automation/ClickDispatchResultTest.java`

1. Write tests asserting `DISPATCHED`, `控件点击指令已发送`, and `坐标点击手势已发送`.
2. Run the focused test and confirm it fails because the class is absent.
3. Implement the immutable result definitions.
4. Re-run the focused test and confirm it passes.

### Task 2: Use truthful results in the service and self-test

**Files:**
- Modify: `app/src/main/java/com/autojump/app/automation/AutoJumpAccessibilityService.java`
- Modify: `app/src/main/java/com/autojump/app/PermissionGuideActivity.java`
- Test: `app/src/test/java/com/autojump/app/automation/ClickDispatchResultTest.java`

1. Pass the node or gesture result into action completion.
2. Append the result's `DISPATCHED` status and transport-level message.
3. Change the terminal message to `规则动作已全部派发`.
4. Allow the safety test to recognize `DISPATCHED`; it must still observe the actual target button callback.
5. Run all JVM tests.

### Task 3: Document and release-verify

**Files:**
- Modify: `docs/DEVICE_VALIDATION.md`
- Modify: `docs/RELEASE_2.0.0-beta.1.md`

1. Record the 10:10 device evidence and explain why later duplicate attempts cannot be judged by dispatch alone.
2. Run JVM tests, lint, and APK assembly in the 120 Docker environment.
3. Install the APK on the Xiaomi device.
4. Verify APK hash, accessibility state, preserved rule data, and zero active sessions without executing the rule.
5. Commit the implementation and verification documents.
