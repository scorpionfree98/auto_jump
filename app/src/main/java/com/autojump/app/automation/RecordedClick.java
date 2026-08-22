package com.autojump.app.automation;

public class RecordedClick {
    private String requestId;
    private String targetPackage;
    private String viewId;
    private String text;
    private String contentDescription;
    private double relativeX;
    private double relativeY;
    private long capturedAt;

    public RecordedClick() {}

    public RecordedClick(String requestId, String targetPackage, String viewId, String text,
                         String contentDescription, double relativeX, double relativeY,
                         long capturedAt) {
        this.requestId = requestId;
        this.targetPackage = targetPackage;
        this.viewId = viewId;
        this.text = text;
        this.contentDescription = contentDescription;
        this.relativeX = relativeX;
        this.relativeY = relativeY;
        this.capturedAt = capturedAt;
    }

    public String getRequestId() { return requestId; }
    public String getTargetPackage() { return targetPackage; }
    public String getViewId() { return viewId; }
    public String getText() { return text; }
    public String getContentDescription() { return contentDescription; }
    public double getRelativeX() { return relativeX; }
    public double getRelativeY() { return relativeY; }
    public long getCapturedAt() { return capturedAt; }
}
