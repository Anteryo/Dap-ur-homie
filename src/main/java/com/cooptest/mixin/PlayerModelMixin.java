package com.cooptest.mixin;

import com.cooptest.ArmPoseTracker;
import com.cooptest.GrabInputHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * ==================================================================================
 * PLAYER MODEL MIXIN - CONTROLS 3RD PERSON ANIMATIONS ONLY
 * ==================================================================================
 *
 * This mixin affects what OTHER PLAYERS see (3rd person view / F5 mode).
 *
 * To test: Press F5 to enter 3rd person, then press G to cycle through poses.
 *
 * IMPORTANT: First-person hand animations are NOT controlled here!
 * First-person requires a separate HeldItemRendererMixin (more complex).
 *
 * All angles are in DEGREES (converted to radians internally).
 * Negative pitch = arm goes UP
 * Positive pitch = arm goes DOWN
 * Negative yaw = arm goes RIGHT (away from body for right arm)
 * Positive yaw = arm goes LEFT
 * Roll = arm rotation/twist
 *
 * ==================================================================================
 */
@Mixin(PlayerEntityModel.class)
public class PlayerModelMixin<T extends LivingEntity> {


    @Unique private static final float ANIMATION_SPEED = 0.2f;       // Smoother base animation
    @Unique private static final float FAST_ANIMATION_SPEED = 0.35f; // Faster for high five


    @Unique private static final float GRAB_READY_ARM_PITCH = -70f;   // How high arms raise (-90 = horizontal)
    @Unique private static final float GRAB_READY_ARM_YAW = 5f;       // How far arms spread apart
    @Unique private static final float GRAB_READY_ARM_ROLL = 0f;      // Arm twist


    @Unique private static final float HOLD_RIGHT_ARM_PITCH = -110f;  // Arm up (zombie style but higher)
    @Unique private static final float HOLD_RIGHT_ARM_YAW = -10f;     // Slightly inward
    @Unique private static final float HOLD_RIGHT_ARM_ROLL = 0f;      // No twist
    @Unique private static final float HOLD_LEFT_ARM_PITCH = 10f;     // Left arm different
    @Unique private static final float HOLD_LEFT_ARM_YAW = 10f;       // Slightly inward
    @Unique private static final float HOLD_LEFT_ARM_ROLL = 0f;       // No twist

    // ----- CHARGE POSE (holding T to charge throw) -----
    // 3RD PERSON: Arms go backward, body leans back
    @Unique private static final float CHARGE_RIGHT_ARM_PITCH = -150f;   // Arms go back/down
    @Unique private static final float CHARGE_RIGHT_ARM_YAW = 10f;       // Arms spread back
    @Unique private static final float CHARGE_RIGHT_ARM_ROLL = 0f;       // No twist
    @Unique private static final float CHARGE_LEFT_ARM_PITCH = -70f;     // Same as right
    @Unique private static final float CHARGE_LEFT_ARM_YAW = 30f;        // Mirror of right
    @Unique private static final float CHARGE_BODY_LEAN = -20f;          // Body leans BACKWARD

    // ----- THROW ANIMATION (releasing T) -----
    // 3RD PERSON: Arms swing forward, body leans forward then returns
    @Unique private static final float THROW_RIGHT_ARM_PITCH = -130f;   // Arms thrust forward/up
    @Unique private static final float THROW_RIGHT_ARM_YAW = -5f;       // Arms come together
    @Unique private static final float THROW_LEFT_ARM_PITCH = -20f;     // Same as right
    @Unique private static final float THROW_LEFT_ARM_YAW = 5f;         // Mirror
    @Unique private static final float THROW_BODY_LEAN = 25f;           // Body leans FORWARD
    @Unique private static final float THROW_DURATION_MS = 350f;        // Animation length in milliseconds

