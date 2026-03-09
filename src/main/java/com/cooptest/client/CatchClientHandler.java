package com.cooptest.client;

import com.cooptest.FallCatchHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class CatchClientHandler {

    private static final Map<UUID, Long> catcherAnimStart = new HashMap<>();
    private static final Map<UUID, Long> caughtAnimStart = new HashMap<>();

    // Animation durations in ms
    private static final long CATCHER_ANIM_DURATION = 500;  // Arms pull in
    private static final long CAUGHT_ANIM_DURATION = 400;   // Brief caught pose

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FallCatchHandler.CatchAnimPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        long now = System.currentTimeMillis();
                        catcherAnimStart.put(payload.catcherId(), now);
                        caughtAnimStart.put(payload.caughtId(), now);

                        // Play PAL catch animation for catcher
                        if (context.client().world != null) {
                            for (net.minecraft.entity.player.PlayerEntity player : context.client().world.getPlayers()) {
                                if (player.getUuid().equals(payload.catcherId())) {
                                    CoopAnimationHandler.playCatchAnimation(player);
                                    break;
                                }
                            }
                        }
                    });
                }
        );
    }


    public static float getCatcherAnimProgress(UUID playerId) {
        Long startTime = catcherAnimStart.get(playerId);
        if (startTime == null) return -1f;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > CATCHER_ANIM_DURATION) {
            catcherAnimStart.remove(playerId);
            return -1f;
        }

        return (float) elapsed / CATCHER_ANIM_DURATION;
    }


    public static float getCaughtAnimProgress(UUID playerId) {
        Long startTime = caughtAnimStart.get(playerId);
        if (startTime == null) return -1f;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > CAUGHT_ANIM_DURATION) {
            caughtAnimStart.remove(playerId);
            return -1f;
        }

        return (float) elapsed / CAUGHT_ANIM_DURATION;
    }
}