package com.roadboost.bridge;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BridgeSessionManager {

    private final Map<UUID, BridgeSession> sessions = new HashMap<>();

    public void add(UUID uuid, BridgeSession session) { sessions.put(uuid, session); }
    public BridgeSession get(UUID uuid)               { return sessions.get(uuid); }
    public boolean has(UUID uuid)                     { return sessions.containsKey(uuid); }
    public BridgeSession remove(UUID uuid)            { return sessions.remove(uuid); }
    public Collection<BridgeSession> all()            { return sessions.values(); }
    public Map<UUID, BridgeSession> allMap()          { return sessions; }
}
