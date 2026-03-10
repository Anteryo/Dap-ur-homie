package com.cooptest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.UUID;

public class LaunchedPlayerTracker {

    private static final HashMap<UUID, Integer> launchedTicks = new HashMap<>();
    private static final int TRAIL_DURATION = 20; // 1 second of particle trail

    public static void markPlayerAsLaunched(UUID playerId) {
        launchedTicks.put(playerId, 0);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var iterator = launchedTicks.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                UUID id = entry.getKey();
                int ticks = entry.getValue();

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player == null) {
                    iterator.remove();
                    continue;
                }

                if (ticks < TRAIL_DURATION) {
                    PoseEffects.playLaunchTrailEffects(player);
                    entry.setValue(ticks + 1);
                } else {
                    iterator.remove();
                }
            }
        });
    }
}