    // ----- PUSH ANIMATION -----
    // 3RD PERSON: Arms thrust forward (SLOWED DOWN)
    @Unique private static final float PUSH_IDLE_PITCH = -45f;         // Arms slightly raised
    @Unique private static final float PUSH_IDLE_YAW = -20f;           // Arms apart
    @Unique private static final float PUSH_ACTION_PITCH = -90f;       // Arms thrust forward
    @Unique private static final float PUSH_ACTION_YAW = 0f;           // Arms straight
    @Unique private static final float PUSH_ANIMATION_SPEED = 0.08f;   // Slower push animation

    // HIGH FIVE now handled by PAL animations - constants removed

    // ----- SUPERMAN POSE (being grabbed/thrown) -----
    // 3RD PERSON: Horizontal flying pose
    @Unique private static final float SUPERMAN_HEAD_PITCH = -30f;     // Head looking forward/up
    @Unique private static final float SUPERMAN_RIGHT_ARM_PITCH = -180f; // Right arm forward
    @Unique private static final float SUPERMAN_RIGHT_ARM_ROLL = -10f;   // Slight spread
    @Unique private static final float SUPERMAN_LEFT_ARM_PITCH = 10f;    // Left arm back
    @Unique private static final float SUPERMAN_LEFT_ARM_ROLL = 10f;     // Slight spread
    @Unique private static final float SUPERMAN_LEG_ROLL = 5f;           // Legs apart

