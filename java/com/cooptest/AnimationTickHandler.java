package com.cooptest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.UUID;

public class AnimationTickHandler {

    private static final HashMap<UUID, Integer> animationTicks = new HashMap<>();
    private static final int ACTION_DURATION = 10;  // ~0.5 seconds
    private static final int RETURN_DURATION = 10;  // ~0.5 seconds

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();
                PoseState currentState = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);

                if (currentState == PoseState.PUSH_ACTION) {
                    int ticks = animationTicks.getOrDefault(id, 0) + 1;
                    animationTicks.put(id, ticks);

                    if (ticks >= ACTION_DURATION) {
                        animationTicks.put(id, 0);
                        PoseNetworking.broadcastPoseChange(server, id, PoseState.PUSH_RETURN);
                    }
                } else if (currentState == PoseState.PUSH_RETURN) {
                    int ticks = animationTicks.getOrDefault(id, 0) + 1;
                    animationTicks.put(id, ticks);

                    if (ticks >= RETURN_DURATION) {
                        animationTicks.put(id, 0);
                        PoseNetworking.broadcastPoseChange(server, id, PoseState.PUSH_IDLE);
                    }
                } else {
                    animationTicks.put(id, 0);
                }
            }
        });
    }
}
