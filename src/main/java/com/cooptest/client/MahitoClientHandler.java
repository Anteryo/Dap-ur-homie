package com.cooptest.client;

import com.cooptest.MahitoTrollHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class MahitoClientHandler {

    private static final Map<UUID, Long> mahitoStartTime = new HashMap<>();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(MahitoTrollHandler.MahitoAnimPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        UUID playerId = payload.playerId();
                        mahitoStartTime.put(playerId, System.currentTimeMillis());

                        if (context.client().world != null) {
                            for (PlayerEntity player : context.client().world.getPlayers()) {
                                if (player.getUuid().equals(playerId)) {
                                    CoopAnimationHandler.playMahitoAnimation(player);
                                    break;
                                }
                            }
                        }
                    });
                }
        );
    }


    public static boolean isBeingMahitod(UUID playerId) {
        return mahitoStartTime.containsKey(playerId);
    }


    public static void cleanup(UUID playerId) {
        mahitoStartTime.remove(playerId);
    }
}