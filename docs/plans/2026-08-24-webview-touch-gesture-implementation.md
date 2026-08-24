# WebView Touch Gesture Implementation Plan

**Goal:** Make recorded clicks inside WebView applications use a true-device-proven touch gesture instead of a false-positive semantic node click.

**Architecture:** Add a pure precision policy used by the Android node finder, preserving native node clicks while marking WebView matches imprecise. Reuse the existing resolver and live-center gesture flow, with a 160 ms stroke duration.

**Tech Stack:** Java, Android AccessibilityService, JUnit 4, Gradle, Docker, ADB.

---

### Task 1: Test the precision policy

**Files:**
- Create: `app/src/test/java/com/autojump/app/automation/ClickTargetPrecisionPolicyTest.java`
- Create: `app/src/main/java/com/autojump/app/automation/ClickTargetPrecisionPolicy.java`

1. Write failing tests for native direct nodes, small/large native ancestors, WebView targets, and 160 ms duration.
2. Run the JVM suite on the 120 Docker builder and confirm the missing policy fails compilation.
3. Implement the smallest pure policy that passes the tests.
4. Re-run the JVM suite and confirm all tests pass.

### Task 2: Connect live WebView detection

**Files:**
- Modify: `app/src/main/java/com/autojump/app/automation/AccessibilityNodeFinder.java`
- Modify: `app/src/main/java/com/autojump/app/automation/AutoJumpAccessibilityService.java`

1. Detect a `WebView` class in the source node's ancestor chain.
2. Use the pure policy when calculating `preciseClickTarget`.
3. Replace the hard-coded 80 ms gesture stroke with the proven 160 ms duration.
4. Run all JVM tests, Android Lint, and assemble the debug APK.

### Task 3: True-device business verification

**Files:**
- Modify: `docs/DEVICE_VALIDATION.md`
- Modify: `docs/RELEASE_2.0.0-beta.1.md`

1. Install the rebuilt APK on the Xiaomi 14 Pro without clearing data.
2. Confirm accessibility authorization and the saved rule remain present.
3. Execute the real attendance rule once under the user's explicit authorization.
4. Require both a gesture dispatch log and a visible Feishu business response.
5. Record APK hash, test count, runtime evidence, and commit the implementation.
