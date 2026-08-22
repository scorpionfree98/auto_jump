package com.autojump.app.data;

import com.autojump.app.bean.Rule;
import java.util.ArrayList;
import java.util.List;

public class RuleDocument {
    public static final int CURRENT_SCHEMA_VERSION = 3;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private List<Rule> rules = new ArrayList<>();

    public RuleDocument() {}

    public RuleDocument(List<Rule> rules) {
        setRules(rules);
    }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public List<Rule> getRules() {
        if (rules == null) rules = new ArrayList<>();
        return rules;
    }
    public void setRules(List<Rule> rules) {
        this.rules = rules == null ? new ArrayList<>() : rules;
    }
}
