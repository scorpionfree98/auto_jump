package com.autojump.app.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** A deterministic action configured by the user. */
public class RuleAction implements Serializable {
    public static final long DEFAULT_TIMEOUT_MS = 15_000L;
    public static final long DEFAULT_RETRY_MS = 500L;

    private ActionType type;
    private long delayBeforeMs;
    private String deeplink;
    private long waitDurationMs;
    private String targetPackage;
    private String viewId;
    private String text;
    private String contentDescription;
    private Double relativeX;
    private Double relativeY;
    private long timeoutMs = DEFAULT_TIMEOUT_MS;
    private long retryIntervalMs = DEFAULT_RETRY_MS;
    private boolean continueOnFailure;
    private List<TouchPoint> touchTrace;
    private int recordedDisplayWidth;
    private int recordedDisplayHeight;
    private int recordedOrientation;
    private String targetAppVersion;

    private RuleAction(ActionType type) {
        this.type = type;
    }

    public static RuleAction deeplink(String deeplink) {
        RuleAction action = new RuleAction(ActionType.DEEPLINK);
        action.deeplink = requireText(deeplink, "deeplink 不能为空");
        return action;
    }

    public static RuleAction waitFor(long durationMs) {
        if (durationMs < 0) throw new IllegalArgumentException("等待时间不能为负数");
        RuleAction action = new RuleAction(ActionType.WAIT);
        action.waitDurationMs = durationMs;
        return action;
    }

    public static RuleAction click(String targetPackage, long delayBeforeMs, long timeoutMs,
                                   long retryIntervalMs, String viewId, String text,
                                   String contentDescription, Double relativeX, Double relativeY) {
        RuleAction action = new RuleAction(ActionType.CLICK);
        action.targetPackage = requireText(targetPackage, "目标应用包名不能为空");
        action.delayBeforeMs = requireNonNegative(delayBeforeMs, "点击延迟不能为负数");
        action.timeoutMs = requirePositive(timeoutMs, "点击超时必须大于 0");
        action.retryIntervalMs = requirePositive(retryIntervalMs, "重试间隔必须大于 0");
        action.viewId = trimToNull(viewId);
        action.text = trimToNull(text);
        action.contentDescription = trimToNull(contentDescription);
        validateCoordinates(relativeX, relativeY);
        action.relativeX = relativeX;
        action.relativeY = relativeY;
        if (!action.hasSelector() && !action.hasCoordinateFallback()) {
            throw new IllegalArgumentException("点击动作至少需要一个控件特征或坐标");
        }
        return action;
    }

    public static RuleAction recordedTouch(String targetPackage, long delayBeforeMs, long timeoutMs,
                                           int displayWidth, int displayHeight, int orientation,
                                           String targetAppVersion, List<TouchPoint> trace) {
        RuleAction action = new RuleAction(ActionType.RECORDED_TOUCH);
        action.targetPackage = requireText(targetPackage, "目标应用包名不能为空");
        action.delayBeforeMs = requireNonNegative(delayBeforeMs, "点击延迟不能为负数");
        action.timeoutMs = requirePositive(timeoutMs, "点击超时必须大于 0");
        if (displayWidth <= 0 || displayHeight <= 0) {
            throw new IllegalArgumentException("录制屏幕尺寸无效");
        }
        action.recordedDisplayWidth = displayWidth;
        action.recordedDisplayHeight = displayHeight;
        action.recordedOrientation = orientation;
        action.targetAppVersion = trimToNull(targetAppVersion);
        action.touchTrace = validateAndCopyTrace(trace);
        return action;
    }

    private static List<TouchPoint> validateAndCopyTrace(List<TouchPoint> trace) {
        if (trace == null || trace.size() < 2) {
            throw new IllegalArgumentException("真实触摸轨迹至少需要按下和抬起两个点");
        }
        List<TouchPoint> copy = new ArrayList<>(trace.size());
        long previous = -1;
        for (TouchPoint point : trace) {
            if (point == null) throw new IllegalArgumentException("触摸轨迹不能包含空点");
            TouchPoint validated = new TouchPoint(point.getRelativeX(), point.getRelativeY(),
                    point.getElapsedMs());
            if (validated.getElapsedMs() < previous) {
                throw new IllegalArgumentException("触摸轨迹时间必须单调递增");
            }
            previous = validated.getElapsedMs();
            copy.add(validated);
        }
        if (copy.get(copy.size() - 1).getElapsedMs() <= copy.get(0).getElapsedMs()) {
            throw new IllegalArgumentException("触摸轨迹必须具有有效时长");
        }
        return copy;
    }

