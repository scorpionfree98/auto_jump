package com.autojump.app.automation;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import java.util.UUID;

public class ClickRecordingStore {
    public enum Status { IDLE, ARMED, CAPTURED, EXPIRED, REJECTED }

    public static class State {
        private Status status = Status.IDLE;
        private String requestId;
        private String draftActionId;
        private long createdAt;
        private long expiresAt;
        private RecordedClick recordedClick;
        private String rejectionReason;

        public Status getStatus() { return status == null ? Status.IDLE : status; }
        public String getRequestId() { return requestId; }
        public String getDraftActionId() { return draftActionId; }
        public long getCreatedAt() { return createdAt; }
        public long getExpiresAt() { return expiresAt; }
        public RecordedClick getRecordedClick() { return recordedClick; }
        public String getRejectionReason() { return rejectionReason; }
    }

    public static final long DEFAULT_TTL_MS = 5 * 60 * 1000L;
    private static final Object LOCK = new Object();
    private static final String SP_NAME = "automation_execution_sp";
    private static final String KEY_STATE = "click_recording_state";
    private final SharedPreferences preferences;
    private final Gson gson = new Gson();

    public ClickRecordingStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public State arm(String draftActionId, long now) {
        synchronized (LOCK) {
            State state = new State();
            state.status = Status.ARMED;
            state.requestId = UUID.randomUUID().toString();
            state.draftActionId = draftActionId;
            state.createdAt = now;
            state.expiresAt = now + DEFAULT_TTL_MS;
            save(state);
            return state;
        }
    }

    public State get() {
        synchronized (LOCK) {
            String json = preferences.getString(KEY_STATE, "");
            if (json == null || json.isEmpty()) return new State();
            State state = gson.fromJson(json, State.class);
            return state == null ? new State() : state;
        }
    }

    public void capture(RecordedClick click) {
        synchronized (LOCK) {
            State state = get();
            if (state.getStatus() != Status.ARMED || click == null
                    || !state.getRequestId().equals(click.getRequestId())) return;
            state.status = Status.CAPTURED;
            state.recordedClick = click;
            save(state);
        }
    }

    public void reject(String requestId, Status status, String reason) {
        synchronized (LOCK) {
            State state = get();
            if (state.getStatus() != Status.ARMED || requestId == null
                    || !requestId.equals(state.getRequestId())) return;
            state.status = status == Status.EXPIRED ? Status.EXPIRED : Status.REJECTED;
            state.rejectionReason = reason;
            save(state);
        }
    }

    public void clear() { preferences.edit().remove(KEY_STATE).apply(); }

    private void save(State state) {
        preferences.edit().putString(KEY_STATE, gson.toJson(state)).apply();
    }
}
