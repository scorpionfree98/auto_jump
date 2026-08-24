# WebView Touch Gesture Design

## Runtime Evidence

On the Xiaomi 14 Pro, the same visible Feishu attendance target produced three different results:

- `adb shell input tap` at the target center did nothing.
- Accessibility `ACTION_CLICK` returned `true` and AutoJump logged a node dispatch, but Feishu did nothing.
- A 160 ms injected touch at the same center produced Feishu's visible `打卡成功` state.

The coordinate is therefore correct. The failure is caused by choosing a semantic node click for WebView virtual content and by using an input shape that is shorter than the proven working touch.

## Decision

AutoJump will use the presence of valid recorded coordinates as the reliable boundary. When an action has coordinates, selectors still find and disambiguate the live target, but `ClickTargetResolver` always selects `GESTURE` and uses the current candidate center. Actions without recorded coordinates remain eligible for precise `ACTION_CLICK`, so the safe self-test and manually configured native IDs keep their semantic-node behavior.

The first implementation attempted to detect a `WebView` ancestor in the accessibility tree. True-device regression disproved that assumption: Feishu did not expose a stable WebView ancestry to the service and the rule still selected `NODE`. The coordinate-based boundary avoids depending on unstable third-party accessibility structure and matches the user's recorded-touch intent directly.

The gesture stroke duration is 160 ms, matching the successful true-device diagnostic.

## Components and Data Flow

`AccessibilityNodeFinder` continues to supply selector matches and current centers. `ClickTargetResolver` first chooses the best ID/text/description match, then selects `GESTURE` whenever the action has valid recorded coordinates. `AutoJumpAccessibilityService` obtains the 160 ms duration from a pure `ClickGesturePolicy` and dispatches the touch at the live matched center.

## Verification

Pure JVM tests cover selector priority, duplicate-text disambiguation, coordinate-to-gesture routing, coordinate-free node routing, and the 160 ms duration. The full JVM suite, Android Lint, and APK assembly run in the 120 Docker environment. The final APK is installed on the Xiaomi device, then the real attendance rule is executed again under the user's authorization. Passing evidence requires a `坐标点击手势已发送` log plus Feishu's visible business response; dispatch alone is insufficient.
