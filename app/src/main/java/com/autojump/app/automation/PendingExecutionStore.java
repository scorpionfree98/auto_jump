package com.autojump.app.automation;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PendingExecutionStore {
    private static final Object LOCK = new Object();
    private static final String SP_NAME = "automation_execution_sp";
    private static final String KEY_SESSIONS = "pending_sessions";
    private final SharedPreferences preferences;
    private final Gson gson = new Gson();

    public PendingExecutionStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public List<ExecutionSession> getAll() {
        synchronized (LOCK) {
            String json = preferences.getString(KEY_SESSIONS, "");
            if (json == null || json.isEmpty()) return new ArrayList<>();
            Type type = new TypeToken<List<ExecutionSession>>() {}.getType();
            List<ExecutionSession> result = gson.fromJson(json, type);
            return result == null ? new ArrayList<>() : result;
        }
    }

    public void upsert(ExecutionSession session) {
        synchronized (LOCK) {
            List<ExecutionSession> sessions = getAll();
            for (int i = 0; i < sessions.size(); i++) {
                if (sessions.get(i).getId().equals(session.getId())) {
                    sessions.set(i, session);
                    save(sessions);
                    return;
                }
            }
            sessions.add(session);
            save(sessions);
        }
    }

    public ExecutionSession findById(String sessionId) {
        if (sessionId == null) return null;
        for (ExecutionSession session : getAll()) {
            if (sessionId.equals(session.getId())) return session;
        }
        return null;
    }

    public void remove(String sessionId) {
        synchronized (LOCK) {
            List<ExecutionSession> sessions = getAll();
            for (int i = sessions.size() - 1; i >= 0; i--) {
                if (sessions.get(i).getId().equals(sessionId)) sessions.remove(i);
            }
            save(sessions);
        }
    }

    public void clear() {
        synchronized (LOCK) { preferences.edit().remove(KEY_SESSIONS).apply(); }
    }

    private void save(List<ExecutionSession> sessions) {
        preferences.edit().putString(KEY_SESSIONS, gson.toJson(sessions)).commit();
    }
    public long nextSequence() {
        synchronized (LOCK) {
            long next = preferences.getLong("queue_sequence", 0L) + 1;
            preferences.edit().putLong("queue_sequence", next).commit();
            return next;
        }
    }
}
