package com.autojump.app.shizuku;

interface IShizukuAccessibilityBridge {
    String restoreAccessibility(String componentName);
    void destroy();
}
