package com.cooptest;

import java.util.HashMap;
import java.util.UUID;


public class ArmPoseTracker {
    public static final HashMap<UUID, Float> rightArmPitch = new HashMap<>();
    public static final HashMap<UUID, Float> leftArmPitch = new HashMap<>();
    public static final HashMap<UUID, Float> rightArmYaw = new HashMap<>();
    public static final HashMap<UUID, Float> leftArmYaw = new HashMap<>();
    public static final HashMap<UUID, Float> rightArmRoll = new HashMap<>();
    public static final HashMap<UUID, Float> leftArmRoll = new HashMap<>();

    public static final HashMap<UUID, Float> bodyLean = new HashMap<>();

    public static final HashMap<UUID, Long> throwAnimationStart = new HashMap<>();

    public static final HashMap<UUID, PoseState> lastPose = new HashMap<>();

  
    public static void cleanup(UUID playerId) {
        rightArmPitch.remove(playerId);
        leftArmPitch.remove(playerId);
        rightArmYaw.remove(playerId);
        leftArmYaw.remove(playerId);
        rightArmRoll.remove(playerId);
        leftArmRoll.remove(playerId);
        bodyLean.remove(playerId);
        throwAnimationStart.remove(playerId);
        lastPose.remove(playerId);
    }
}
