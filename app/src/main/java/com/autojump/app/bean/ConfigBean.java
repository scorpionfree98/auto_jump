package com.autojump.app.bean;

import java.io.Serializable;

public class ConfigBean implements Serializable {
    private String id;          // 唯一标识
    private String name;        // 快捷显示名称
    private String deepLink;    // 跳转deeplink链接

    public ConfigBean(String id, String name, String deepLink) {
        this.id = id;
        this.name = name;
        this.deepLink = deepLink;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDeepLink() { return deepLink; }
    public void setDeepLink(String deepLink) { this.deepLink = deepLink; }
}
