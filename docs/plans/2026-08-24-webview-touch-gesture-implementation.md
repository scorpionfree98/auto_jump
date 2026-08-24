# WebView Touch Gesture Implementation Plan

**Goal:** Make recorded clicks inside WebView applications use a true-device-proven touch gesture instead of a false-positive semantic node click.

**Architecture:** Make valid recorded coordinates select the existing live-center gesture path after selector matching, while coordinate-free actions retain native node clicks. Keep the proven 160 ms duration in a small pure policy.

**Tech Stack:** Java, Android AccessibilityService, JUnit 4, Gradle, Docker, ADB.

---

### Task 1: Test recorded-coordinate routing

**Files:**
- Modify: `app/src/test/java/com/autojump/app/automation/ClickTargetResolverTest.java`
- Create: `app/src/test/java/com/autojump/app/automation/ClickGesturePolicyTest.java`
- Create: `app/src/main/java/com/autojump/app/automation/ClickGesturePolicy.java`

1. Write failing tests that coordinate-backed matches select `GESTURE`, coordinate-free precise matches select `NODE`, and duration is 160 ms.
2. Run the JVM suite on the 120 Docker builder and confirm the old resolver returns `NODE` for coordinate-backed matches.
3. Implement the smallest resolver and duration policy changes that pass the tests.
4. Re-run the JVM suite and confirm all tests pass.

### Task 2: Connect the proven touch duration

**Files:**
- Modify: `app/src/main/java/com/autojump/app/automation/ClickTargetResolver.java`
- Modify: `app/src/main/java/com/autojump/app/automation/AutoJumpAccessibilityService.java`

1. Route every valid recorded coordinate through `GESTURE` after live selector matching.
2. Preserve `NODE` routing for precise actions without coordinates.
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
