package com.cooptest.client;

import java.util.HashMap;
import java.util.UUID;


public class ThrowAnimationTracker {

    private static final HashMap<UUID, Long> throwAnimStartTime = new HashMap<>();
    public static final long THROW_ANIM_DURATION = 300; // milliseconds

    public static void triggerThrowAnimation(UUID playerId) {
        throwAnimStartTime.put(playerId, System.currentTimeMillis());
    }

    public static float getThrowAnimationProgress(UUID playerId) {
        Long startTime = throwAnimStartTime.get(playerId);
        if (startTime == null) {
            return -1f;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= THROW_ANIM_DURATION) {
            throwAnimStartTime.remove(playerId);
            return -1f;
        }

        return (float) elapsed / THROW_ANIM_DURATION;
    }

    public static boolean isAnimating(UUID playerId) {
        return throwAnimStartTime.containsKey(playerId);
    }
}