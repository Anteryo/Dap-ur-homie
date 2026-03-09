package com.cooptest.client;

import com.cooptest.PoseState;
import com.cooptest.client.ChargedDapClientHandler;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles player animations using Player Animation Library (PAL)
 *
 * Animation files are in: assets/testcoop/player_animations/
 * PAL will automatically load these JSON files.
 */
@Environment(EnvType.CLIENT)
public class CoopAnimationHandler {

    private static final String MOD_ID = "testcoop";

    // Animation layer ID for our mod - high priority (1500+) for gameplay animations
    public static final Identifier ANIMATION_LAYER_ID = Identifier.of(MOD_ID, "coop_animations");

    // Animation identifiers - match the animation NAMES in the JSON files
    public static final Identifier GRAB_HOLDING_ANIM = Identifier.of(MOD_ID, "grab_holding");
    public static final Identifier GRAB_HOLDING_CHARGE_ANIM = Identifier.of(MOD_ID, "grab_holding_charge");
    public static final Identifier GRAB_HOLDING_CHARGE_IDLE_ANIM = Identifier.of(MOD_ID, "grab_holding_charge_idle");
    public static final Identifier GRAB_THROW_ANIM = Identifier.of(MOD_ID, "grab_throw");
    public static final Identifier GRAB_READY_ANIM = Identifier.of(MOD_ID, "grab_ready");
    public static final Identifier GRAB_READY_IDLE_ANIM = Identifier.of(MOD_ID, "grab_ready_idle");
    public static final Identifier DAP_CHARGE_ANIM = Identifier.of(MOD_ID, "dap_charge");
    public static final Identifier DAP_CHARGE_IDLE_ANIM = Identifier.of(MOD_ID, "dap_charge_idle");
    public static final Identifier DAP_HIT_ANIM = Identifier.of(MOD_ID, "dap_hit");
    public static final Identifier FIRE_DAP_CHARGE_ANIM = Identifier.of(MOD_ID, "fire_dap_charge");
    public static final Identifier FIRE_DAP_CHARGE_IDLE_ANIM = Identifier.of(MOD_ID, "fire_dap_charge_idle");
    public static final Identifier FIRE_DAP_HIT_ANIM = Identifier.of(MOD_ID, "fire_dap_hit");
    public static final Identifier PUSH_START_ANIM = Identifier.of(MOD_ID, "push_start");
    public static final Identifier PUSH_IDLE_ANIM = Identifier.of(MOD_ID, "push_idle");
    public static final Identifier PUSH_ANIM = Identifier.of(MOD_ID, "push");
    public static final Identifier CATCH_ANIM = Identifier.of(MOD_ID, "catch");
    public static final Identifier MAHITO_ANIM = Identifier.of(MOD_ID, "mahito");

    // New high five animations
    public static final Identifier HIGHFIVE_START_ANIM = Identifier.of(MOD_ID, "highfive_start");
    public static final Identifier HIGHFIVE_END_ANIM = Identifier.of(MOD_ID, "highfive_end");
    public static final Identifier HIGHFIVE_HIT_ANIM = Identifier.of(MOD_ID, "highfive_hit");
    public static final Identifier HIGHFIVE_HIT_COMBO_ANIM = Identifier.of(MOD_ID, "highfive_hitcombo");


    // New fall dap animations (must be lowercase!)
    public static final Identifier DAP_CHARGE_FALL_START_ANIM = Identifier.of(MOD_ID, "dap_charge_fall_start");
    public static final Identifier DAP_CHARGE_FALLING_ANIM = Identifier.of(MOD_ID, "dap_charge_falling");
    public static final Identifier DAP_CHARGE_FALL_HIT_ANIM = Identifier.of(MOD_ID, "dap_charge_fall_hit");
    public static final Identifier SQUASHED_ANIM = Identifier.of(MOD_ID, "squashed");

    // New perfect dap and cancel animations
    public static final Identifier PERFECT_DAP_HIT_ANIM = Identifier.of(MOD_ID, "perfect_dap_hit");
    public static final Identifier DAP_DOWN_ANIM = Identifier.of(MOD_ID, "dap_down");

    // QTE Extender animations
    public static final Identifier DAP_HIT_WEAK_ANIM = Identifier.of(MOD_ID, "dap_hit_weak");
    public static final Identifier PERFECT_DAP_EXTEND1_P1_ANIM = Identifier.of(MOD_ID, "perfect_dap_extandp1");
    public static final Identifier PERFECT_DAP_EXTEND1_P2_ANIM = Identifier.of(MOD_ID, "perfect_dap_extandp2");
    public static final Identifier PERFECT_DAP_MYBOY_P1_ANIM = Identifier.of(MOD_ID, "perfect_dap_extande_myboyp1");
    public static final Identifier PERFECT_DAP_MYBOY_P2_ANIM = Identifier.of(MOD_ID, "perfect_dap_extande_myboyp2");

    // Extend stage 2 - both players same animation
    public static final Identifier PERFECT_DAP_EXTEND_BOTH_ANIM = Identifier.of(MOD_ID, "perfect_dap_extand_both");

    // Human shield animations
    public static final Identifier HOLD_SHIELD_ANIM = Identifier.of(MOD_ID, "hold_shield");
    public static final Identifier SHIELD_ANIM = Identifier.of(MOD_ID, "shield");

    // Mario jump animations
    public static final Identifier MARIO_JUMP_ANIM = Identifier.of(MOD_ID, "jumpmario");
    public static final Identifier POP_ANIM = Identifier.of(MOD_ID, "pop");

