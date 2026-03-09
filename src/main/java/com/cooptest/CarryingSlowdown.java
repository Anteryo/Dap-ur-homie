package com.cooptest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;


public class CarryingSlowdown {

    public static final float CARRY_SPEED_MULTIPLIER = 0.6f;

    private static final double SLOWDOWN_AMOUNT = -(1.0 - CARRY_SPEED_MULTIPLIER) * 0.1;

    private static final Identifier MODIFIER_ID = Identifier.of("cooptest", "carrying_slowdown");

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                updateSlowdown(player);
            }
        });
    }

    private static void updateSlowdown(ServerPlayerEntity player) {
        PoseState pose = PoseNetworking.poseStates.getOrDefault(player.getUuid(), PoseState.NONE);
        boolean isCarrying = pose == PoseState.GRAB_HOLDING;

        EntityAttributeInstance speedAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr == null) return;

        EntityAttributeModifier existingModifier = speedAttr.getModifier(MODIFIER_ID);

        if (isCarrying) {
            // Add slowdown if not present
            if (existingModifier == null) {
                speedAttr.addTemporaryModifier(new EntityAttributeModifier(
                        MODIFIER_ID,
                        SLOWDOWN_AMOUNT,
                        EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
        } else {
            if (existingModifier != null) {
                speedAttr.removeModifier(MODIFIER_ID);
            }
        }
    }
}