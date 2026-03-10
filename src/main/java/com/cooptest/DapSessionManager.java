package com.cooptest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.*;


public class DapSessionManager {

    private static final Map<UUID, DapSession> activeSessions = new HashMap<>();

    private static final Set<UUID> playersInSession = new HashSet<>();

   
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DapSessionManager::tick);
        System.out.println("[DapSessionManager] Registered!");
    }

  
    public static DapSession createSession(UUID playerA, UUID playerB, double targetDistance, DapSession.DapType type) {
        // Check if either player already in a session
        if (isInSession(playerA)) {
            System.out.println("[DapSessionManager]  Player A (" + playerA + ") already in session!");
            return null;
        }
        if (isInSession(playerB)) {
            System.out.println("[DapSessionManager]  Player B (" + playerB + ") already in session!");
            return null;
        }

        System.out.println("[DapSessionManager]  Creating session - Distance: " + targetDistance + " | Type: " + type);

        DapSession session = new DapSession(playerA, playerB, targetDistance, type);

        activeSessions.put(playerA, session);
        playersInSession.add(playerA);
        playersInSession.add(playerB);

        System.out.println("[DapSessionManager]  Session created successfully for " + playerA + " and " + playerB);

        return session;
    }

   
    public static DapSession getSession(UUID playerId) {
        DapSession session = activeSessions.get(playerId);
        if (session != null) return session;

        for (DapSession s : activeSessions.values()) {
            if (s.getPlayerBId().equals(playerId)) {
                return s;
            }
        }

        return null;
    }

  
    public static boolean isInSession(UUID playerId) {
        return playersInSession.contains(playerId);
    }

   
    public static void removeSession(UUID playerA) {
        DapSession session = activeSessions.remove(playerA);
        if (session != null) {
            playersInSession.remove(session.getPlayerAId());
            playersInSession.remove(session.getPlayerBId());
            System.out.println("[DapSessionManager] Removed session for " + playerA);
        }
    }

   
    public static void removeSessionForPlayer(UUID playerId) {
        DapSession session = getSession(playerId);
        if (session != null) {
            removeSession(session.getPlayerAId());
        }
    }

    
    private static void tick(MinecraftServer server) {
        // Use iterator for safe removal
        Iterator<Map.Entry<UUID, DapSession>> iterator = activeSessions.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, DapSession> entry = iterator.next();
            DapSession session = entry.getValue();

            // Tick the session
            session.tick(server);

            // Clean up if completed or timed out
            if (session.getTickCount() > 100) {  // 5 seconds timeout
                UUID playerA = session.getPlayerAId();
                UUID playerB = session.getPlayerBId();

                playersInSession.remove(playerA);
                playersInSession.remove(playerB);
                iterator.remove();

                System.out.println("[DapSessionManager] Session timed out and removed");
            }
        }
    }

   
    public static Collection<DapSession> getAllSessions() {
        return activeSessions.values();
    }

  
    public static void clearAll() {
        activeSessions.clear();
        playersInSession.clear();
        System.out.println("[DapSessionManager] Cleared all sessions");
    }
}
