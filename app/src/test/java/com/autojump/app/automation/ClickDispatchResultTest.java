package com.autojump.app.automation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClickDispatchResultTest {
    @Test
    public void nodeResultDescribesInstructionDispatch() {
        ClickDispatchResult result = ClickDispatchResult.node();

        assertEquals("DISPATCHED", result.getStatus());
        assertEquals("控件点击指令已发送", result.getMessage());
    }

    @Test
    public void gestureResultDescribesGestureDispatch() {
        ClickDispatchResult result = ClickDispatchResult.gesture();

        assertEquals("DISPATCHED", result.getStatus());
        assertEquals("坐标点击手势已发送", result.getMessage());
    }
}