    // Hug animations
    public static final Identifier HUG_START_ANIM = Identifier.of(MOD_ID, "hug_start");
    public static final Identifier HUGGING_ANIM = Identifier.of(MOD_ID, "hugging");
    public static final Identifier HUGGING2_ANIM = Identifier.of(MOD_ID, "hugging2");
    public static final Identifier HUG_END_ANIM = Identifier.of(MOD_ID, "hugend");

    // Divine Flame combo animations
    public static final Identifier FIRE_DAP_HIT_PERFECT_ANIM = Identifier.of(MOD_ID, "fire_dap_hit_perfect");
    public static final Identifier FIRE_DAP_COMBO_P1_ANIM = Identifier.of(MOD_ID, "fire_dap_hitp1");
    public static final Identifier FIRE_DAP_COMBO_P2_ANIM = Identifier.of(MOD_ID, "fire_dap_hitp2");
    // DapHold animation identifiers
    public static final Identifier DAPHOLD_HIGHFIVE_ANIM = Identifier.of(MOD_ID, "highfive_dap");
    public static final Identifier DAPHOLD_DAP_ANIM = Identifier.of(MOD_ID, "dap_high");
    public static final Identifier DAPHOLD_DAPPING_ANIM = Identifier.of(MOD_ID, "dapping");
    public static final Identifier DAPHOLD_DAPPING_END_ANIM = Identifier.of(MOD_ID, "dapping_end");

    // Track player states
    private static final Map<UUID, PoseState> currentPoses = new HashMap<>();
    private static final Map<UUID, AnimState> animStates = new HashMap<>();

    // Animation state tracking - PUBLIC so network can access
    public enum AnimState {
        NONE,
        GRAB_READY,           // Arms out to grab
        GRAB_READY_IDLE,      // Waiting to grab (loop)
        GRAB_HOLDING,         // Holding player
        GRAB_CHARGING,        // Playing charge animation (once)
        GRAB_CHARGE_IDLE,     // Playing charge idle (loop)
        GRAB_THROWING,        // Playing throw animation
        DAP_CHARGING,         // Playing dap charge (once)
        DAP_CHARGE_IDLE,      // Playing dap charge idle (loop)
        DAP_HIT,              // Playing dap hit animation
        FIRE_DAP_CHARGING,    // Playing fire dap charge
        FIRE_DAP_CHARGE_IDLE, // Playing fire dap charge idle
        FIRE_DAP_HIT,         // Playing fire dap hit animation
        PUSH_START,           // Push initiation
        PUSH_IDLE,            // Waiting for push interaction
        PUSHING,              // Push action
        CATCHING,             // Playing catch animation
        MAHITO,               // Mahito troll animation (head grows)
        // New high five states
        HIGHFIVE_START,       // Starting high five (0.33 sec before can dap)
        HIGHFIVE_END,         // Ending high five (blocking state)
        HIGHFIVE_HIT,         // High five hit animation
        HIGHFIVE_HIT_COMBO,   // High five combo animation (double hit!)
        // New fall dap states
        DAP_CHARGE_FALL_START,  // Fall dap charge start (0.75 sec)
        DAP_CHARGE_FALLING,     // Fall dap idle (looping while falling)
        DAP_CHARGE_FALL_HIT,    // Fall dap hit animation
        SQUASHED,               // Being squashed by fall dap
        // New perfect dap and cancel states
        PERFECT_DAP_HIT,        // Perfect dap hit animation (blocks movement) - ordinal 26
        DAP_DOWN,               // Dap cancel animation (blocks actions) - ordinal 27
        // Human shield states
        HOLD_SHIELD,            // Holder animation when using human shield - ordinal 28
        SHIELD,                 // Held player animation when being used as shield - ordinal 29
        // Mario jump states
        MARIO_JUMP,             // Mario jump animation - ordinal 30
        POP,                    // Pop animation (target head squish) - ordinal 31
        HUG_START,      // hug_start.json (0.33s) - ordinal 32
        HUGGING,        // hugging.json (5.4s loop) - ordinal 33
        HUGGING2,       // hugging2.json (8.25s loop) - ordinal 34
        HUG_END,        // hugend.json (0.54s) - ordinal 35
        FIRE_DAP_COMBO_P1,  // fire_dap_hitp1.json (combo player 1) - ordinal 36
        FIRE_DAP_COMBO_P2,  // fire_dap_hitp2.json (combo player 2) - ordinal 37
        // DapHold states
        DAPHOLD_HIGHFIVE,   // highfive_dap.json  (ordinal 38) - highfive player side
        DAPHOLD_DAP,        // dap_high.json      (ordinal 39) - dap player side
        DAPHOLD_DAPPING,    // dapping.json loop  (ordinal 40) - both holding J
        DAPHOLD_DAPPING_END,// dapping_end.json   (ordinal 41) - releasing from loop
        // QTE EXTENDER STATES - AT THE END TO NOT BREAK ORDINALS!
        DAP_HIT_WEAK,           // Weak dap hit for tier 0-2 (shorter animation) - ordinal 42
        PERFECT_DAP_EXTEND1_P1, // Perfect dap extender stage 1 - Player 1 - ordinal 43
        PERFECT_DAP_EXTEND1_P2, // Perfect dap extender stage 1 - Player 2 - ordinal 44
        PERFECT_DAP_MYBOY_P1,   // Perfect dap myboy ultimate - Player 1 (can move!) - ordinal 45
        PERFECT_DAP_MYBOY_P2,   // Perfect dap myboy ultimate - Player 2 (can move!) - ordinal 46
        PERFECT_DAP_EXTEND_BOTH // Extend stage 2 - both players same animation - ordinal 47

    }

