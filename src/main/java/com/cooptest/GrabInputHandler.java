package com.cooptest;

import com.cooptest.client.GrabClientState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GrabInputHandler {

    private static KeyBinding grabKey;
    private static KeyBinding throwKey;
    private static KeyBinding shieldKey;  // V key for shield toggle

    private static boolean wasGrabKeyPressed = false;
    private static boolean wasThrowKeyPressed = false;
    private static boolean wasSneakPressed = false;
    private static boolean wasJumpPressed = false; // For elytra boost
    private static boolean wasShieldKeyPressed = false;

    private static boolean isChargingThrow = false;
    private static long throwChargeStartTime = 0;
    private static final long MAX_CHARGE_TIME_MS = 1500;

    private static float lastSentChargeProgress = -1f;

    public static final Map<UUID, Boolean> clientShieldMode = new HashMap<>();


    public static void register() {
        grabKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.coopmoves.grab", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.coopmoves"
        ));

        throwKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.coopmoves.throw", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_T, "category.coopmoves"
        ));

        shieldKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.coopmoves.shield", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.coopmoves"
        ));

        // Register shield mode receiver for client-side sync
        ClientPlayNetworking.registerGlobalReceiver(GrabMechanic.ShieldModePayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        clientShieldMode.put(payload.holderId(), payload.enabled());
                        if (!payload.enabled()) {
                            clientShieldMode.remove(payload.holderId());
                        }
                    });
                }
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            UUID playerId = client.player.getUuid();
            PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);

            // ===== ELYTRA BOOST: Press space while thrown with elytra =====
            boolean isJumpPressed = client.options.jumpKey.isPressed();
            if (isJumpPressed && !wasJumpPressed) {
                // Check if player is in GRABBED pose (thrown/flying through air)
                if (pose == PoseState.GRABBED && !client.player.hasVehicle()) {
                    // Check if wearing elytra
                    if (client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).getItem()
                            instanceof net.minecraft.item.ElytraItem) {
                        // Send elytra boost request to server
                        ClientPlayNetworking.send(new GrabNetworking.ElytraBoostRequestPayload());
                    }
                }
            }
            wasJumpPressed = isJumpPressed;

            // ===== AIR CONTROL: Send movement input while thrown =====
            if (pose == PoseState.GRABBED && !client.player.hasVehicle()) {
                // Get movement input
                float forward = 0f;
                float strafe = 0f;
                if (client.options.forwardKey.isPressed()) forward += 1f;
                if (client.options.backKey.isPressed()) forward -= 1f;
                if (client.options.leftKey.isPressed()) strafe += 1f;
                if (client.options.rightKey.isPressed()) strafe -= 1f;

                // Send to server if any input
                if (Math.abs(forward) > 0.01f || Math.abs(strafe) > 0.01f) {
                    ClientPlayNetworking.send(new GrabNetworking.AirMovementPayload(forward, strafe));
                }
            }


            // ==================== END TEST MODE ====================

            // Check if holding someone
            boolean isHolding = pose == PoseState.GRAB_HOLDING || GrabClientState.isHolding(playerId);

            // Check if being held
            boolean isBeingHeld = (pose == PoseState.GRABBED && client.player.hasVehicle())
                    || GrabClientState.isBeingHeld(playerId);

            // ===== R KEY - Grab Ready / Drop =====
            boolean isGrabKeyPressed = grabKey.isPressed();
            if (isGrabKeyPressed && !wasGrabKeyPressed) {
                if (isHolding) {
                    // Drop when holding
                    ClientPlayNetworking.send(new GrabNetworking.DropRequestPayload());
                } else if (pose == PoseState.GRAB_READY) {
                    PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                    PoseNetworking.sendPoseToServer(playerId, PoseState.NONE);
                } else if (pose == PoseState.NONE && handsEmpty(client)) {
                    PoseNetworking.poseStates.put(playerId, PoseState.GRAB_READY);
                    PoseNetworking.sendPoseToServer(playerId, PoseState.GRAB_READY);
                }
            }
            wasGrabKeyPressed = isGrabKeyPressed;

            // ===== V KEY - Toggle Shield Mode =====
            boolean isShieldKeyPressed = shieldKey.isPressed();
            if (isShieldKeyPressed && !wasShieldKeyPressed) {
                if (isHolding) {
                    // Toggle between shield mode and throw mode
                    ClientPlayNetworking.send(new GrabNetworking.ShieldTogglePayload());
                }
            }
            wasShieldKeyPressed = isShieldKeyPressed;

            // ===== SHIFT - Escape when being held =====
            boolean isSneakPressed = client.options.sneakKey.isPressed();
            if (isSneakPressed && !wasSneakPressed) {
                if (isBeingHeld) {
                    // Escape when being held
                    ClientPlayNetworking.send(new GrabNetworking.EscapeRequestPayload());
                }
            }
            wasSneakPressed = isSneakPressed;

            // ===== T KEY - Throw (hold to charge) =====
            boolean isThrowKeyPressed = throwKey.isPressed();
            if (isHolding) {
                if (isThrowKeyPressed && !wasThrowKeyPressed) {
                    // Started charging
                    isChargingThrow = true;
                    throwChargeStartTime = System.currentTimeMillis();
                    lastSentChargeProgress = 0f;

                    // Start grab charge animation
                    com.cooptest.client.CoopAnimationHandler.startGrabCharge(client.player);
                } else if (isThrowKeyPressed && isChargingThrow) {
                    // While charging, sync progress to other players every 0.1 change
                    float currentProgress = getThrowChargeProgress();
                    GrabClientState.setChargeProgress(playerId, currentProgress); // For HUD
                    if (Math.abs(currentProgress - lastSentChargeProgress) >= 0.1f) {
                        PoseNetworking.sendChargeProgress(playerId, currentProgress);
                        lastSentChargeProgress = currentProgress;
                    }
                } else if (!isThrowKeyPressed && wasThrowKeyPressed && isChargingThrow) {
                    // Released - throw!
                    long chargeTime = System.currentTimeMillis() - throwChargeStartTime;
                    float power = Math.min(1.0f, (float) chargeTime / MAX_CHARGE_TIME_MS);
                    ClientPlayNetworking.send(new GrabNetworking.ThrowRequestPayload(power));
                    isChargingThrow = false;

                    // Play throw animation
                    com.cooptest.client.CoopAnimationHandler.playThrowAnimation(client.player);

                    // Trigger throw animation locally (mixin fallback)
                    ArmPoseTracker.throwAnimationStart.put(playerId, System.currentTimeMillis());

                    // Sync throw animation to other players
                    PoseNetworking.sendThrowAnimation(playerId);

                    // Reset charge progress
                    GrabClientState.setChargeProgress(playerId, 0f); // For HUD
                    PoseNetworking.sendChargeProgress(playerId, -1f);
                    lastSentChargeProgress = -1f;
                }
            } else {
                if (isChargingThrow) {
                    // Was charging but no longer holding - reset
                    GrabClientState.setChargeProgress(playerId, 0f); // For HUD
                    PoseNetworking.sendChargeProgress(playerId, -1f);
                    lastSentChargeProgress = -1f;
                }
                isChargingThrow = false;
            }
            wasThrowKeyPressed = isThrowKeyPressed;

            // ===== Auto-cancel GRAB_READY if hands not empty =====
            if (pose == PoseState.GRAB_READY && !handsEmpty(client)) {
                PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                PoseNetworking.sendPoseToServer(playerId, PoseState.NONE);
            }
        });
    }

    private static boolean handsEmpty(MinecraftClient client) {
        // Only check main hand - allow items in off-hand (shields, totems, etc)
        return client.player.getMainHandStack().isEmpty();
    }

    public static float getThrowChargeProgress() {
        if (!isChargingThrow) return -1f;
        long chargeTime = System.currentTimeMillis() - throwChargeStartTime;
        return Math.min(1.0f, (float) chargeTime / MAX_CHARGE_TIME_MS);
    }

    /**
     * Get charge progress for a specific player (used by mixin for other players)
     */
    public static float getChargeProgressFor(UUID playerId) {
        // For local player, use local tracking
        @SuppressWarnings("resource")
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getUuid().equals(playerId)) {
            return getThrowChargeProgress();
        }
        // For other players, use network-synced value
        return PoseNetworking.chargeProgress.getOrDefault(playerId, -1f);
    }
}