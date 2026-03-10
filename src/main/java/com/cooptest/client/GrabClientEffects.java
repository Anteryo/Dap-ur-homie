package com.cooptest.client;

import com.cooptest.GrabInputHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.sound.SoundEvents;

import java.util.UUID;

public class GrabClientEffects {

    private static final float CHARGE_SOUND_PITCH_MIN = 0.5f;
    private static final float CHARGE_SOUND_PITCH_MAX = 2.0f;
    private static final int CHARGE_SOUND_INTERVAL = 4;

    private static final float CHARGE_SHAKE_INTENSITY = 0.15f;

    private static final float HELD_SHAKE_INTENSITY = 0.3f;

    private static final float LANDING_SHAKE_INTENSITY = 1.5f;
    private static final long LANDING_SHAKE_DURATION_MS = 2000;

    private static int chargeSoundTicks = 0;
    private static boolean wasFullyCharged = false;
    private static boolean wasBeingHeld = false;
    private static boolean wasGrabbed = false;
    private static long landingShakeStartTime = 0;
    private static boolean isLandingShaking = false;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            ClientPlayerEntity player = client.player;
            UUID playerId = player.getUuid();
            PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);

            float chargeProgress = GrabInputHandler.getThrowChargeProgress();
            boolean isCharging = chargeProgress >= 0f;

            if (isCharging && pose == PoseState.GRAB_HOLDING) {
                chargeSoundTicks++;

                if (chargeSoundTicks >= CHARGE_SOUND_INTERVAL) {
                    float pitch = CHARGE_SOUND_PITCH_MIN +
                            (CHARGE_SOUND_PITCH_MAX - CHARGE_SOUND_PITCH_MIN) * chargeProgress;

                    player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.3f, pitch);
                    chargeSoundTicks = 0;
                }

                if (chargeProgress >= 0.99f && !wasFullyCharged) {
                    player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
                    player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
                    wasFullyCharged = true;
                }

                if (chargeProgress >= 0.99f) {
                    float shakeX = (float)(Math.random() - 0.5) * CHARGE_SHAKE_INTENSITY;
                    float shakeY = (float)(Math.random() - 0.5) * CHARGE_SHAKE_INTENSITY;
                    player.setPitch(player.getPitch() + shakeX);
                    player.setYaw(player.getYaw() + shakeY);
                }

            } else {
                chargeSoundTicks = 0;
                wasFullyCharged = false;
            }

            boolean isBeingHeld = pose == PoseState.GRABBED && player.hasVehicle();

            if (isBeingHeld && !wasBeingHeld) {
                player.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
            }

            if (isBeingHeld && player.getVehicle() != null) {
                UUID holderId = player.getVehicle().getUuid();
                float holderCharge = PoseNetworking.chargeProgress.getOrDefault(holderId, -1f);

                if (holderCharge >= 0.99f) {
                    float shakeX = (float)(Math.random() - 0.5) * HELD_SHAKE_INTENSITY;
                    float shakeY = (float)(Math.random() - 0.5) * HELD_SHAKE_INTENSITY;
                    player.setPitch(player.getPitch() + shakeX);
                    player.setYaw(player.getYaw() + shakeY);
                }
            }

            wasBeingHeld = isBeingHeld;

            boolean wasInGrabbedPose = wasGrabbed;
            boolean nowInNormalPose = pose == PoseState.NONE;

            if (wasInGrabbedPose && nowInNormalPose && !player.hasVehicle()) {
                isLandingShaking = true;
                landingShakeStartTime = System.currentTimeMillis();

                player.playSound(SoundEvents.ENTITY_PLAYER_BIG_FALL, 1.0f, 1.0f);
            }

            wasGrabbed = (pose == PoseState.GRABBED);

            if (isLandingShaking) {
                long elapsed = System.currentTimeMillis() - landingShakeStartTime;

                if (elapsed < LANDING_SHAKE_DURATION_MS) {
                    float fadeProgress = 1.0f - (elapsed / (float)LANDING_SHAKE_DURATION_MS);
                    float intensity = LANDING_SHAKE_INTENSITY * fadeProgress;

                    float shakeX = (float)(Math.random() - 0.5) * intensity;
                    float shakeY = (float)(Math.random() - 0.5) * intensity;
                    player.setPitch(player.getPitch() + shakeX);
                    player.setYaw(player.getYaw() + shakeY);
                } else {
                    isLandingShaking = false;
                }
            }
        });
    }
}