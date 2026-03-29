package com.cooptest.mixin.client;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
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

    @Inject(method = "setupTransforms(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;FF)V", at = @At("RETURN"))
    private void rotateGrabbedPlayer(PlayerEntityRenderState state, MatrixStack matrices, float bodyYaw, float animationProgress, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        Entity entity = client.world.getEntityById(state.id);
        if (!(entity instanceof PlayerEntity)) return;
        PlayerEntity player = (PlayerEntity) entity;
        UUID uuid = player.getUuid();
        PoseState pose = PoseNetworking.poseStates.getOrDefault(uuid, PoseState.NONE);

        if (pose == PoseState.GRABBED) {

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
            float counterRotation = -bodyYaw + facingYaw;

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
}