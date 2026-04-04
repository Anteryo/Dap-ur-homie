package com.cooptest.mixin;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class PlayerRidingMixin {

    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void allowGrabRiding(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        System.out.println("[RidingMixin] canAddPassenger called on " + this.getClass().getSimpleName());
        if ((Entity)(Object)this instanceof PlayerEntity holder && passenger instanceof PlayerEntity) {
            PoseState holderPose = PoseNetworking.poseStates.getOrDefault(
                    holder.getUuid(), PoseState.NONE);
            System.out.println("[RidingMixin] canAddPassenger holder pose=" + holderPose);
            if (holderPose == PoseState.GRAB_READY || holderPose == PoseState.GRAB_HOLDING) {
                System.out.println("[RidingMixin] allowing passenger!");
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "couldAcceptPassenger", at = @At("HEAD"), cancellable = true)
    private void allowBeingRidden(CallbackInfoReturnable<Boolean> cir) {
        System.out.println("[RidingMixin] couldAcceptPassenger called on " + this.getClass().getSimpleName());
        if ((Entity)(Object)this instanceof PlayerEntity) {
            cir.setReturnValue(true);
            }
        }
    }