    // Helper to send anim state over network
    public static void syncAnimState(UUID playerId, AnimState state) {
        animStates.put(playerId, state);
        // Send to other players
        com.cooptest.PoseNetworking.sendAnimState(playerId, state.ordinal());
    }

    // Timing for animations (in ticks, 20 ticks = 1 sec)
    private static final Map<UUID, Long> chargeStartTime = new HashMap<>();
    private static final int DAP_CHARGE_DURATION_TICKS = 5;       // 0.25 sec for dap charge
    private static final int GRAB_CHARGE_DURATION_TICKS = 32;     // 1.60 sec for grab throw charge
    private static final int GRAB_READY_DURATION_TICKS = 5;       // 0.25 sec for grab ready
    private static final int PUSH_START_DURATION_TICKS = 9;       // 0.46 sec for push start
    private static final int THROW_ANIM_DURATION_TICKS = 6;       // 0.3 sec for throw animation

    // New timing constants
    private static final int HIGHFIVE_START_DURATION_TICKS = 7;   // 0.33 sec before can dap
    private static final int HIGHFIVE_END_DURATION_TICKS = 10;    // 0.5 sec end animation (blocking)
    private static final int DAP_HIT_EFFECT_DELAY_TICKS = 5;      // 0.25 sec before effects
    private static final int FALL_CHARGE_DURATION_TICKS = 15;     // 0.75 sec for fall charge
    private static final int PERFECT_DAP_HIT_DURATION_TICKS = 33; // 1.625 sec perfect dap animation (1625ms / 50ms = 32.5 ticks)
    private static final int DAP_DOWN_DURATION_TICKS = 7;         // 0.33 sec dap down animation
    private static final int FIRE_DAP_HIT_DURATION_TICKS = 46;    // 2.2917 sec fire dap hit (2292ms / 50ms = 45.84 ticks)
    private static final int DAP_HIT_DURATION_TICKS = 19;         // 0.96 sec normal dap hit (was 10 - too short!)

    // Mario jump timing
    private static final int MARIO_JUMP_DURATION_TICKS = 10;      // 0.5 sec mario jump animation
    private static final int POP_DURATION_TICKS = 8;              // 0.4167 sec pop animation (matches json)

    private static boolean initialized = false;