    private static void validateCoordinates(Double x, Double y) {
        if ((x == null) != (y == null)) {
            throw new IllegalArgumentException("点击坐标必须同时包含 X 和 Y");
        }
        if (x != null && (x < 0 || x > 1 || y < 0 || y > 1)) {
            throw new IllegalArgumentException("相对坐标必须在 0 到 1 之间");
        }
    }

    private static String requireText(String value, String message) {
        String result = trimToNull(value);
        if (result == null) throw new IllegalArgumentException(message);
        return result;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long requireNonNegative(long value, String message) {
        if (value < 0) throw new IllegalArgumentException(message);
        return value;
    }

    private static long requirePositive(long value, String message) {
        if (value <= 0) throw new IllegalArgumentException(message);
        return value;
    }

    public boolean hasSelector() {
        return viewId != null || text != null || contentDescription != null;
    }

    public boolean hasCoordinateFallback() {
        return relativeX != null && relativeY != null;
    }

    public String getDescription() {
        if (type == ActionType.DEEPLINK) return "打开: " + deeplink;
        if (type == ActionType.WAIT) return "等待 " + waitDurationMs + "ms";
        if (type == ActionType.CLICK) {
            String target = viewId != null ? viewId : text != null ? text
                    : contentDescription != null ? contentDescription
                    : String.format(Locale.US, "坐标 %.3f, %.3f", relativeX, relativeY);
            return "等待 " + delayBeforeMs + "ms 后点击 " + target;
        }
        if (type == ActionType.RECORDED_TOUCH) {
            return "等待 " + delayBeforeMs + "ms 后回放真实触摸（"
                    + getTouchDurationMs() + "ms，" + getTouchTrace().size() + "点）";
        }
        return "未知动作";
    }

    public ActionType getType() { return type; }
    public void setType(ActionType type) { this.type = type; }
    public long getDelayBeforeMs() { return delayBeforeMs; }
    public void setDelayBeforeMs(long delayBeforeMs) { this.delayBeforeMs = delayBeforeMs; }
    public String getDeeplink() { return deeplink; }
    public void setDeeplink(String deeplink) { this.deeplink = deeplink; }
    public long getWaitDurationMs() { return waitDurationMs; }
    public void setWaitDurationMs(long waitDurationMs) { this.waitDurationMs = waitDurationMs; }
    public String getTargetPackage() { return targetPackage; }
    public void setTargetPackage(String targetPackage) { this.targetPackage = targetPackage; }
    public String getViewId() { return viewId; }
    public void setViewId(String viewId) { this.viewId = viewId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getContentDescription() { return contentDescription; }
    public void setContentDescription(String contentDescription) { this.contentDescription = contentDescription; }
    public Double getRelativeX() { return relativeX; }
    public void setRelativeX(Double relativeX) { this.relativeX = relativeX; }
    public Double getRelativeY() { return relativeY; }
    public void setRelativeY(Double relativeY) { this.relativeY = relativeY; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public long getRetryIntervalMs() { return retryIntervalMs; }
    public void setRetryIntervalMs(long retryIntervalMs) { this.retryIntervalMs = retryIntervalMs; }
    public boolean isContinueOnFailure() { return continueOnFailure; }
    public void setContinueOnFailure(boolean continueOnFailure) { this.continueOnFailure = continueOnFailure; }
    public List<TouchPoint> getTouchTrace() {
        if (touchTrace == null) return Collections.emptyList();
        return Collections.unmodifiableList(touchTrace);
    }
    public long getTouchDurationMs() {
        List<TouchPoint> trace = getTouchTrace();
        return trace.size() < 2 ? 0 : trace.get(trace.size() - 1).getElapsedMs()
                - trace.get(0).getElapsedMs();
    }
    public int getRecordedDisplayWidth() { return recordedDisplayWidth; }
    public int getRecordedDisplayHeight() { return recordedDisplayHeight; }
    public int getRecordedOrientation() { return recordedOrientation; }
    public String getTargetAppVersion() { return targetAppVersion; }
}
