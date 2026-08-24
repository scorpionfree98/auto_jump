# WebView Touch Gesture Design

## Runtime Evidence

On the Xiaomi 14 Pro, the same visible Feishu attendance target produced three different results:

- `adb shell input tap` at the target center did nothing.
- Accessibility `ACTION_CLICK` returned `true` and AutoJump logged a node dispatch, but Feishu did nothing.
- A 160 ms injected touch at the same center produced Feishu's visible `打卡成功` state.

The coordinate is therefore correct. The failure is caused by choosing a semantic node click for WebView virtual content and by using an input shape that is shorter than the proven working touch.

## Decision

AutoJump will treat a selector match inside an `android.webkit.WebView` ancestry as an imprecise semantic target. When the recorded action contains valid coordinates, `ClickTargetResolver` will consequently select `GESTURE`, using the current live candidate center as before. Native Android controls remain eligible for `ACTION_CLICK`, so the safe self-test and ordinary buttons keep their precise node behavior.

The gesture stroke duration will be 160 ms, matching the successful true-device diagnostic. A WebView match without recorded coordinates will not fall back to a potentially false-positive `ACTION_CLICK`; it will retry until timeout and fail clearly.

## Components and Data Flow

`AccessibilityNodeFinder` detects WebView ancestry while walking the live accessibility tree. A pure `ClickTargetPrecisionPolicy` combines ancestry, source/clickable identity, and clickable-area ratio into the candidate's `preciseClickTarget` flag. `ClickTargetResolver` already maps an imprecise candidate with coordinates to `GESTURE`. `AutoJumpAccessibilityService` obtains the gesture duration from the same policy and dispatches the touch at the live matched center.

## Verification

Pure JVM tests cover native direct nodes, small native ancestors, oversized ancestors, WebView matches, and the 160 ms duration. The full JVM suite, Android Lint, and APK assembly run in the 120 Docker environment. The final APK is installed on the Xiaomi device, then the real attendance rule is executed again under the user's authorization. Passing evidence requires a `坐标点击手势已发送` log plus Feishu's visible business response; dispatch alone is insufficient.
