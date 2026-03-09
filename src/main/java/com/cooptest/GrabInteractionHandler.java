package com.cooptest;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

public class GrabInteractionHandler {

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity clicker)) return ActionResult.PASS;
            if (!(entity instanceof ServerPlayerEntity target)) return ActionResult.PASS;

            PoseState targetPose = PoseNetworking.poseStates.getOrDefault(target.getUuid(), PoseState.NONE);
            if (targetPose != PoseState.GRAB_READY) return ActionResult.PASS;

            PoseState clickerPose = PoseNetworking.poseStates.getOrDefault(clicker.getUuid(), PoseState.NONE);
            if (clickerPose == PoseState.GRAB_HOLDING || clickerPose == PoseState.GRABBED) return ActionResult.PASS;

            if (target.distanceTo(clicker) > 3.0f) return ActionResult.PASS;

            boolean success = GrabMechanic.tryGrab(target, clicker);
            return success ? ActionResult.SUCCESS : ActionResult.PASS;
        });
    }
}
