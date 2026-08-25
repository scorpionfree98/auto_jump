package com.autojump.app.automation;

/** Transport-level evidence that Android accepted and dispatched a click instruction. */
public final class ClickDispatchResult {
    private static final String STATUS = "DISPATCHED";
    private static final ClickDispatchResult NODE =
            new ClickDispatchResult("控件点击指令已发送");
    private static final ClickDispatchResult GESTURE =
            new ClickDispatchResult("坐标点击手势已发送");

    private final String message;

    private ClickDispatchResult(String message) {
        this.message = message;
    }

    public static ClickDispatchResult node() {
        return NODE;
    }

    public static ClickDispatchResult gesture() {
        return GESTURE;
    }

    public static ClickDispatchResult recordedTouch(long durationMs, int pointCount) {
        return recordedTouch(durationMs, pointCount, 1);
    }

    public static ClickDispatchResult recordedTouch(long durationMs, int pointCount,
                                                    int repeatCount) {
        return new ClickDispatchResult("真实触摸轨迹已发送（" + durationMs + "ms，"
                + pointCount + "点，" + Math.max(1, repeatCount) + "次）");
    }

    public String getStatus() {
        return STATUS;
    }

    public String getMessage() {
        return message;
    }
}
