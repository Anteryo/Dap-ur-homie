package com.cooptest.mixin;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(PlayerEntity.class)
public abstract class GrabbedPlayerControlMixin {


    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void lockGrabbedMovement(Vec3d movementInput, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        PoseState pose = PoseNetworking.poseStates.getOrDefault(self.getUuid(), PoseState.NONE);

        if (pose == PoseState.GRABBED && self.hasVehicle()) {
            ci.cancel();
        }
    }


    @Inject(method = "tick", at = @At("TAIL"))
    private void lockGrabbedRotation(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        PoseState pose = PoseNetworking.poseStates.getOrDefault(self.getUuid(), PoseState.NONE);

        if (pose == PoseState.GRABBED && self.hasVehicle()) {
            Entity vehicle = self.getVehicle();
            if (vehicle instanceof PlayerEntity holder) {
                float holderYaw = holder.getYaw();
                float holderPitch = holder.getPitch();

                self.setYaw(holderYaw);
                self.prevYaw = holderYaw;
                self.setBodyYaw(holderYaw);

                self.setHeadYaw(holderYaw);
                self.setPitch(holderPitch);
                self.prevPitch = holderPitch;
            }
        }
    }
}