package com.autojump.app.data;

import com.autojump.app.bean.Rule;
import com.autojump.app.domain.ActionType;
import com.autojump.app.domain.ConditionType;
import com.autojump.app.domain.RuleAction;
import com.autojump.app.domain.RuleCondition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Encodes schema v3 and tolerantly migrates older rule documents and arrays. */
public class RuleJsonCodec {
    private final Gson gson;

    public RuleJsonCodec() {
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public String encode(List<Rule> rules) {
        return gson.toJson(new RuleDocument(sanitizeRules(rules)));
    }

    public List<Rule> decode(String json) {
        if (json == null || json.trim().isEmpty()) return new ArrayList<>();
        JsonElement root = com.google.gson.JsonParser.parseString(json);
        if (root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            if (!object.has("rules")) throw new JsonParseException("规则文件缺少 rules 字段");
            int schemaVersion = intValue(object, "schemaVersion", 1);
            if (schemaVersion > RuleDocument.CURRENT_SCHEMA_VERSION) {
                throw new JsonParseException("规则文件版本过新: " + schemaVersion);
            }
            if (schemaVersion == RuleDocument.CURRENT_SCHEMA_VERSION) {
                RuleDocument document = gson.fromJson(object, RuleDocument.class);
                return sanitizeRules(document == null ? null : document.getRules());
            }
            return decodeArray(object.getAsJsonArray("rules"));
        }
        if (root.isJsonArray()) return decodeArray(root.getAsJsonArray());
        throw new JsonParseException("规则文件必须是对象或数组");
    }

    private List<Rule> decodeArray(JsonArray array) {
        List<Rule> rules = new ArrayList<>();
        if (array == null) return rules;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            Rule rule = new Rule(stringValue(object, "id", UUID.randomUUID().toString()),
                    stringValue(object, "name", "未命名规则"));
            rule.setPriority(intValue(object, "priority", 0));
            rule.setEnabled(booleanValue(object, "enabled", true));
            rule.setRequestUnlockBeforeRun(booleanValue(object, "requestUnlockBeforeRun", false));
            rule.setConditions(decodeConditions(object.getAsJsonArray("conditions")));
            rule.setActions(decodeActions(object.getAsJsonArray("actions")));
            rules.add(rule);
        }
        return sanitizeRules(rules);
    }

    private List<RuleCondition> decodeConditions(JsonArray array) {
        List<RuleCondition> conditions = new ArrayList<>();
        if (array == null) return conditions;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            ConditionType type = inferConditionType(object);
            if (type == null) continue;
            try {
                switch (type) {
                    case TIME:
                        conditions.add(RuleCondition.time(intArray(object.getAsJsonArray("daysOfWeek")),
                                intValue(object, "startHour", 0), intValue(object, "startMinute", 0),
                                intValue(object, "endHour", 0), intValue(object, "endMinute", 0)));
                        break;
                    case WIFI:
                        conditions.add(RuleCondition.wifi(stringValue(object, "ssid", "")));
                        break;
                    case BLUETOOTH:
                        conditions.add(RuleCondition.bluetooth(stringValue(object, "deviceName", "")));
                        break;
                    case CHARGING:
                        conditions.add(RuleCondition.charging(booleanValue(object, "shouldBeCharging", true)));
                        break;
                    case LOCATION:
                        conditions.add(RuleCondition.location(doubleValue(object, "latitude", 0),
                                doubleValue(object, "longitude", 0), intValue(object, "radiusMeters", 500),
                                stringValue(object, "placeName", "")));
                        break;
                }
            } catch (IllegalArgumentException ignored) {
                // A malformed individual legacy condition is skipped; other rules remain importable.
            }
        }
        return conditions;
    }

    private List<RuleAction> decodeActions(JsonArray array) {
        List<RuleAction> actions = new ArrayList<>();
        if (array == null) return actions;
        for (JsonElement element : array) {
            try {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    actions.add(RuleAction.deeplink(element.getAsString()));
                } else if (element.isJsonObject()) {
                    JsonObject object = element.getAsJsonObject();
                    ActionType type = enumValue(ActionType.class, stringValue(object, "type", "DEEPLINK"));
                    if (type == ActionType.DEEPLINK) {
                        actions.add(RuleAction.deeplink(stringValue(object, "deeplink", "")));
                    } else if (type == ActionType.WAIT) {
                        actions.add(RuleAction.waitFor(longValue(object, "waitDurationMs", 0)));
                    } else if (type == ActionType.CLICK) {
                        RuleAction action = RuleAction.click(
                                stringValue(object, "targetPackage", ""),
                                longValue(object, "delayBeforeMs", 0),
                                longValue(object, "timeoutMs", RuleAction.DEFAULT_TIMEOUT_MS),
                                longValue(object, "retryIntervalMs", RuleAction.DEFAULT_RETRY_MS),
                                nullableString(object, "viewId"), nullableString(object, "text"),
                                nullableString(object, "contentDescription"),
                                nullableDouble(object, "relativeX"), nullableDouble(object, "relativeY"));
                        action.setContinueOnFailure(booleanValue(object, "continueOnFailure", false));
                        actions.add(action);
                    }
                }
            } catch (RuntimeException ignored) {
                // Skip invalid legacy actions instead of making the complete backup unusable.
            }
        }
        return actions;
    }

    private ConditionType inferConditionType(JsonObject object) {
        String rawType = nullableString(object, "type");
        if (rawType != null) {
            String normalized = rawType.replace("Condition", "").toUpperCase();
            try { return ConditionType.valueOf(normalized); } catch (IllegalArgumentException ignored) {}
        }
        if (object.has("daysOfWeek") || object.has("startHour")) return ConditionType.TIME;
        if (object.has("ssid")) return ConditionType.WIFI;
        if (object.has("deviceName")) return ConditionType.BLUETOOTH;
        if (object.has("shouldBeCharging")) return ConditionType.CHARGING;
        if (object.has("latitude") || object.has("longitude")) return ConditionType.LOCATION;
        return null;
    }

    private List<Rule> sanitizeRules(List<Rule> input) {
        List<Rule> output = new ArrayList<>();
        if (input == null) return output;
        for (Rule rule : input) {
            if (rule == null) continue;
            if (rule.getId() == null || rule.getId().trim().isEmpty()) rule.setId(UUID.randomUUID().toString());
            if (rule.getName() == null || rule.getName().trim().isEmpty()) rule.setName("未命名规则");
            rule.getConditions();
            rule.getActions();
            output.add(rule);
        }
        return output;
    }

    private static int[] intArray(JsonArray array) {
        if (array == null || array.size() == 0) return null;
        int[] result = new int[array.size()];
        for (int i = 0; i < array.size(); i++) result[i] = array.get(i).getAsInt();
        return result;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return Enum.valueOf(type, value.toUpperCase());
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        String value = nullableString(object, key);
        return value == null ? fallback : value;
    }

    private static String nullableString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsInt();
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsLong();
    }

    private static double doubleValue(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsDouble();
    }

    private static Double nullableDouble(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsDouble();
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
    }
}
