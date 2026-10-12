package com.autojump.app.bean;

import java.io.Serializable;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.domain.RuleCondition;
import com.autojump.app.domain.ShortcutEmoji;
import java.util.ArrayList;
import java.util.List;

/**
 * 跳转规则：条件 + 动作
 */
public class Rule implements Serializable {
    private boolean showInShortcutMenu;
    private int shortcutRank;
    private String shortcutEmoji;
    public boolean isShowInShortcutMenu() { return showInShortcutMenu; }
    public void setShowInShortcutMenu(boolean value) { showInShortcutMenu = value; }
    public int getShortcutRank() { return shortcutRank; }
    public void setShortcutRank(int value) { shortcutRank = value; }
    public String getShortcutEmoji() { return ShortcutEmoji.normalize(shortcutEmoji); }
    public void setShortcutEmoji(String value) { shortcutEmoji = ShortcutEmoji.normalize(value); }
    private String id;              // 唯一标识
    private String name;            // 规则名称
    private int priority;           // 优先级（数字越大越优先）
    private boolean enabled;        // 是否启用
    private boolean requestUnlockBeforeRun; // 锁屏时是否唤醒并交给系统认证
    private List<RuleCondition> conditions;  // 条件列表（AND 关系）
    private List<RuleAction> actions;   // 按顺序执行的动作列表

    public Rule() {
        this("", "");
    }

    public Rule(String id, String name) {
        this.id = id;
        this.name = name;
        this.priority = 0;
        this.enabled = true;
        this.requestUnlockBeforeRun = false;
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

    public boolean isRequestUnlockBeforeRun() { return requestUnlockBeforeRun; }
    public void setRequestUnlockBeforeRun(boolean requestUnlockBeforeRun) {
        this.requestUnlockBeforeRun = requestUnlockBeforeRun;
    }

    public List<RuleCondition> getConditions() {
        if (conditions == null) conditions = new ArrayList<>();
        return conditions;
    }
    public void setConditions(List<RuleCondition> conditions) {
        this.conditions = conditions == null ? new ArrayList<>() : conditions;
    }

    public List<RuleAction> getActions() {
        if (actions == null) actions = new ArrayList<>();
        return actions;
    }
    public void setActions(List<RuleAction> actions) {
        this.actions = actions == null ? new ArrayList<>() : actions;
    }
}