    // ==================================================================================
    // END ADJUSTABLE SETTINGS
    // ==================================================================================

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void injectPose(T entity, float f, float g, float h, float i, float j, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player)) return;

        UUID playerId = player.getUuid();
        PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
        PoseState lastPose = ArmPoseTracker.lastPose.getOrDefault(playerId, PoseState.NONE);

        PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
        ModelPart rightArm = model.rightArm;
        ModelPart leftArm = model.leftArm;
        ModelPart body = model.body;
        ModelPart head = model.head;
        ModelPart rightLeg = model.rightLeg;
        ModelPart leftLeg = model.leftLeg;

        // Store base angles (from vanilla animations like walking)
        float baseRightPitch = rightArm.pitch;
        float baseLeftPitch = leftArm.pitch;
        float baseRightYaw = rightArm.yaw;
        float baseLeftYaw = leftArm.yaw;

        boolean isSwinging = player.handSwinging;
        boolean isUsingItem = player.isUsingItem();

        // ==================== GRABBED/THROWN POSE (superman) ====================
        // When being held OR thrown - show superman pose
        // BUT skip if in shield mode (they use their own shield animation)
        if (pose == PoseState.GRABBED) {
            // Check if player is riding an armor stand (shield mode)
            // In shield mode, PAL handles the animation, so skip superman pose
            if (player.hasVehicle() && !(player.getVehicle() instanceof PlayerEntity)) {
                // Riding armor stand (shield mode) - let PAL handle animation
                ArmPoseTracker.lastPose.put(playerId, pose);
                return;
            }

            body.pitch = 0;
            body.yaw = 0;
            body.roll = 0;

            head.pitch = (float) Math.toRadians(SUPERMAN_HEAD_PITCH);
            head.yaw = 0;
            head.roll = 0;

            rightArm.pitch = (float) Math.toRadians(SUPERMAN_RIGHT_ARM_PITCH);
            rightArm.yaw = 0;
            rightArm.roll = (float) Math.toRadians(SUPERMAN_RIGHT_ARM_ROLL);

            leftArm.pitch = (float) Math.toRadians(SUPERMAN_LEFT_ARM_PITCH);
            leftArm.yaw = 0;
            leftArm.roll = (float) Math.toRadians(SUPERMAN_LEFT_ARM_ROLL);

            rightLeg.pitch = 0;
            rightLeg.yaw = 0;
            rightLeg.roll = (float) Math.toRadians(SUPERMAN_LEG_ROLL);

            leftLeg.pitch = 0;
            leftLeg.yaw = 0;
            leftLeg.roll = (float) Math.toRadians(-SUPERMAN_LEG_ROLL);

            model.rightSleeve.copyTransform(rightArm);
            model.leftSleeve.copyTransform(leftArm);
            model.rightPants.copyTransform(rightLeg);
            model.leftPants.copyTransform(leftLeg);
            model.jacket.copyTransform(body);
            model.hat.copyTransform(head);

            ArmPoseTracker.lastPose.put(playerId, pose);
            return;
        }

        // ==================== THROWN PLAYER (superman pose) ====================
        // Check if player was recently thrown (not riding anymore but in air)
        if (pose == PoseState.NONE && !player.isOnGround() && !player.hasVehicle()) {
            // Check if this player is being tracked as thrown
            if (com.cooptest.GrabMechanic.isPlayerThrown(playerId)) {
                // Apply superman pose
                body.pitch = 0;
                body.yaw = 0;
                body.roll = 0;

                head.pitch = (float) Math.toRadians(SUPERMAN_HEAD_PITCH);
                head.yaw = 0;
                head.roll = 0;

                rightArm.pitch = (float) Math.toRadians(SUPERMAN_RIGHT_ARM_PITCH);
                rightArm.yaw = 0;
                rightArm.roll = (float) Math.toRadians(SUPERMAN_RIGHT_ARM_ROLL);

                leftArm.pitch = (float) Math.toRadians(SUPERMAN_LEFT_ARM_PITCH);
                leftArm.yaw = 0;
                leftArm.roll = (float) Math.toRadians(SUPERMAN_LEFT_ARM_ROLL);

                rightLeg.pitch = 0;
                rightLeg.yaw = 0;
                rightLeg.roll = (float) Math.toRadians(SUPERMAN_LEG_ROLL);

                leftLeg.pitch = 0;
                leftLeg.yaw = 0;
                leftLeg.roll = (float) Math.toRadians(-SUPERMAN_LEG_ROLL);

                model.rightSleeve.copyTransform(rightArm);
                model.leftSleeve.copyTransform(leftArm);
                model.rightPants.copyTransform(rightLeg);
                model.leftPants.copyTransform(leftLeg);
                model.jacket.copyTransform(body);
                model.hat.copyTransform(head);

                ArmPoseTracker.lastPose.put(playerId, pose);
                return;
            }
        }


        if (pose == PoseState.GRAB_HOLDING) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            boolean isLocalPlayer = mc.player != null && mc.player.getUuid().equals(playerId);
            boolean isFirstPerson = mc.options.getPerspective().isFirstPerson();

            if (!isLocalPlayer || !isFirstPerson) {
                // Let PAL handle third person
                ArmPoseTracker.lastPose.put(playerId, pose);
                return;
            }
        }


        if (pose == PoseState.GRAB_READY) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            boolean isLocalPlayer = mc.player != null && mc.player.getUuid().equals(playerId);
            boolean isFirstPerson = mc.options.getPerspective().isFirstPerson();

            if (!isLocalPlayer || !isFirstPerson) {
                // Let PAL handle third person
                ArmPoseTracker.lastPose.put(playerId, pose);
                return;
            }
        }


        if (pose == PoseState.PUSH_IDLE || pose == PoseState.PUSH_ACTION || pose == PoseState.PUSH_RETURN) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            boolean isLocalPlayer = mc.player != null && mc.player.getUuid().equals(playerId);
            boolean isFirstPerson = mc.options.getPerspective().isFirstPerson();

            if (!isLocalPlayer || !isFirstPerson) {
                ArmPoseTracker.lastPose.put(playerId, pose);
                return;
            }
        }


        boolean hasHandRaised = com.cooptest.HighFiveHandler.hasHandRaised(playerId);
        if (hasHandRaised) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            boolean isLocalPlayer = mc.player != null && mc.player.getUuid().equals(playerId);
            boolean isFirstPerson = mc.options.getPerspective().isFirstPerson();

            if (isLocalPlayer && isFirstPerson) {
                rightArm.pitch = (float) Math.toRadians(-100f);  // Arm up
                rightArm.yaw = (float) Math.toRadians(30f);      // Slightly out
                rightArm.roll = 0f;

                model.rightSleeve.copyTransform(rightArm);
                ArmPoseTracker.lastPose.put(playerId, pose);
                return;
            }
        }

        if (isSwinging || isUsingItem) {
            if (pose != PoseState.GRAB_READY && pose != PoseState.GRAB_HOLDING &&
                    pose != PoseState.PUSH_IDLE && pose != PoseState.PUSH_ACTION) {
                return;
            }
        }

        float targetRightPitch = 0f;
        float targetLeftPitch = 0f;
        float targetRightYaw = 0f;
        float targetLeftYaw = 0f;
        float targetRightRoll = 0f;
        float targetLeftRoll = 0f;
        float targetBodyLean = 0f;  // Body lean (positive = back, negative = forward)
        float animSpeed = ANIMATION_SPEED;
        boolean useAbsoluteAngles = false; // If true, don't add to base angles

        float directJitterPitch = 0f;
        float directJitterYaw = 0f;
        float directJitterRoll = 0f;

        Long throwStart = ArmPoseTracker.throwAnimationStart.get(playerId);
        boolean inThrowAnimation = false;
        float throwProgress = 0f;

        if (throwStart != null) {
            long elapsed = System.currentTimeMillis() - throwStart;
            if (elapsed < THROW_DURATION_MS) {
                inThrowAnimation = true;
                throwProgress = elapsed / THROW_DURATION_MS; // 0.0 to 1.0
            } else {
                ArmPoseTracker.throwAnimationStart.remove(playerId);
            }
        }

        float chargeProgress = GrabInputHandler.getChargeProgressFor(playerId);
        boolean isCharging = chargeProgress >= 0f;

        if (inThrowAnimation) {

            animSpeed = FAST_ANIMATION_SPEED;
            useAbsoluteAngles = true;


            if (throwProgress < 0.4f) {
                float throwPhase = throwProgress / 0.4f;
                targetRightPitch = lerp(CHARGE_RIGHT_ARM_PITCH, THROW_RIGHT_ARM_PITCH, throwPhase);
                targetRightYaw = lerp(CHARGE_RIGHT_ARM_YAW, THROW_RIGHT_ARM_YAW, throwPhase);
                targetLeftPitch = lerp(CHARGE_LEFT_ARM_PITCH, THROW_LEFT_ARM_PITCH, throwPhase);
                targetLeftYaw = lerp(CHARGE_LEFT_ARM_YAW, THROW_LEFT_ARM_YAW, throwPhase);
                targetBodyLean = lerp(CHARGE_BODY_LEAN, THROW_BODY_LEAN, throwPhase);
            } else {
                float returnPhase = (throwProgress - 0.4f) / 0.6f;
                targetRightPitch = lerp(THROW_RIGHT_ARM_PITCH, 0f, returnPhase);
                targetRightYaw = lerp(THROW_RIGHT_ARM_YAW, 0f, returnPhase);
                targetLeftPitch = lerp(THROW_LEFT_ARM_PITCH, 0f, returnPhase);
                targetLeftYaw = lerp(THROW_LEFT_ARM_YAW, 0f, returnPhase);
                targetBodyLean = lerp(THROW_BODY_LEAN, 0f, returnPhase);
            }
            targetRightRoll = 0f;
            targetLeftRoll = 0f;

        } else if (pose == PoseState.GRAB_HOLDING && isCharging) {
            useAbsoluteAngles = true;
            animSpeed = ANIMATION_SPEED;


            targetRightPitch = lerp(HOLD_RIGHT_ARM_PITCH, CHARGE_RIGHT_ARM_PITCH, chargeProgress);
            targetRightYaw = lerp(HOLD_RIGHT_ARM_YAW, CHARGE_RIGHT_ARM_YAW, chargeProgress);
            targetRightRoll = lerp(HOLD_RIGHT_ARM_ROLL, CHARGE_RIGHT_ARM_ROLL, chargeProgress);
            targetLeftPitch = lerp(HOLD_LEFT_ARM_PITCH, CHARGE_LEFT_ARM_PITCH, chargeProgress);
            targetLeftYaw = lerp(HOLD_LEFT_ARM_YAW, CHARGE_LEFT_ARM_YAW, chargeProgress);
            targetLeftRoll = HOLD_LEFT_ARM_ROLL;
            targetBodyLean = lerp(0f, CHARGE_BODY_LEAN, chargeProgress);  // Lean back while charging

        } else if (pose == PoseState.GRAB_HOLDING) {
            useAbsoluteAngles = true;
            targetRightPitch = HOLD_RIGHT_ARM_PITCH;
            targetRightYaw = HOLD_RIGHT_ARM_YAW;
            targetRightRoll = HOLD_RIGHT_ARM_ROLL;
            targetLeftPitch = HOLD_LEFT_ARM_PITCH;
            targetLeftYaw = HOLD_LEFT_ARM_YAW;
            targetLeftRoll = HOLD_LEFT_ARM_ROLL;

        } else if (pose == PoseState.GRAB_READY) {
            useAbsoluteAngles = true;
            targetRightPitch = GRAB_READY_ARM_PITCH;
            targetLeftPitch = GRAB_READY_ARM_PITCH;
            targetRightYaw = -GRAB_READY_ARM_YAW;
            targetLeftYaw = GRAB_READY_ARM_YAW;
            targetRightRoll = GRAB_READY_ARM_ROLL;
            targetLeftRoll = -GRAB_READY_ARM_ROLL;

        } else if (pose == PoseState.PUSH_ACTION) {
            useAbsoluteAngles = true;
            animSpeed = 1.0f;

            float pushProgress = com.cooptest.client.PushClientHandler.getPushAnimProgress(playerId);

            if (pushProgress >= 0 && pushProgress < 0.4f) {
                float phase = pushProgress / 0.4f;
                float eased = 1.0f - (1.0f - phase) * (1.0f - phase);

                targetRightPitch = lerp(PUSH_IDLE_PITCH, -110f, eased);
                targetLeftPitch = lerp(PUSH_IDLE_PITCH, -110f, eased);
                targetRightYaw = lerp(PUSH_IDLE_YAW, -5f, eased);
                targetLeftYaw = lerp(-PUSH_IDLE_YAW, 5f, eased);
            } else if (pushProgress >= 0.4f) {
                float phase = (pushProgress - 0.4f) / 0.6f;
                float eased = phase * phase;

                targetRightPitch = lerp(-110f, PUSH_IDLE_PITCH, eased);
                targetLeftPitch = lerp(-110f, PUSH_IDLE_PITCH, eased);
                targetRightYaw = lerp(-5f, PUSH_IDLE_YAW, eased);
                targetLeftYaw = lerp(5f, -PUSH_IDLE_YAW, eased);
            } else {
                targetRightPitch = PUSH_IDLE_PITCH;
                targetLeftPitch = PUSH_IDLE_PITCH;
                targetRightYaw = PUSH_IDLE_YAW;
                targetLeftYaw = -PUSH_IDLE_YAW;
            }

        } else if (pose == PoseState.PUSH_IDLE || pose == PoseState.PUSH_RETURN) {
            useAbsoluteAngles = true;
            animSpeed = PUSH_ANIMATION_SPEED;  // Use slower push speed
            targetRightPitch = PUSH_IDLE_PITCH;
            targetLeftPitch = PUSH_IDLE_PITCH;
            targetRightYaw = PUSH_IDLE_YAW;
            targetLeftYaw = -PUSH_IDLE_YAW;



        } else if (com.cooptest.client.ChargedDapClientHandler.isPlayerCharging(playerId)) {

            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            boolean isLocalPlayer = mc.player != null && mc.player.getUuid().equals(playerId);
            boolean isFirstPerson = mc.options.getPerspective().isFirstPerson();

            if (!isLocalPlayer || !isFirstPerson) {
                ArmPoseTracker.lastPose.put(playerId, pose);
                return;
            }

            useAbsoluteAngles = true;
            animSpeed = FAST_ANIMATION_SPEED;

            float chargePercent = com.cooptest.client.ChargedDapClientHandler.getPlayerChargePercent(playerId);
            float fireLevel = com.cooptest.client.ChargedDapClientHandler.getPlayerFireLevel(playerId);

            float basePitch = -70f;  // Raised
            float baseYaw = -15f;    // Slightly to side
            float baseRoll = 5f;

            if (chargePercent > 0.5f) {
                float pullback = (chargePercent - 0.5f) * 2f; // 0-1 over second half
                basePitch -= pullback * 10f;   // -70 to -80
                baseYaw -= pullback * 10f;     // -15 to -25
            }

            if (fireLevel > 0.05f) {
                basePitch -= fireLevel * 30f;   // Up to -110 (way back)
                baseYaw -= fireLevel * 35f;     // Up to -60 (far to side)
                baseRoll += fireLevel * 20f;    // Up to 25 (rotated out)
            }

            targetRightPitch = basePitch;
            targetRightYaw = baseYaw;
            targetRightRoll = baseRoll;

            targetLeftPitch = 0f;
            targetLeftYaw = 0f;
            targetLeftRoll = 0f;

        } else if (com.cooptest.client.CatchClientHandler.getCatcherAnimProgress(playerId) >= 0) {
            useAbsoluteAngles = true;
            animSpeed = 1.0f;

            float progress = com.cooptest.client.CatchClientHandler.getCatcherAnimProgress(playerId);

            if (progress < 0.3f) {
                float phase = progress / 0.3f;
                float eased = phase * phase; // ease-in

                targetRightPitch = lerp(GRAB_READY_ARM_PITCH, -30f, eased);
                targetLeftPitch = lerp(GRAB_READY_ARM_PITCH, -30f, eased);
                targetRightYaw = lerp(GRAB_READY_ARM_YAW, 40f, eased);
                targetLeftYaw = lerp(-GRAB_READY_ARM_YAW, -40f, eased);
            } else {
                float phase = (progress - 0.3f) / 0.7f;
                float eased = 1.0f - (1.0f - phase) * (1.0f - phase); // ease-out

                targetRightPitch = lerp(-30f, 0f, eased);
                targetLeftPitch = lerp(-30f, 0f, eased);
                targetRightYaw = lerp(40f, 0f, eased);
                targetLeftYaw = lerp(-40f, 0f, eased);
            }

        } else if (com.cooptest.client.CatchClientHandler.getCaughtAnimProgress(playerId) >= 0) {
            useAbsoluteAngles = true;
            animSpeed = 1.0f;

            float progress = com.cooptest.client.CatchClientHandler.getCaughtAnimProgress(playerId);

            if (progress < 0.2f) {
                float phase = progress / 0.2f;
                targetRightPitch = lerp(0f, -20f, phase);
                targetLeftPitch = lerp(0f, -20f, phase);
                targetRightYaw = lerp(0f, -30f, phase);
                targetLeftYaw = lerp(0f, 30f, phase);
            } else {
                float phase = (progress - 0.2f) / 0.8f;
                float eased = phase * phase;

                targetRightPitch = lerp(-20f, 0f, eased);
                targetLeftPitch = lerp(-20f, 0f, eased);
                targetRightYaw = lerp(-30f, 0f, eased);
                targetLeftYaw = lerp(30f, 0f, eased);
            }

        } else {

            useAbsoluteAngles = false;
        }


        float currRightPitch = ArmPoseTracker.rightArmPitch.getOrDefault(playerId, 0f);
        float currLeftPitch = ArmPoseTracker.leftArmPitch.getOrDefault(playerId, 0f);
        float currRightYaw = ArmPoseTracker.rightArmYaw.getOrDefault(playerId, 0f);
        float currLeftYaw = ArmPoseTracker.leftArmYaw.getOrDefault(playerId, 0f);
        float currRightRoll = ArmPoseTracker.rightArmRoll.getOrDefault(playerId, 0f);
        float currLeftRoll = ArmPoseTracker.leftArmRoll.getOrDefault(playerId, 0f);
        float currBodyLean = ArmPoseTracker.bodyLean.getOrDefault(playerId, 0f);

        float targetRightPitchRad = (float) Math.toRadians(targetRightPitch);
        float targetLeftPitchRad = (float) Math.toRadians(targetLeftPitch);
        float targetRightYawRad = (float) Math.toRadians(targetRightYaw);
        float targetLeftYawRad = (float) Math.toRadians(targetLeftYaw);
        float targetRightRollRad = (float) Math.toRadians(targetRightRoll);
        float targetLeftRollRad = (float) Math.toRadians(targetLeftRoll);
        float targetBodyLeanRad = (float) Math.toRadians(targetBodyLean);

        currRightPitch += (targetRightPitchRad - currRightPitch) * animSpeed;
        currLeftPitch += (targetLeftPitchRad - currLeftPitch) * animSpeed;
        currRightYaw += (targetRightYawRad - currRightYaw) * animSpeed;
        currLeftYaw += (targetLeftYawRad - currLeftYaw) * animSpeed;
        currRightRoll += (targetRightRollRad - currRightRoll) * animSpeed;
        currLeftRoll += (targetLeftRollRad - currLeftRoll) * animSpeed;
        currBodyLean += (targetBodyLeanRad - currBodyLean) * animSpeed;

        ArmPoseTracker.rightArmPitch.put(playerId, currRightPitch);
        ArmPoseTracker.leftArmPitch.put(playerId, currLeftPitch);
        ArmPoseTracker.rightArmYaw.put(playerId, currRightYaw);
        ArmPoseTracker.leftArmYaw.put(playerId, currLeftYaw);
        ArmPoseTracker.rightArmRoll.put(playerId, currRightRoll);
        ArmPoseTracker.leftArmRoll.put(playerId, currLeftRoll);
        ArmPoseTracker.bodyLean.put(playerId, currBodyLean);

        if (useAbsoluteAngles) {

            rightArm.pitch = currRightPitch + (float) Math.toRadians(directJitterPitch);
            leftArm.pitch = currLeftPitch;
            rightArm.yaw = currRightYaw + (float) Math.toRadians(directJitterYaw);
            leftArm.yaw = currLeftYaw;
            rightArm.roll = currRightRoll + (float) Math.toRadians(directJitterRoll);
            leftArm.roll = currLeftRoll;
        } else {
            rightArm.pitch = baseRightPitch + currRightPitch;
            leftArm.pitch = baseLeftPitch + currLeftPitch;
            rightArm.yaw = baseRightYaw + currRightYaw;
            leftArm.yaw = baseLeftYaw + currLeftYaw;
            rightArm.roll += currRightRoll;
            leftArm.roll += currLeftRoll;
        }


        boolean shouldApplyBodyLean = inThrowAnimation || (pose == PoseState.GRAB_HOLDING && isCharging);
        if (shouldApplyBodyLean && Math.abs(currBodyLean) > 0.01f) {
            body.pitch += currBodyLean;
            model.jacket.copyTransform(body);
        }

        model.rightSleeve.copyTransform(rightArm);
        model.leftSleeve.copyTransform(leftArm);

        ArmPoseTracker.lastPose.put(playerId, pose);
    }

    @Unique
    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }
}