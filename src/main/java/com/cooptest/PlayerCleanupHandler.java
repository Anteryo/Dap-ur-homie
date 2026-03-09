package com.cooptest;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;


public class PlayerCleanupHandler {

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            UUID uuid = player.getUuid();

            PoseNetworking.poseStates.remove(uuid);

            GrabMechanic.fullCleanup(uuid);

            HighFiveHandler.cleanup(uuid);

            // Clean up push state
            PushInteractionHandler.pushImmunity.remove(uuid);

            ChargedDapHandler.cleanup(uuid);

            // YES WORKIGG
            QTEManager.cancelQTE(uuid);
            DapComboChain.cancelCombo(uuid);

            MahitoTrollHandler.cleanup(uuid);

            FallDapHandler.cleanup(uuid);

            FallCatchHandler.cleanup(uuid);

            ArmPoseTracker.cleanup(uuid);
            MarioJumpHandler.cleanup(uuid);
            DivineFlamCombo.cleanup(uuid);

            ChargedDapHandler.cleanup(uuid);  

            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                if (!other.getUuid().equals(uuid)) {
                    try {
                        ServerPlayNetworking.send(other,
                                new PoseNetworking.AnimStateSyncPayload(uuid, 0)); // 0 = NONE
                    } catch (Exception e) {
                    }
                }
            }
        });
    }
}
