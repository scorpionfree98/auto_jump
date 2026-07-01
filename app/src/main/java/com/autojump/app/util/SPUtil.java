package com.autojump.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.autojump.app.bean.ConfigBean;
import com.autojump.app.bean.Rule;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class SPUtil {
    private static final String SP_NAME = "jump_config_sp";
    private static final String KEY_CONFIG_LIST = "config_list";
    private static final String KEY_RULE_LIST = "rule_list";
    private static final String KEY_DEFAULT_LINK = "default_link";
    private static final String DEFAULT_DEEP_LINK = "";

    private static SharedPreferences getSP(Context context) {
        return context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    // 初始化默认链接
    public static void initDefaultLink(Context context) {
        if (getSP(context).getString(KEY_DEFAULT_LINK, "").isEmpty()) {
            getSP(context).edit().putString(KEY_DEFAULT_LINK, DEFAULT_DEEP_LINK).apply();
        }
    }

    // 获取默认启动链接
    public static String getDefaultLink(Context context) {
        return getSP(context).getString(KEY_DEFAULT_LINK, DEFAULT_DEEP_LINK);
    }

    // 设置默认启动链接
    public static void setDefaultLink(Context context, String link) {
        getSP(context).edit().putString(KEY_DEFAULT_LINK, link).apply();
    }

    // 保存全部跳转配置列表
    public static void saveConfigList(Context context, List<ConfigBean> list) {
        Gson gson = new Gson();
        String json = gson.toJson(list);
        getSP(context).edit().putString(KEY_CONFIG_LIST, json).apply();
    }

    // 读取配置列表
    public static List<ConfigBean> getConfigList(Context context) {
        String json = getSP(context).getString(KEY_CONFIG_LIST, "");
        List<ConfigBean> list = new ArrayList<>();
        if (!json.isEmpty()) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<ConfigBean>>() {}.getType();
            List<ConfigBean> parsed = gson.fromJson(json, type);
            if (parsed != null) {
                list = parsed;
            }
        }
        return list;
    }

    // ========== 规则列表存储 ==========

    // 保存规则列表
    public static void saveRuleList(Context context, List<Rule> list) {
        Gson gson = new Gson();
        String json = gson.toJson(list);
        getSP(context).edit().putString(KEY_RULE_LIST, json).apply();
    }

    // 读取规则列表
    public static List<Rule> getRuleList(Context context) {
        String json = getSP(context).getString(KEY_RULE_LIST, "");
        List<Rule> list = new ArrayList<>();
        if (!json.isEmpty()) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<Rule>>() {}.getType();
            List<Rule> parsed = gson.fromJson(json, type);
            if (parsed != null) {
                list = parsed;
            }
        }
        return list;
    }
}
