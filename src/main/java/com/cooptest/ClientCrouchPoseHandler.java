package com.cooptest;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.HashMap;
import java.util.UUID;

public class ClientCrouchPoseHandler {

    private static final HashMap<UUID, Integer> crouchTicks = new HashMap<>();
    private static final HashMap<UUID, double[]> lastPos = new HashMap<>();
    private static final HashMap<UUID, PoseState> lastSentState = new HashMap<>();
    public static boolean disableAutoReset = false;

    private static final double MOVE_TOLERANCE = 0.0025;
    private static final double CANCEL_TOLERANCE = 0.075;
    private static final int REQUIRED_TICKS = 10;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player == null) return;

            UUID id = player.getUuid();
            PoseState currentPose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);

            // Don't touch grab poses
            if (currentPose == PoseState.GRAB_READY ||
                    currentPose == PoseState.GRAB_HOLDING ||
                    currentPose == PoseState.GRABBED) {
                return;
            }

            if (disableAutoReset) return;

            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();

            double[] prev = lastPos.getOrDefault(id, new double[]{px, py, pz});
            double dx = px - prev[0];
            double dy = py - prev[1];
            double dz = pz - prev[2];
            double dist2 = dx * dx + dy * dy + dz * dz;
            lastPos.put(id, new double[]{px, py, pz});

            boolean sneaking = player.isSneaking();
            boolean isStill = dist2 < (MOVE_TOLERANCE * MOVE_TOLERANCE);
            boolean holdingRightClick = client.options.useKey.isPressed();
            boolean movedTooMuch = dist2 > (CANCEL_TOLERANCE * CANCEL_TOLERANCE);

            PoseState newState;

            if (sneaking && isStill && holdingRightClick) {
                int ticks = crouchTicks.getOrDefault(id, 0) + 1;
                crouchTicks.put(id, ticks);

                if (ticks >= REQUIRED_TICKS) {
                    newState = PoseState.PUSH_IDLE;
                } else {
                    newState = PoseState.NONE;
                }
            } else if (currentPose == PoseState.PUSH_IDLE && !movedTooMuch && sneaking && holdingRightClick) {
                newState = PoseState.PUSH_IDLE;
            } else {
                crouchTicks.put(id, 0);
                newState = PoseState.NONE;
            }

            PoseNetworking.poseStates.put(id, newState);

            PoseState previousState = lastSentState.getOrDefault(id, PoseState.NONE);
            if (newState != previousState) {
                PoseNetworking.sendPoseToServer(id, newState);
                lastSentState.put(id, newState);
            }
        });
    }
}
