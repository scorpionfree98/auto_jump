package com.autojump.app.automation;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ExecutionLogStore {
    public static class Entry {
        private long timestamp;
        private String sessionId;
        private String ruleName;
        private String status;
        private String message;

        Entry(long timestamp, String sessionId, String ruleName, String status, String message) {
            this.timestamp = timestamp;
            this.sessionId = sessionId;
            this.ruleName = ruleName;
            this.status = status;
            this.message = message;
        }

        public long getTimestamp() { return timestamp; }
        public String getSessionId() { return sessionId; }
        public String getRuleName() { return ruleName; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
    }

    private static final Object LOCK = new Object();
    private static final int MAX_ENTRIES = 100;
    private static final String SP_NAME = "automation_execution_sp";
    private static final String KEY_LOGS = "execution_logs";
    private final SharedPreferences preferences;
    private final Gson gson = new Gson();

    public ExecutionLogStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public void append(ExecutionSession session, String status, String message) {
        synchronized (LOCK) {
            List<Entry> entries = getAll();
            entries.add(0, new Entry(System.currentTimeMillis(), session.getId(),
                    session.getRuleName(), status, message));
            if (entries.size() > MAX_ENTRIES) entries = new ArrayList<>(entries.subList(0, MAX_ENTRIES));
            preferences.edit().putString(KEY_LOGS, gson.toJson(entries)).apply();
        }
    }

    public List<Entry> getAll() {
        synchronized (LOCK) {
            String json = preferences.getString(KEY_LOGS, "");
            if (json == null || json.isEmpty()) return new ArrayList<>();
            Type type = new TypeToken<List<Entry>>() {}.getType();
            List<Entry> entries = gson.fromJson(json, type);
            return entries == null ? new ArrayList<>() : entries;
        }
    }

    public void clear() { preferences.edit().remove(KEY_LOGS).apply(); }
}
