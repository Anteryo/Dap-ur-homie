package com.cooptest.mixin.client;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.UUID;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @Unique
    private static final HashMap<UUID, Boolean> matrixPushed = new HashMap<>();

    // Track the yaw direction when player was grabbed/thrown
    @Unique
    private static final HashMap<UUID, Float> lockedYaw = new HashMap<>();

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
    private void rotateGrabbedPlayer(AbstractClientPlayerEntity player, float yaw, float tickDelta,
                                     MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                     int light, CallbackInfo ci) {
        PoseState pose = PoseNetworking.poseStates.getOrDefault(player.getUuid(), PoseState.NONE);

        if (pose == PoseState.GRABBED) {
            matrices.push();

            float facingYaw;

            // Check if being held by someone (riding them)
            Entity vehicle = player.getVehicle();
            if (vehicle instanceof PlayerEntity holder) {
                // Being held - LOCK to holder's yaw
                facingYaw = holder.getYaw();
                lockedYaw.put(player.getUuid(), facingYaw);
            } else {
                // Thrown/flying - use LOCKED yaw from when thrown
                // This prevents player from rotating during flight
                if (lockedYaw.containsKey(player.getUuid())) {
                    facingYaw = lockedYaw.get(player.getUuid());
                } else {
                    // Fallback: lock to current yaw
                    facingYaw = player.getYaw();
                    lockedYaw.put(player.getUuid(), facingYaw);
                }
            }

            // COMPLETELY OVERRIDE the render rotation
            // Ignore the passed 'yaw' parameter and use our locked yaw
            // This counter-rotates against what Minecraft wants to render
            float counterRotation = -yaw + facingYaw;

            // Apply counter-rotation to lock player facing the correct direction
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(counterRotation));

            // Now rotate to horizontal (superman pose) - stomach facing DOWN
            matrices.translate(0, 0.9, 0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            matrices.translate(0, -0.9, 0);

            matrixPushed.put(player.getUuid(), true);
        } else {
            // Clean up stored yaw when no longer grabbed
            lockedYaw.remove(player.getUuid());
            matrixPushed.put(player.getUuid(), false);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("RETURN"))
    private void restoreMatrix(AbstractClientPlayerEntity player, float yaw, float tickDelta,
                               MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                               int light, CallbackInfo ci) {
        Boolean pushed = matrixPushed.get(player.getUuid());
        if (pushed != null && pushed) {
            matrices.pop();
            matrixPushed.put(player.getUuid(), false);
        }
    }
}