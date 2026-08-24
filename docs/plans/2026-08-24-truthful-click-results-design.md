# Truthful Click Results Design

## Problem

AutoJump currently writes `点击动作执行成功` after Android accepts a node click or finishes dispatching a gesture. Those callbacks only prove that the input instruction reached the accessibility framework; they do not prove that the target app accepted the operation or completed its business action. A duplicate attendance attempt can therefore look successful in AutoJump even when no new attendance record is created.

## Decision

Keep the existing automation lifecycle, but separate transport evidence from business evidence. A node action will record `控件点击指令已发送`; a gesture action will record `坐标点击手势已发送`. Both use the new `DISPATCHED` log status. When the last action leaves the queue, the terminal record will say `规则动作已全部派发`, while the internal session can still reach `COMPLETED` because the automation pipeline has finished its work.

The built-in safety test remains stronger than a normal rule: it observes the dedicated test button's real click callback and also requires a matching `DISPATCHED` or `COMPLETED` execution record. It can therefore continue to report that accessibility recognition and clicking work without claiming anything about a third-party app.

## Alternatives Considered

1. Keep the current success wording. Rejected because it overstates the available evidence.
2. Inspect Feishu pages to confirm attendance. Rejected because it couples AutoJump to one app, depends on unstable UI text, and could expose workplace data.
3. Treat dispatch as a failure unless the user confirms it. Rejected because it would stall every generic automation and add unnecessary interaction.

## Verification

Unit tests will lock down status and wording for node and gesture dispatch, plus self-test acceptance of `DISPATCHED`. The full JVM suite, Android lint, and debug build will run in the existing Docker build environment. The APK will then be installed on the Xiaomi device and checked without starting another real attendance action.
