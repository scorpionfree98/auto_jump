package com.autojump.app.automation;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import java.util.UUID;

public class ClickRecordingStore {
    public enum Status { IDLE, WAITING_START, ARMED, CAPTURED, EXPIRED, REJECTED }

    public static class State {
        private Status status = Status.IDLE;
        private String requestId;
        private String draftActionId;
        private long createdAt;
        private long startedAt;
        private long expiresAt;
        private String deeplink;
        private String targetPackage;
        private RecordedClick recordedClick;
        private String rejectionReason;

        public Status getStatus() { return status == null ? Status.IDLE : status; }
        public String getRequestId() { return requestId; }
        public String getDraftActionId() { return draftActionId; }
        public long getCreatedAt() { return createdAt; }
        public long getStartedAt() { return startedAt; }
        public long getExpiresAt() { return expiresAt; }
        public String getDeeplink() { return deeplink; }
        public String getTargetPackage() { return targetPackage; }
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

    public State prepare(String draftActionId, String deeplink, long now) {
        synchronized (LOCK) {
            State state = new State();
            state.status = Status.WAITING_START;
            state.requestId = UUID.randomUUID().toString();
            state.draftActionId = draftActionId;
            state.createdAt = now;
            state.deeplink = deeplink;
            state.expiresAt = now + DEFAULT_TTL_MS;
            save(state);
            return state;
        }
    }

    public State bindTargetPackage(String requestId, String targetPackage) {
        synchronized (LOCK) {
            State state = get();
            if (state.getStatus() != Status.WAITING_START || requestId == null
                    || !requestId.equals(state.getRequestId())) return state;
            if (state.targetPackage == null || state.targetPackage.trim().isEmpty()) {
                state.targetPackage = targetPackage;
                save(state);
            }
            return state;
        }
    }

    public State start(String requestId, String targetPackage, long now) {
        synchronized (LOCK) {
            State state = get();
            if (state.getStatus() != Status.WAITING_START || requestId == null
                    || !requestId.equals(state.getRequestId())
                    || state.getTargetPackage() == null
                    || !state.getTargetPackage().equals(targetPackage)) return state;
            state.status = Status.ARMED;
            state.startedAt = now;
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
            if ((state.getStatus() != Status.ARMED
                    && state.getStatus() != Status.WAITING_START) || requestId == null
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
