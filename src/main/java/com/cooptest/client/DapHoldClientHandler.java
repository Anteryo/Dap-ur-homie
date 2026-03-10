package com.cooptest.client;

import com.cooptest.DapHoldHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DapHoldClientHandler - Client side with COMPLETE animation locking
 */
public class DapHoldClientHandler {

    // My state in current pair
    private static int myRole       = -1;
    private static UUID myPartnerId = null;
    private static boolean windowOpen = false;
    private static boolean looping    = false;

    // CRITICAL: Lock prevents ALL other animations from playing
    private static boolean animationLocked = false;
    private static final Set<UUID> lockedPlayers = new HashSet<>();

    // Track which animations we've played (by "UUID-role" to allow same player both roles in test)
    private static final Set<String> startAnimPlayed = new HashSet<>();
    private static final Set<UUID> loopAnimPlayed  = new HashSet<>();

    // Movement freeze map
    private static final Map<UUID, Boolean> freezeMap = new HashMap<>();

    // J key state tracking
    private static boolean jWasHeld = false;

    public static void register() {

        // S→C: Start - LOCK animations and play DapHold animations
        ClientPlayNetworking.registerGlobalReceiver(DapHoldHandler.DapHoldStartPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    MinecraftClient client = ctx.client();
                    if (client.player == null || client.world == null) return;

                    UUID localId = client.player.getUuid();
                    UUID payloadId = payload.playerId();
                    UUID partnerId = payload.partnerId();

                    System.out.println("[DapHold Client] Received payload for " + payloadId + " partner=" + partnerId + " role=" + payload.role());

                    // LOCK animations for both players
                    lockedPlayers.add(payloadId);
                    lockedPlayers.add(partnerId);

                    // Check if this payload is for us
                    if (payloadId.equals(localId)) {
                        // If starting fresh (not already in interaction), clear tracking
                        if (myRole == -1) {
                            startAnimPlayed.clear();
                            loopAnimPlayed.clear();
                            System.out.println("[DapHold Client] Cleared animation tracking for new interaction");
                        }

                        myRole      = payload.role();
                        myPartnerId = partnerId;
                        windowOpen  = false;
                        looping     = false;
                        animationLocked = true;

                        System.out.println("[DapHold Client] Starting! My role=" + myRole);
                    }

                    // Play animation ONLY if we haven't already played it for this player+role
                    // Key format: "UUID-role" allows same player to have both roles in single player test
                    String animKey = payloadId.toString() + "-" + payload.role();
                    if (!startAnimPlayed.contains(animKey)) {
                        startAnimPlayed.add(animKey);

                        PlayerEntity targetPlayer = client.world.getPlayerByUuid(payloadId);
                        if (targetPlayer != null) {
                            CoopAnimationHandler.playDapHoldStart(targetPlayer, payload.role());
                            System.out.println("[DapHold Client] ✓ Playing anim for " + payloadId + " role=" + payload.role());
                        }
                    } else {
                        System.out.println("[DapHold Client] Skipping - already played " + animKey);
                    }
                }));

        // S→C: J window open/close
        ClientPlayNetworking.registerGlobalReceiver(DapHoldHandler.DapHoldWindowPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (myRole == -1) return;
                    windowOpen = payload.open();
                }));

        // S→C: Switch to dapping loop
        ClientPlayNetworking.registerGlobalReceiver(DapHoldHandler.DapHoldLoopPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (myRole == -1) return;
                    MinecraftClient client = ctx.client();
                    if (client.player == null || client.world == null) return;

                    looping = payload.looping();

                    if (looping) {
                        System.out.println("[DapHold Client] 🔥 DAPPING LOOP! 🔥");

                        // Play dapping for BOTH
                        CoopAnimationHandler.playDapHoldDapping(client.player);

                        if (myPartnerId != null) {
                            PlayerEntity partner = client.world.getPlayerByUuid(myPartnerId);
                            if (partner != null) {
                                CoopAnimationHandler.playDapHoldDapping(partner);
                            }
                        }
                    }
                }));

        // S→C: End interaction
        ClientPlayNetworking.registerGlobalReceiver(DapHoldHandler.DapHoldEndPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (myRole == -1) return;
                    MinecraftClient client = ctx.client();
                    if (client.player == null || client.world == null) return;

                    boolean wasLooping = payload.wasLooping();

                    if (wasLooping) {
                        // Play dapping_end for BOTH
                        CoopAnimationHandler.playDapHoldEnd(client.player);

                        if (myPartnerId != null) {
                            PlayerEntity partner = client.world.getPlayerByUuid(myPartnerId);
                            if (partner != null) {
                                CoopAnimationHandler.playDapHoldEnd(partner);
                            }
                        }
                    }

                    // Cleanup after animation
                    new Thread(() -> {
                        try { Thread.sleep(1100); } catch (InterruptedException ignored) {}
                        client.execute(() -> {
                            FirstPersonAnimationTest.stop();

                            // UNLOCK animations
                            UUID localId = client.player != null ? client.player.getUuid() : null;
                            if (localId != null) {
                                lockedPlayers.remove(localId);
                                if (myPartnerId != null) {
                                    lockedPlayers.remove(myPartnerId);
                                }
                                System.out.println("[DapHold Client] UNLOCKED animations");
                            }

                            animationLocked = false;
                            myRole      = -1;
                            myPartnerId = null;
                            looping     = false;
                            windowOpen  = false;
                            jWasHeld    = false;

                            // Clear animation tracking
                            startAnimPlayed.clear();
                            loopAnimPlayed.clear();
                        });
                    }).start();
                }));

        // S→C: Freeze / unfreeze
        ClientPlayNetworking.registerGlobalReceiver(DapHoldHandler.DapHoldFreezePayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    freezeMap.put(payload.playerId(), payload.frozen());
                }));

        // Tick: send J hold/release
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (myRole == -1) {
                jWasHeld = false;
                return;
            }

            if (!windowOpen && !looping) {
                jWasHeld = false;
                return;
            }

            boolean jHeld = ChargedDapClientHandler.isFireDapJKeyHeld();

            if (jHeld) {
                ClientPlayNetworking.send(new DapHoldHandler.DapHoldJHoldPayload());
                jWasHeld = true;
            } else if (jWasHeld) {
                ClientPlayNetworking.send(new DapHoldHandler.DapHoldJReleasePayload());
                jWasHeld = false;
            }
        });

        System.out.println("[DapHold Client] Registered!");
    }

    // ---- Public queries ----

    public static boolean isLocalPlayerFrozen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        return freezeMap.getOrDefault(client.player.getUuid(), false);
    }

    public static boolean isPlayerFrozen(UUID playerId) {
        return freezeMap.getOrDefault(playerId, false);
    }

    /**
     * Check if player's animations are locked by DapHold
     * CRITICAL: Use this in CoopAnimationHandler to block other animations!
     */
    public static boolean isAnimationLocked(UUID playerId) {
        return lockedPlayers.contains(playerId);
    }
}