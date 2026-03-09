package com.cooptest.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GrabClientState {

    private static final Map<UUID, UUID> holderToHeld = new HashMap<>();
    private static final Map<UUID, UUID> heldToHolder = new HashMap<>();
    private static final Map<UUID, Float> chargeProgress = new HashMap<>();

    public static void onGrabStart(UUID holderUuid, UUID heldUuid) {
        holderToHeld.put(holderUuid, heldUuid);
        heldToHolder.put(heldUuid, holderUuid);
    }

    public static void onGrabEnd(UUID holderUuid, UUID heldUuid) {
        holderToHeld.remove(holderUuid);
        heldToHolder.remove(heldUuid);
        chargeProgress.remove(holderUuid);
    }

    public static boolean isHolding(UUID playerUuid) {
        return holderToHeld.containsKey(playerUuid);
    }

    public static boolean isBeingHeld(UUID playerUuid) {
        return heldToHolder.containsKey(playerUuid);
    }

    public static void setHolding(UUID playerUuid, boolean holding) {
        if (!holding) {
            holderToHeld.remove(playerUuid);
        }
    }

    public static void setBeingHeld(UUID playerUuid, boolean beingHeld) {
        if (!beingHeld) {
            heldToHolder.remove(playerUuid);
        }
    }

    public static void setChargeProgress(UUID playerUuid, float progress) {
        chargeProgress.put(playerUuid, progress);
    }

    public static float getChargeProgress(UUID playerUuid) {
        return chargeProgress.getOrDefault(playerUuid, 0f);
    }

    public static void clear() {
        holderToHeld.clear();
        heldToHolder.clear();
        chargeProgress.clear();
    }
}