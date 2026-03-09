package com.cooptest.client;

import com.cooptest.FallDapHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class FallDapClientHandler {

    private static final Map<UUID, Integer> fallDapStates = new HashMap<>();

    public static final int STATE_NONE = 0;
    public static final int STATE_CHARGING = 1;
    public static final int STATE_FALLING = 2;
    public static final int STATE_HIT = 3;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FallDapHandler.FallDapAnimPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        UUID playerId = payload.playerId();
                        int state = payload.state();

                        fallDapStates.put(playerId, state);

                        MinecraftClient client = context.client();
                        if (client.world != null) {
                            for (PlayerEntity player : client.world.getPlayers()) {
                                if (player.getUuid().equals(playerId)) {
                                    triggerFallDapAnimation(player, state);
                                    break;
                                }
                            }
                        }
                    });
                }
        );

        // Handle squash animation payload
        ClientPlayNetworking.registerGlobalReceiver(FallDapHandler.SquashAnimPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        UUID playerId = payload.playerId();

                        // Find the player and trigger squash animation
                        MinecraftClient client = context.client();
                        if (client.world != null) {
                            for (PlayerEntity player : client.world.getPlayers()) {
                                if (player.getUuid().equals(playerId)) {
                                    CoopAnimationHandler.playSquashed(player);
                                    break;
                                }
                            }
                        }
                    });
                }
        );
    }

    private static void triggerFallDapAnimation(PlayerEntity player, int state) {
        UUID playerId = player.getUuid();

        switch (state) {
            case STATE_CHARGING -> CoopAnimationHandler.playFallDapChargeStart(player);
            case STATE_FALLING -> CoopAnimationHandler.playFallDapFalling(player);
            case STATE_HIT -> CoopAnimationHandler.playFallDapHit(player);
            case STATE_NONE -> {
                // Clear fall dap state and let dap_charge_idle take over
                fallDapStates.remove(playerId);
                // Don't stop animation here - let the next animation command take over
            }
        }
    }

    /**
     * Get fall dap state for a player
     */
    public static int getFallDapState(UUID playerId) {
        return fallDapStates.getOrDefault(playerId, STATE_NONE);
    }

    /**
     * Check if player is in fall dap mode
     */
    public static boolean isInFallDap(UUID playerId) {
        int state = fallDapStates.getOrDefault(playerId, STATE_NONE);
        return state == STATE_CHARGING || state == STATE_FALLING;
    }

    /**
     * Cleanup when player disconnects
     */
    public static void cleanup(UUID playerId) {
        fallDapStates.remove(playerId);
    }
}