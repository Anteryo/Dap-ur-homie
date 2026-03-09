package com.cooptest.mixin;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class PlayerEntityPassengerOffsetMixin {


    private static final double GRAB_OFFSET_FORWARD = 0.6;   // How far in front
    private static final double GRAB_OFFSET_UP = 0.8;        // How high above normal ride pos (above head)
    private static final double GRAB_OFFSET_RIGHT = 3.0;     // Left/right offset (0 = centered)
    // ===========================================================

    @Inject(method = "getPassengerRidingPos", at = @At("RETURN"), cancellable = true)
    private void customPassengerPosition(Entity passenger, CallbackInfoReturnable<Vec3d> cir) {
        Entity vehicle = (Entity) (Object) this;

        if (!(vehicle instanceof PlayerEntity holder)) return;
        if (!(passenger instanceof PlayerEntity)) return;

        PoseState holderPose = PoseNetworking.poseStates.getOrDefault(holder.getUuid(), PoseState.NONE);

        if (holderPose == PoseState.GRAB_HOLDING) {
            Vec3d basePos = cir.getReturnValue();
            float yaw = holder.getYaw();
            double yawRad = Math.toRadians(-yaw);

            double offsetForward = GRAB_OFFSET_FORWARD;
            double offsetUp = GRAB_OFFSET_UP;
            double offsetRight = GRAB_OFFSET_RIGHT;

            double rotX = offsetRight * Math.cos(yawRad) + offsetForward * Math.sin(yawRad);
            double rotZ = offsetRight * Math.sin(yawRad) - offsetForward * Math.cos(yawRad);

            Vec3d newPos = new Vec3d(
                    basePos.x + rotX,
                    basePos.y + offsetUp,
                    basePos.z + rotZ
            );
            cir.setReturnValue(newPos);
        }
    }
}