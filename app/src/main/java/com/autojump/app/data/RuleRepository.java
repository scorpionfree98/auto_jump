package com.autojump.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.autojump.app.bean.Rule;
import java.util.ArrayList;
import java.util.List;

public class RuleRepository {
    private static final String TAG = "RuleRepository";
    private static final String SP_NAME = "jump_config_sp";
    private static final String KEY_RULE_LIST = "rule_list";
    private final SharedPreferences preferences;
    private final RuleJsonCodec codec;

    public RuleRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        codec = new RuleJsonCodec();
    }

    public List<Rule> getRules() {
        String json = preferences.getString(KEY_RULE_LIST, "");
        if (json == null || json.trim().isEmpty()) return new ArrayList<>();
        try {
            List<Rule> rules = codec.decode(json);
            if (!json.trim().startsWith("{")) saveRules(rules);
            return rules;
        } catch (RuntimeException error) {
            Log.e(TAG, "读取规则失败，保留原始数据", error);
            return new ArrayList<>();
        }
    }

    public void saveRules(List<Rule> rules) {
        preferences.edit().putString(KEY_RULE_LIST, codec.encode(rules)).apply();
    }

    public String exportRules(List<Rule> rules) {
        return codec.encode(rules);
    }

    public List<Rule> importRules(String json) {
        return StrictRuleImport.decode(json);
    }
}