    /**
     * Register the animation layer - call from ClientModInitializer
     */
    public static void register() {
        if (!isPALAvailable()) {
            return;
        }

        try {
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                    ANIMATION_LAYER_ID,
                    1500,
                    player -> new PlayerAnimationController(player,
                            (controller, state, animSetter) -> PlayState.STOP
                    )
            );

            initialized = true;

            // Initialize first person animation system
            FirstPersonAnimationTest.init();

            // Register tick callback for animation state transitions
            ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        } catch (Exception e) {
            System.err.println("[CoopMoves] Failed to register PAL animations: " + e.getMessage());
        }
    }

    private static boolean isPALAvailable() {
        try {
            Class.forName("com.zigythebird.playeranimcore.animation.layered.IAnimation");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static PlayerAnimationController getController(AbstractClientPlayerEntity player) {
        return (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(
                player, ANIMATION_LAYER_ID
        );
    }

    /**
     * Update animation for a player based on their pose state
     */
    public static void updatePlayerAnimation(PlayerEntity player, PoseState newPose) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();
        PoseState currentPose = currentPoses.get(playerId);

        if (currentPose == newPose) return;

        currentPoses.put(playerId, newPose);

        // Check if this is the local player (for network sync)
        MinecraftClient client = MinecraftClient.getInstance();
        boolean isLocalPlayer = client.player != null && client.player.getUuid().equals(playerId);

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller == null) return;

            switch (newPose) {
                case GRABBED -> {
                    // Mixin handles superman pose - just track state
                    animStates.put(playerId, AnimState.NONE);
                }
                case GRAB_READY -> {
                    // Play grab ready animation then idle
                    controller.triggerAnimation(GRAB_READY_ANIM);
                    // Show both hands when pressing R
                    if (isLocalPlayer) {
                        FirstPersonAnimationTest.showBothHands();
                        syncAnimState(playerId, AnimState.GRAB_READY);
                    } else {
                        animStates.put(playerId, AnimState.GRAB_READY);
                    }
                    chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
                }
                case GRAB_HOLDING -> {
                    controller.triggerAnimation(GRAB_HOLDING_ANIM);
                    // Show both hands holding
                    if (isLocalPlayer) {
                        FirstPersonAnimationTest.showBothHands();
                        syncAnimState(playerId, AnimState.GRAB_HOLDING);
                    } else {
                        animStates.put(playerId, AnimState.GRAB_HOLDING);
                    }
                }
                case PUSH_IDLE -> {
                    // Play push start then idle
                    controller.triggerAnimation(PUSH_START_ANIM);
                    // Show both hands for push
                    if (isLocalPlayer) {
                        FirstPersonAnimationTest.showBothHands();
                        syncAnimState(playerId, AnimState.PUSH_START);
                    } else {
                        animStates.put(playerId, AnimState.PUSH_START);
                    }
                    chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
                }
                case NONE -> {
                    controller.stop();
                    if (isLocalPlayer) {
                        syncAnimState(playerId, AnimState.NONE);
                        // Also stop FP animation!
                        FirstPersonAnimationTest.stop();
                    } else {
                        animStates.put(playerId, AnimState.NONE);
                    }
                    chargeStartTime.remove(playerId);
                }
                default -> {
                    // Other poses handled by mixin
                }
            }
        } catch (Exception e) {
            System.err.println("[CoopMoves] Animation error: " + e.getMessage());
        }
    }

    /**
     * Start grab charge animation sequence
     */
    public static void startGrabCharge(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();
        AnimState currentState = animStates.get(playerId);

        // Only start charge if holding
        if (currentState != AnimState.GRAB_HOLDING && currentState != AnimState.GRAB_CHARGE_IDLE) {
            return;
        }

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(GRAB_HOLDING_CHARGE_ANIM);
                syncAnimState(playerId, AnimState.GRAB_CHARGING);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());

                // Show BOTH hands charging throw
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play throw animation
     */
    public static void playThrowAnimation(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(GRAB_THROW_ANIM);
                syncAnimState(playerId, AnimState.GRAB_THROWING);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());

                // Show BOTH hands throwing forward
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playThrow();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Start dap charge animation sequence
     */
    public static void startDapCharge(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAP_CHARGE_ANIM);
                syncAnimState(playerId, AnimState.DAP_CHARGING);

                // Show right arm pulling back
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playDapCharge();
                }
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Stop dap charge (on hit or cancel) - returns to normal, vanilla swing handles hit
     */
    public static void stopDapCharge(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();
        AnimState state = animStates.get(playerId);

        if (state == AnimState.DAP_CHARGING || state == AnimState.DAP_CHARGE_IDLE
                || state == AnimState.FIRE_DAP_CHARGING || state == AnimState.FIRE_DAP_CHARGE_IDLE) {
            try {
                PlayerAnimationController controller = getController(clientPlayer);
                if (controller != null) {
                    controller.stop();
                    syncAnimState(playerId, AnimState.NONE);
                    chargeStartTime.remove(playerId);

                    // Hide hands
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.stop();
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Cancel dap charge with dap_down animation (blocks actions until done - 0.33 sec)
     */
    public static void cancelDapCharge(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();
        AnimState state = animStates.get(playerId);

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                // Play dap_down if was in ANY dap-related state (charging, idle, or even hit)
                if (state == AnimState.DAP_CHARGING || state == AnimState.DAP_CHARGE_IDLE
                        || state == AnimState.FIRE_DAP_CHARGING || state == AnimState.FIRE_DAP_CHARGE_IDLE
                        || state == AnimState.DAP_HIT || state == AnimState.FIRE_DAP_HIT
                        || state == null) {  // Also play if state is null (edge case)
                    controller.triggerAnimation(DAP_DOWN_ANIM);
                    syncAnimState(playerId, AnimState.DAP_DOWN);
                }
                chargeStartTime.remove(playerId);
            }
        } catch (Exception e) {
            System.err.println("[CoopMoves] cancelDapCharge error: " + e.getMessage());
        }
    }

    /**
     * Tick - handle animation state transitions
     */
    public static void tick() {
        if (!initialized) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        long currentTime = client.world.getTime();
        UUID localPlayerId = client.player != null ? client.player.getUuid() : null;

        for (PlayerEntity player : client.world.getPlayers()) {
            if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) continue;

            UUID playerId = player.getUuid();
            AnimState state = animStates.get(playerId);
            if (state == null) continue;

            // Only sync for local player
            boolean isLocalPlayer = playerId.equals(localPlayerId);

            try {
                PlayerAnimationController controller = getController(clientPlayer);
                if (controller == null) continue;

                Long startTime = chargeStartTime.get(playerId);

                switch (state) {
                    case GRAB_CHARGING -> {
                        // Transition to charge idle after 1.6 sec
                        if (startTime != null && currentTime - startTime >= GRAB_CHARGE_DURATION_TICKS) {
                            controller.triggerAnimation(GRAB_HOLDING_CHARGE_IDLE_ANIM);
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.GRAB_CHARGE_IDLE);
                            } else {
                                animStates.put(playerId, AnimState.GRAB_CHARGE_IDLE);
                            }
                        }
                    }
                    case GRAB_THROWING -> {
                        // Return to holding after throw animation
                        if (startTime != null && currentTime - startTime >= THROW_ANIM_DURATION_TICKS) {
                            // Check if still holding someone
                            PoseState pose = currentPoses.get(playerId);
                            if (pose == PoseState.GRAB_HOLDING) {
                                controller.triggerAnimation(GRAB_HOLDING_ANIM);
                                if (isLocalPlayer) {
                                    syncAnimState(playerId, AnimState.GRAB_HOLDING);
                                } else {
                                    animStates.put(playerId, AnimState.GRAB_HOLDING);
                                }
                            } else {
                                controller.stop();
                                if (isLocalPlayer) {
                                    syncAnimState(playerId, AnimState.NONE);
                                } else {
                                    animStates.put(playerId, AnimState.NONE);
                                }
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case DAP_CHARGING -> {
                        // Transition to dap charge idle after 0.25 sec
                        if (startTime != null && currentTime - startTime >= DAP_CHARGE_DURATION_TICKS) {
                            controller.triggerAnimation(DAP_CHARGE_IDLE_ANIM);
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.DAP_CHARGE_IDLE);
                            } else {
                                animStates.put(playerId, AnimState.DAP_CHARGE_IDLE);
                            }
                        }
                    }
                    case GRAB_READY -> {
                        // Transition to grab ready idle after 0.25 sec
                        if (startTime != null && currentTime - startTime >= GRAB_READY_DURATION_TICKS) {
                            controller.triggerAnimation(GRAB_READY_IDLE_ANIM);
                            // Keep showing both hands in idle
                            if (isLocalPlayer) {
                                FirstPersonAnimationTest.showBothHands();
                                syncAnimState(playerId, AnimState.GRAB_READY_IDLE);
                            } else {
                                animStates.put(playerId, AnimState.GRAB_READY_IDLE);
                            }
                        }
                    }
                    case PUSH_START -> {
                        // Transition to push idle after 0.46 sec
                        if (startTime != null && currentTime - startTime >= PUSH_START_DURATION_TICKS) {
                            controller.triggerAnimation(PUSH_IDLE_ANIM);
                            // Keep showing both hands in idle
                            if (isLocalPlayer) {
                                FirstPersonAnimationTest.showBothHands();
                                syncAnimState(playerId, AnimState.PUSH_IDLE);
                            } else {
                                animStates.put(playerId, AnimState.PUSH_IDLE);
                            }
                        }
                    }
                    case PERFECT_DAP_HIT -> {
                        // Clear after 0.83 sec
                        if (startTime != null && currentTime - startTime >= PERFECT_DAP_HIT_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case DAP_DOWN -> {
                        // Clear after 0.33 sec
                        if (startTime != null && currentTime - startTime >= DAP_DOWN_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case DAP_HIT -> {
                        // Clear after 0.96 sec
                        if (startTime != null && currentTime - startTime >= DAP_HIT_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case FIRE_DAP_HIT -> {
                        // Clear after 2.29 sec (FULL ANIMATION LENGTH!)
                        if (startTime != null && currentTime - startTime >= FIRE_DAP_HIT_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case HIGHFIVE_START -> {
                        // Clear after 0.33 sec
                        if (startTime != null && currentTime - startTime >= HIGHFIVE_START_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case HIGHFIVE_END -> {
                        // Clear after 0.5 sec
                        if (startTime != null && currentTime - startTime >= HIGHFIVE_END_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    case HIGHFIVE_HIT -> {
                        // Clear after 0.5 sec (same as end)
                        if (startTime != null && currentTime - startTime >= HIGHFIVE_END_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    // Mario jump animation auto-clear
                    case MARIO_JUMP -> {
                        if (startTime != null && currentTime - startTime >= MARIO_JUMP_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    // Pop animation auto-clear
                    case POP -> {
                        if (startTime != null && currentTime - startTime >= POP_DURATION_TICKS) {
                            controller.stop();
                            if (isLocalPlayer) {
                                syncAnimState(playerId, AnimState.NONE);
                            } else {
                                animStates.put(playerId, AnimState.NONE);
                            }
                            chargeStartTime.remove(playerId);
                        }
                    }
                    // DAPHOLD animations are managed by DapHoldHandler
                    // They don't need tick-based re-triggering
                    case DAPHOLD_HIGHFIVE, DAPHOLD_DAP, DAPHOLD_DAPPING, DAPHOLD_DAPPING_END -> {
                        // Do nothing - DapHold manages its own animations
                    }
                    // FIRE_DAP_CHARGING transitions handled by ChargedDapClientHandler based on fire level
                    // Other states don't need tick updates
                }
            } catch (Exception e) {
                // Ignore tick errors
            }
        }
    }

    /**
     * Stop animation for a player
     */
    public static void stopAnimation(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.stop();
            }
        } catch (Exception e) {
            // Ignore
        }

        UUID playerId = player.getUuid();
        currentPoses.remove(playerId);
        animStates.remove(playerId);
        chargeStartTime.remove(playerId);
    }

    /**
     * Clean up when player disconnects
     */
    public static void cleanup(UUID playerId) {
        currentPoses.remove(playerId);
        animStates.remove(playerId);
        chargeStartTime.remove(playerId);
    }

    /**
     * Check if player is in charge idle state (for UI feedback)
     */
    public static boolean isInChargeIdle(UUID playerId) {
        AnimState state = animStates.get(playerId);
        return state == AnimState.GRAB_CHARGE_IDLE || state == AnimState.DAP_CHARGE_IDLE
                || state == AnimState.FIRE_DAP_CHARGE_IDLE;
    }

    // ==================== NEW ANIMATION METHODS ====================

    /**
     * Play dap hit animation (for third person view)
     */
    public static void playDapHit(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        // Block if player is locked by DapHold!
        if (DapHoldClientHandler.isAnimationLocked(playerId)) {
            return;
        }

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAP_HIT_ANIM);
                syncAnimState(playerId, AnimState.DAP_HIT);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());

                // Show right arm with dap hit animation (forward + handshake)
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playDapHit();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play fire dap hit animation (for third person view)
     */
    public static void playFireDapHit(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(FIRE_DAP_HIT_ANIM);
                syncAnimState(playerId, AnimState.FIRE_DAP_HIT);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Start fire dap charge animation
     */
    public static void startFireDapCharge(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(FIRE_DAP_CHARGE_ANIM);
                syncAnimState(playerId, AnimState.FIRE_DAP_CHARGING);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play fire dap charge idle animation (when fire bar is full)
     */
    public static void playFireDapChargeIdle(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(FIRE_DAP_CHARGE_IDLE_ANIM);
                syncAnimState(playerId, AnimState.FIRE_DAP_CHARGE_IDLE);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play regular dap charge idle animation (when fire drops)
     */
    public static void playDapChargeIdle(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAP_CHARGE_IDLE_ANIM);
                syncAnimState(playerId, AnimState.DAP_CHARGE_IDLE);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play push animation
     */
    public static void playPushAnimation(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(PUSH_ANIM);
                syncAnimState(playerId, AnimState.PUSHING);

                // Show BOTH hands pushing upward
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playPush();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play catch animation
     */
    public static void playCatchAnimation(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(CATCH_ANIM);
                syncAnimState(playerId, AnimState.CATCHING);

                // Show BOTH hands catching
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play Mahito troll animation (head grows then pops!)
     */
    public static void playMahitoAnimation(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(MAHITO_ANIM);
                syncAnimState(playerId, AnimState.MAHITO);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    // ==================== NEW HIGH FIVE ANIMATIONS ====================

    /**
     * Play high five start animation (0.33 sec before can dap)
     */
    public static void playHighFiveStart(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(HIGHFIVE_START_ANIM);
                syncAnimState(playerId, AnimState.HIGHFIVE_START);

                // Show right arm going UP for high five
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playHighFiveStart();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play high five end animation (blocking - can't do anything during this)
     */
    public static void playHighFiveEnd(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(HIGHFIVE_END_ANIM);
                syncAnimState(playerId, AnimState.HIGHFIVE_END);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play high five hit animation
     */
    public static void playHighFiveHit(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        // Block if player is locked by DapHold!
        if (DapHoldClientHandler.isAnimationLocked(playerId)) {
            return;
        }

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(HIGHFIVE_HIT_ANIM);
                syncAnimState(playerId, AnimState.HIGHFIVE_HIT);

                // Show right arm coming DOWN smoothly
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playHighFiveHit();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    // ==================== NEW FALL DAP ANIMATIONS ====================

    /**
     * Play fall dap charge start (0.75 sec full charge)
     */
    public static void playFallDapChargeStart(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAP_CHARGE_FALL_START_ANIM);
                syncAnimState(playerId, AnimState.DAP_CHARGE_FALL_START);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play fall dap falling idle (loop while falling)
     */
    public static void playFallDapFalling(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAP_CHARGE_FALLING_ANIM);
                syncAnimState(playerId, AnimState.DAP_CHARGE_FALLING);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play fall dap hit animation
     */
    public static void playFallDapHit(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAP_CHARGE_FALL_HIT_ANIM);
                syncAnimState(playerId, AnimState.DAP_CHARGE_FALL_HIT);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play squashed animation (victim of fall dap)
     */
    public static void playSquashed(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(SQUASHED_ANIM);
                syncAnimState(playerId, AnimState.SQUASHED);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play perfect dap hit animation (blocks movement until done - 0.83 sec)
     */
    public static void playPerfectDapHit(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(PERFECT_DAP_HIT_ANIM);
                syncAnimState(playerId, AnimState.PERFECT_DAP_HIT);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());

                // Show right arm with PERFECT dap (longer handshake!)
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.playPerfectDap();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play dap down animation (when canceling dap charge - blocks actions for 0.33 sec)
     */
    public static void playDapDown(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAP_DOWN_ANIM);
                syncAnimState(playerId, AnimState.DAP_DOWN);
                chargeStartTime.put(playerId, MinecraftClient.getInstance().world.getTime());
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play hold_shield animation (for the holder)
     */
    public static void playHoldShield(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(HOLD_SHIELD_ANIM);
                syncAnimState(playerId, AnimState.HOLD_SHIELD);

                // Show BOTH hands holding shield
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play shield animation (for the held player being used as shield)
     */
    public static void playShield(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(SHIELD_ANIM);
                syncAnimState(playerId, AnimState.SHIELD);

                // Show BOTH hands in shield pose
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Stop shield animations (when exiting shield mode)
     */
    public static void stopShieldAnimation(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.stop();
                syncAnimState(playerId, AnimState.NONE);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Check if player is in a blocking state (can't do any action)
     */
    public static boolean isInBlockingState(UUID playerId) {
        AnimState state = animStates.get(playerId);
        return state == AnimState.HIGHFIVE_END
                || state == AnimState.SQUASHED
                || state == AnimState.PERFECT_DAP_HIT
                || state == AnimState.DAP_DOWN;
    }

    /**
     * Get current animation state for a player (for network sync)
     */
    public static AnimState getAnimState(UUID playerId) {
        return animStates.getOrDefault(playerId, AnimState.NONE);
    }

    /**
     * Set animation state from network (for other players)
     */
    public static void setAnimStateFromNetwork(PlayerEntity player, int stateOrdinal) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        // Define client ONCE for entire function
        MinecraftClient client = MinecraftClient.getInstance();

        // Handle NONE state as cleanup
        if (stateOrdinal == 0) {
            UUID playerId = player.getUuid();
            try {
                PlayerAnimationController controller = getController(clientPlayer);
                if (controller != null) {
                    controller.stop();
                }
            } catch (Exception e) {
                // Ignore
            }
            animStates.remove(playerId);
            currentPoses.remove(playerId);
            return;
        }

        AnimState state = AnimState.values()[stateOrdinal];
        UUID playerId = player.getUuid();
        AnimState current = animStates.get(playerId);

        // DON'T skip if same state - animations need to trigger every time!
        // This was preventing fire dap from working!

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller == null) return;

            switch (state) {
                case GRAB_READY -> {
                    controller.triggerAnimation(GRAB_READY_ANIM);
                    // Show both hands ready to grab
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case GRAB_READY_IDLE -> {
                    controller.triggerAnimation(GRAB_READY_IDLE_ANIM);
                    // Show both hands ready (idle)
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case GRAB_HOLDING -> {
                    controller.triggerAnimation(GRAB_HOLDING_ANIM);
                    // Show both hands holding
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case GRAB_CHARGING -> {
                    controller.triggerAnimation(GRAB_HOLDING_CHARGE_ANIM);
                    // Show BOTH hands charging throw
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case GRAB_CHARGE_IDLE -> controller.triggerAnimation(GRAB_HOLDING_CHARGE_IDLE_ANIM);
                case GRAB_THROWING -> {
                    controller.triggerAnimation(GRAB_THROW_ANIM);
                    // Show BOTH hands throwing
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playThrow();
                    }
                }
                case DAP_CHARGING -> {
                    controller.triggerAnimation(DAP_CHARGE_ANIM);
                    // Show right arm pulling back (charging dap)
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playDapCharge();
                    }
                }
                case DAP_CHARGE_IDLE -> controller.triggerAnimation(DAP_CHARGE_IDLE_ANIM);
                case DAP_HIT -> {
                    controller.triggerAnimation(DAP_HIT_ANIM);
                    // Show right arm with dap hit (forward + handshake)
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playDapHit();
                    }
                }
                case FIRE_DAP_CHARGING -> controller.triggerAnimation(FIRE_DAP_CHARGE_ANIM);
                case FIRE_DAP_CHARGE_IDLE -> controller.triggerAnimation(FIRE_DAP_CHARGE_IDLE_ANIM);
                case FIRE_DAP_HIT -> {
                    controller.triggerAnimation(FIRE_DAP_HIT_ANIM);
                    // ALWAYS show both hands for fire dap!
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case PUSH_START -> {
                    controller.triggerAnimation(PUSH_START_ANIM);
                    // Show both hands
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case PUSH_IDLE -> {
                    controller.triggerAnimation(PUSH_IDLE_ANIM);
                    // Show both hands (idle)
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case PUSHING -> {
                    controller.triggerAnimation(PUSH_ANIM);
                    // Show BOTH hands pushing upward
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playPush();
                    }
                }
                case CATCHING -> {
                    controller.triggerAnimation(CATCH_ANIM);
                    // Show BOTH hands catching
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case MAHITO -> controller.triggerAnimation(MAHITO_ANIM);
                // New high five animations
                case HIGHFIVE_START -> {
                    controller.triggerAnimation(HIGHFIVE_START_ANIM);
                    // Show right arm going UP
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playHighFiveStart();
                    }
                }
                case HIGHFIVE_END -> controller.triggerAnimation(HIGHFIVE_END_ANIM);
                case HIGHFIVE_HIT -> {
                    controller.triggerAnimation(HIGHFIVE_HIT_ANIM);
                    // Show right arm coming DOWN smoothly
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playHighFiveHit();
                    }
                }
                case HIGHFIVE_HIT_COMBO -> {
                    controller.triggerAnimation(HIGHFIVE_HIT_COMBO_ANIM);
                    // Show RIGHT arm frozen, LEFT arm going up (COMBO!)
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playHighFiveCombo();
                    }
                }
                // New fall dap animations
                case DAP_CHARGE_FALL_START -> controller.triggerAnimation(DAP_CHARGE_FALL_START_ANIM);
                case DAP_CHARGE_FALLING -> controller.triggerAnimation(DAP_CHARGE_FALLING_ANIM);
                case DAP_CHARGE_FALL_HIT -> controller.triggerAnimation(DAP_CHARGE_FALL_HIT_ANIM);
                case SQUASHED -> controller.triggerAnimation(SQUASHED_ANIM);
                case PERFECT_DAP_HIT -> {
                    controller.triggerAnimation(PERFECT_DAP_HIT_ANIM);
                    // Show right arm with PERFECT dap (longer handshake)
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playPerfectDap();
                    }
                }
                // ===== PERFECT DAP EXTENDER ANIMATIONS (QTE System!) =====
                case PERFECT_DAP_EXTEND1_P1 -> {
                    controller.triggerAnimation(PERFECT_DAP_EXTEND1_P1_ANIM);
                    // Show BOTH hands for extender!
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case PERFECT_DAP_EXTEND1_P2 -> {
                    controller.triggerAnimation(PERFECT_DAP_EXTEND1_P2_ANIM);
                    // Show BOTH hands for extender 2!
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case PERFECT_DAP_MYBOY_P1 -> {
                    controller.triggerAnimation(PERFECT_DAP_MYBOY_P1_ANIM);
                    // Show BOTH hands for MYBOY ULTIMATE!
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case PERFECT_DAP_MYBOY_P2 -> {
                    controller.triggerAnimation(PERFECT_DAP_MYBOY_P2_ANIM);
                    // Show BOTH hands for MYBOY P2!
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case PERFECT_DAP_EXTEND_BOTH -> {
                    controller.triggerAnimation(PERFECT_DAP_EXTEND_BOTH_ANIM);
                    // Show BOTH hands for extend stage 2
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                // =============================================================
                case DAP_DOWN -> controller.triggerAnimation(DAP_DOWN_ANIM);
                // Human shield animations
                case HOLD_SHIELD -> {
                    controller.triggerAnimation(HOLD_SHIELD_ANIM);
                    // Show BOTH hands holding shield
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case SHIELD -> {
                    controller.triggerAnimation(SHIELD_ANIM);
                    // Show BOTH hands in shield pose
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                // Mario jump animations
                case MARIO_JUMP -> controller.triggerAnimation(MARIO_JUMP_ANIM);
                case POP -> controller.triggerAnimation(POP_ANIM);
                case HUG_START -> {
                    controller.triggerAnimation(HUG_START_ANIM);
                    // Show BOTH arms reaching out for hug
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playHug();
                    }
                }
                case HUGGING -> controller.triggerAnimation(HUGGING_ANIM);
                case HUGGING2 -> controller.triggerAnimation(HUGGING2_ANIM);
                case HUG_END -> {
                    controller.triggerAnimation(HUG_END_ANIM);
                    // Stop showing hands when hug ends
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.stop();
                    }
                }
                case FIRE_DAP_COMBO_P1 -> {
                    controller.triggerAnimation(FIRE_DAP_COMBO_P1_ANIM);
                    // Show BOTH hands for Fire Dap combo
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case FIRE_DAP_COMBO_P2 -> {
                    controller.triggerAnimation(FIRE_DAP_COMBO_P2_ANIM);
                    // Show BOTH hands for Fire Dap combo
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case DAPHOLD_HIGHFIVE -> {
                    controller.triggerAnimation(DAPHOLD_HIGHFIVE_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playHighFiveStart();
                    }
                }
                case DAPHOLD_DAP -> {
                    controller.triggerAnimation(DAPHOLD_DAP_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.playDapHit();
                    }
                }
                case DAPHOLD_DAPPING -> {
                    controller.triggerAnimation(DAPHOLD_DAPPING_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                    }
                }
                case DAPHOLD_DAPPING_END -> {
                    controller.triggerAnimation(DAPHOLD_DAPPING_END_ANIM);
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.stop();
                    }
                }
                case NONE -> {
                    controller.stop();
                    // Stop showing hands
                    if (client.player != null && client.player.getUuid().equals(playerId)) {
                        FirstPersonAnimationTest.stop();
                    }
                }
            }

            animStates.put(playerId, state);
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play fire dap hit perfect animation (Divine Flame start)
     */
    public static void playFireDapHitPerfect(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(FIRE_DAP_HIT_PERFECT_ANIM);
                syncAnimState(playerId, AnimState.FIRE_DAP_HIT);

                // Show both hands in first person for Divine Flame
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play fire dap combo P1 animation
     */
    public static void playFireDapComboP1(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(FIRE_DAP_COMBO_P1_ANIM);
                syncAnimState(playerId, AnimState.FIRE_DAP_HIT);

                // Show BOTH hands for Divine Flame combo
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Play fire dap combo P2 animation
     */
    public static void playFireDapComboP2(PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        UUID playerId = player.getUuid();

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(FIRE_DAP_COMBO_P2_ANIM);
                syncAnimState(playerId, AnimState.FIRE_DAP_HIT);

                // Show BOTH hands for Divine Flame combo
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    // ===================== DAPHOLD METHODS =====================

    /**
     * Play the correct start animation for a DapHold interaction.
     * role=0 → highfive_dap (highfive player), role=1 → dap_high (dap player)
     */
    public static void playDapHoldStart(net.minecraft.entity.player.PlayerEntity player, int role) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();


        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                // Clear any existing animation state first
                animStates.put(playerId, AnimState.NONE);

                MinecraftClient client = MinecraftClient.getInstance();
                boolean isLocalPlayer = client.player != null && client.player.getUuid().equals(playerId);

                // Play the animation immediately (no delay!)
                if (role == 0) {
                    controller.triggerAnimation(DAPHOLD_HIGHFIVE_ANIM);
                    syncAnimState(playerId, AnimState.DAPHOLD_HIGHFIVE);

                    // First person: Right hand going up
                    if (isLocalPlayer) {
                        FirstPersonAnimationTest.playHighFiveStart();
                    }
                } else {
                    controller.triggerAnimation(DAPHOLD_DAP_ANIM);
                    syncAnimState(playerId, AnimState.DAPHOLD_DAP);

                    // First person: Right hand forward for dap
                    if (isLocalPlayer) {
                        FirstPersonAnimationTest.playDapHit();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Switch player to the looping dapping animation (both held J).
     */
    public static void playDapHoldDapping(net.minecraft.entity.player.PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAPHOLD_DAPPING_ANIM);
                syncAnimState(playerId, AnimState.DAPHOLD_DAPPING);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
        }
    }

    /**
     * Resume the tail of the animation from 0.75s after J release.
     * role=0 → highfive_dap tail, role=1 → dap_high tail
     */
    public static void playDapHoldResume(net.minecraft.entity.player.PlayerEntity player, int role) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                if (role == 0) {
                    controller.triggerAnimation(DAPHOLD_HIGHFIVE_ANIM);
                    syncAnimState(playerId, AnimState.DAPHOLD_HIGHFIVE);
                } else {
                    controller.triggerAnimation(DAPHOLD_DAP_ANIM);
                    syncAnimState(playerId, AnimState.DAPHOLD_DAP);
                }
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    FirstPersonAnimationTest.stop();
                }
            }
        } catch (Exception e) {
        }
    }

    /**
     * Play dapping_end animation when releasing from dapping loop.
     */
    public static void playDapHoldEnd(net.minecraft.entity.player.PlayerEntity player) {
        if (!initialized) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;
        UUID playerId = player.getUuid();
        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.triggerAnimation(DAPHOLD_DAPPING_END_ANIM);
                syncAnimState(playerId, AnimState.DAPHOLD_DAPPING_END);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    // Show both hands for dapping_end animation
                    FirstPersonAnimationTest.showBothHands();
                }
            }
        } catch (Exception e) {
        }
    }
}