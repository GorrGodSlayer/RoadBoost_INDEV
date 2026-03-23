package com.roadboost.managers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple in-memory store of active recording sessions.
 */
public class SessionManager {

    private final Map<UUID, RecordingSession> sessions = new HashMap<>();

    public void addSession(UUID uuid, RecordingSession session) {
        sessions.put(uuid, session);
    }

    public RecordingSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public RecordingSession removeSession(UUID uuid) {
        return sessions.remove(uuid);
    }

    public Map<UUID, RecordingSession> getAllSessions() {
        return sessions;
    }
}
