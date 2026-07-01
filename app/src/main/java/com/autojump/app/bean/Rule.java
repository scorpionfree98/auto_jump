package com.autojump.app.bean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 跳转规则：条件 + 动作
 */
public class Rule implements Serializable {
    private String id;              // 唯一标识
    private String name;            // 规则名称
    private int priority;           // 优先级（数字越大越优先）
    private boolean enabled;        // 是否启用
    private List<Condition> conditions;  // 条件列表（AND 关系）
    private List<String> actions;   // 动作列表（deeplink）

    public Rule(String id, String name) {
        this.id = id;
        this.name = name;
        this.priority = 0;
        this.enabled = true;
        this.conditions = new ArrayList<>();
        this.actions = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<Condition> getConditions() { return conditions; }
    public void setConditions(List<Condition> conditions) { this.conditions = conditions; }

    public List<String> getActions() { return actions; }
    public void setActions(List<String> actions) { this.actions = actions; }

    /**
     * 条件基类
     */
    public static abstract class Condition implements Serializable {
        public abstract boolean match(android.content.Context context);  // 子类实现具体匹配逻辑
        public abstract String getDescription();  // 用于界面显示
    }
}
