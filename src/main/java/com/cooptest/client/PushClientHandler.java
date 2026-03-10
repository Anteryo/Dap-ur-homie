package com.cooptest.client;

import com.cooptest.PushInteractionHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class PushClientHandler {

    private static final Map<UUID, Long> pushAnimStart = new HashMap<>();

    private static final long PUSH_ANIM_DURATION = 400;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PushInteractionHandler.PushAnimPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        pushAnimStart.put(payload.playerId(), System.currentTimeMillis());

                        MinecraftClient client = context.client();

                        if (client.world != null) {
                            for (net.minecraft.entity.player.PlayerEntity player : client.world.getPlayers()) {
                                if (player.getUuid().equals(payload.playerId())) {
                                    CoopAnimationHandler.playPushAnimation(player);
                                    break;
                                }
                            }
                        }

                        if (client.player != null && client.player.getUuid().equals(payload.playerId())) {
                            client.player.swingHand(Hand.MAIN_HAND);
                        }
                    });
                }
        );
    }


    public static float getPushAnimProgress(UUID playerId) {
        Long startTime = pushAnimStart.get(playerId);
        if (startTime == null) return -1f;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > PUSH_ANIM_DURATION) {
            pushAnimStart.remove(playerId);
            return -1f;
        }

        return (float) elapsed / PUSH_ANIM_DURATION;
    }


    public static void cleanup(UUID playerId) {
        pushAnimStart.remove(playerId);
    }
}