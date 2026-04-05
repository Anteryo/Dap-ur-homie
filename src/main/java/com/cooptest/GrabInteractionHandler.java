package com.cooptest;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

public class GrabInteractionHandler {

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity clicker)) return ActionResult.PASS;
            if (!(entity instanceof ServerPlayerEntity target)) return ActionResult.PASS;


            PoseState targetEntityPose = PoseNetworking.poseStates.getOrDefault(target.getUuid(), PoseState.NONE);
            if (targetEntityPose != PoseState.GRAB_READY) return ActionResult.PASS;

            PoseState clickerStatePose = PoseNetworking.poseStates.getOrDefault(clicker.getUuid(), PoseState.NONE);
            if (clickerStatePose == PoseState.GRAB_HOLDING || clickerStatePose == PoseState.GRABBED)
                return ActionResult.PASS;

            if (target.distanceTo(clicker) > 3.0f) return ActionResult.PASS;

            boolean success = GrabMechanic.tryGrab(clicker, target);
            System.out.println("[GrabDebug] tryGrab result=" + success);
            return success ? ActionResult.SUCCESS : ActionResult.PASS;
        });
    }
}