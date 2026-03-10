package com.cooptest.mixin.client;

import com.cooptest.ArmPoseTracker;
import com.cooptest.GrabInputHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * First-person arm animations for grab/throw/push mechanics.
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    // ==================== ANIMATION SETTINGS ====================

    // GRAB_READY - Arms up, ready to grab (zombie style)
    // "when i press R i want arm to go up as im about to hold him"
    @Unique private static final float READY_UP = 1.2f;         // HIGH up
    @Unique private static final float READY_FORWARD = 0.8f;    // Forward
    @Unique private static final float READY_PITCH = -85f;      // Point forward/up

    // GRAB_HOLDING - Holding player overhead
    // "i want to point when player right clicks me to ride it goes back a lot"
    @Unique private static final float HOLD_UP = 1.5f;          // Very high
    @Unique private static final float HOLD_FORWARD = 0.3f;     // Slightly forward
    @Unique private static final float HOLD_PITCH = -95f;       // Pointing up

    // CHARGE - Pulling back to throw
    // "when i charge it starts to jitter like bow and goes backwards a little"
    @Unique private static final float CHARGE_UP = 0.8f;        // Lower than hold
    @Unique private static final float CHARGE_FORWARD = -0.5f;  // BACK (negative)
    @Unique private static final float CHARGE_PITCH = -60f;     // Less raised
    @Unique private static final float CHARGE_SHAKE = 0.08f;    // Jitter intensity

    // THROW - Swing forward animation
    // "when i throw we player a normal hand swing and go back to normal"
    @Unique private static final float THROW_DURATION = 300f;   // ms

    // PUSH_IDLE - Ready stance
    // "when i hold shift right click my hand tilt and go forward"
    @Unique private static final float PUSH_IDLE_UP = 0.4f;
    @Unique private static final float PUSH_IDLE_FORWARD = 0.5f;
    @Unique private static final float PUSH_IDLE_PITCH = -50f;

    // PUSH_ACTION - Thrust forward
    // "when interaction happen a hand swing happens"
    @Unique private static final float PUSH_ACTION_UP = 0.6f;
    @Unique private static final float PUSH_ACTION_FORWARD = 1.0f;  // Far forward
    @Unique private static final float PUSH_ACTION_PITCH = -80f;

    @Unique private static final float LERP_SPEED = 0.25f;
    @Unique private static final float FAST_LERP = 0.4f;
    // ==============================================================

    // Current interpolated values
    @Unique private static float currUp = 0f;
    @Unique private static float currForward = 0f;
    @Unique private static float currPitch = 0f;

    // Throw animation tracking
    @Unique private static long throwStartTime = 0;
    @Unique private static boolean wasHolding = false;

    @Inject(method = "renderArm", at = @At("HEAD"))
    private void onRenderArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                             int light, Arm arm, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        boolean handsEmpty = client.player.getMainHandStack().isEmpty() &&
                client.player.getOffHandStack().isEmpty();

        UUID playerId = client.player.getUuid();
        PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);

        // Detect throw (was holding, now not holding)
        boolean isHolding = pose == PoseState.GRAB_HOLDING;
        if (wasHolding && !isHolding && pose == PoseState.GRAB_READY) {
            // Player just threw!
            throwStartTime = System.currentTimeMillis();
        }
        wasHolding = isHolding;

        // Check for throw animation from ArmPoseTracker
        Long trackerThrowStart = ArmPoseTracker.throwAnimationStart.get(playerId);
        if (trackerThrowStart != null) {
            throwStartTime = trackerThrowStart;
        }

        // Calculate targets
        float targetUp = 0f;
        float targetForward = 0f;
        float targetPitch = 0f;
        float lerpSpeed = LERP_SPEED;
        float shakeAmount = 0f;

        // Check if in throw animation
        boolean inThrowAnim = false;
        float throwProgress = 0f;
        if (throwStartTime > 0) {
            long elapsed = System.currentTimeMillis() - throwStartTime;
            if (elapsed < THROW_DURATION) {
                inThrowAnim = true;
                throwProgress = elapsed / THROW_DURATION;
            } else {
                throwStartTime = 0;
            }
        }

        if (inThrowAnim) {
            // THROW ANIMATION - swing forward then back to normal
            lerpSpeed = FAST_LERP;
            if (throwProgress < 0.3f) {
                // Swing forward (0 to 0.3)
                float p = throwProgress / 0.3f;
                targetUp = lerp(HOLD_UP, 0.3f, p);
                targetForward = lerp(HOLD_FORWARD, 1.2f, p);  // Thrust forward
                targetPitch = lerp(HOLD_PITCH, -100f, p);     // Swing down
            } else {
                // Return to normal (0.3 to 1.0)
                float p = (throwProgress - 0.3f) / 0.7f;
                targetUp = lerp(0.3f, 0f, p);
                targetForward = lerp(1.2f, 0f, p);
                targetPitch = lerp(-100f, 0f, p);
            }
        } else if (handsEmpty && pose != PoseState.NONE && pose != PoseState.GRABBED) {
            float charge = GrabInputHandler.getThrowChargeProgress();
            boolean isCharging = charge >= 0f;

            switch (pose) {
                case GRAB_READY -> {
                    targetUp = READY_UP;
                    targetForward = READY_FORWARD;
                    targetPitch = READY_PITCH;
                }
                case GRAB_HOLDING -> {
                    if (isCharging) {
                        // Interpolate hold -> charge + add jitter
                        targetUp = lerp(HOLD_UP, CHARGE_UP, charge);
                        targetForward = lerp(HOLD_FORWARD, CHARGE_FORWARD, charge);
                        targetPitch = lerp(HOLD_PITCH, CHARGE_PITCH, charge);

                        // Add bow-like jitter at high charge
                        if (charge > 0.5f) {
                            shakeAmount = CHARGE_SHAKE * (charge - 0.5f) * 2f;
                        }
                    } else {
                        targetUp = HOLD_UP;
                        targetForward = HOLD_FORWARD;
                        targetPitch = HOLD_PITCH;
                    }
                }
                case PUSH_IDLE, PUSH_RETURN -> {
                    targetUp = PUSH_IDLE_UP;
                    targetForward = PUSH_IDLE_FORWARD;
                    targetPitch = PUSH_IDLE_PITCH;
                }
                case PUSH_ACTION -> {
                    targetUp = PUSH_ACTION_UP;
                    targetForward = PUSH_ACTION_FORWARD;
                    targetPitch = PUSH_ACTION_PITCH;
                    lerpSpeed = FAST_LERP;  // Fast thrust
                }
            }
        }

        // Smooth interpolation
        currUp = lerp(currUp, targetUp, lerpSpeed);
        currForward = lerp(currForward, targetForward, lerpSpeed);
        currPitch = lerp(currPitch, targetPitch, lerpSpeed);

        // Add charge shake/jitter
        float shakeOffset = 0f;
        if (shakeAmount > 0f) {
            shakeOffset = (float)(Math.random() - 0.5) * shakeAmount;
        }

        // Skip if at rest
        if (Math.abs(currPitch) < 1f && Math.abs(currUp) < 0.01f && Math.abs(currForward) < 0.01f) {
            return;
        }

        // Apply transforms
        matrices.translate(0.0, currUp + shakeOffset, -currForward + shakeOffset * 0.5f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(currPitch + shakeOffset * 20f));
    }

    @Unique
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}