package com.cooptest.client;

import com.cooptest.ChargedDapHandler;
import com.cooptest.HighFiveHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class HighFiveClientHandler {

    private static KeyBinding highFiveKey;
    private static boolean wasKeyPressed = false;
    private static long flashStartTime = 0;
    private static int currentTier = 0;
    private static final Map<UUID, Boolean> raisedHands = new HashMap<>();
    private static final Map<UUID, Long> highFiveAnimStart = new HashMap<>();
    public static final long HIGH_FIVE_ANIM_DURATION = 800; 
    private static long comboWindowStart = 0;
    private static boolean inComboWindow = false;
    private static final long COMBO_WINDOW_MS = 400; // 0.4 seconds
    private static final Map<UUID, Boolean> frozenPlayers = new HashMap<>();
    public static void register() {
        highFiveKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.coopmoves.highfive", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.coopmoves"
        ));

        ClientPlayNetworking.registerGlobalReceiver(HighFiveHandler.HandRaisedSyncPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        raisedHands.put(payload.playerId(), payload.raised());

                        MinecraftClient client = context.client();
                        if (client.player != null && client.player.getUuid().equals(payload.playerId())) {
                        }
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(HighFiveHandler.HighFiveAnimPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        UUID playerId = payload.playerId();
                        int animState = payload.animState();

                        MinecraftClient client = context.client();
                        if (client.world != null) {
                            boolean found = false;
                            for (net.minecraft.entity.player.PlayerEntity player : client.world.getPlayers()) {
                                if (player.getUuid().equals(playerId)) {
                                    found = true;
                                    switch (animState) {
                                        case 1 -> CoopAnimationHandler.playHighFiveStart(player);  // START
                                        case 2 -> CoopAnimationHandler.playHighFiveEnd(player);    // END
                                        case 3 -> CoopAnimationHandler.playHighFiveHit(player);    // HIT
                                    }
                                    break;
                                }
                            }
                            if (!found) {
                            }
                        }
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(HighFiveHandler.HighFiveSuccessPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        onHighFiveSuccess(payload.x(), payload.y(), payload.z(),
                                payload.player1(), payload.player2(), payload.tier());
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(HighFiveHandler.ComboWindowPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        comboWindowStart = System.currentTimeMillis();
                        inComboWindow = true;
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(HighFiveHandler.ComboWindowClosePayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        inComboWindow = false;

                        MinecraftClient client = context.client();
                        if (client.player != null) {
                            UUID myId = client.player.getUuid();
                            CoopAnimationHandler.syncAnimState(myId, CoopAnimationHandler.AnimState.NONE);
                        }

                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(HighFiveHandler.FreezeStatePayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        frozenPlayers.put(payload.playerId(), payload.frozen());
                    });
                }
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            UUID myId = client.player.getUuid();
            Long animStart = highFiveAnimStart.get(myId);
            if (animStart != null) {
                long elapsed = System.currentTimeMillis() - animStart;
                if (elapsed > HIGH_FIVE_ANIM_DURATION) {
                
                    var currentState = CoopAnimationHandler.getAnimState(myId);
                    if (currentState == CoopAnimationHandler.AnimState.HIGHFIVE_HIT) {
                        highFiveAnimStart.remove(myId);
                        raisedHands.put(myId, false);
                        CoopAnimationHandler.syncAnimState(myId, CoopAnimationHandler.AnimState.NONE);
                    } else if (currentState == CoopAnimationHandler.AnimState.HIGHFIVE_HIT_COMBO) {
                        highFiveAnimStart.remove(myId);
                    }
                }
            }

            boolean isKeyPressed = highFiveKey.isPressed();

            if (isKeyPressed && !wasKeyPressed) {
                if (ChargedDapClientHandler.isPlayerFrozen() && !QTEClientHandler.isActive()) {
                    wasKeyPressed = true;
                    return;
                }
            }
            
            if (isKeyPressed && !wasKeyPressed) {
                if (QTEClientHandler.isActive()) {
                    QTEClientHandler.handleKeyPress("H");
                    wasKeyPressed = true;
                    return; 
                }
            }
          
            if (inComboWindow && isKeyPressed && !wasKeyPressed) {
                ClientPlayNetworking.send(new HighFiveHandler.ComboRequestPayload());
                inComboWindow = false; // Close window after pressing
            }

            if (inComboWindow && System.currentTimeMillis() - comboWindowStart > COMBO_WINDOW_MS) {
                inComboWindow = false;
            }

            if (!inComboWindow && isKeyPressed && !wasKeyPressed) {
                if (ChargedDapClientHandler.isLocalPlayerCharging()) {
                    client.player.sendMessage(Text.literal("§cCan't high five while charging dap!"), true);
                } else if (!client.player.getMainHandStack().isEmpty() ||
                        !client.player.getOffHandStack().isEmpty()) {
                    client.player.sendMessage(Text.literal("§cHands must be empty for high five!"), true);
                } else {
                    raisedHands.put(client.player.getUuid(), true);
                    ClientPlayNetworking.send(new HighFiveHandler.HighFiveRequestPayload());
                }
            }
            wasKeyPressed = isKeyPressed;
        });

        HudRenderCallback.EVENT.register(HighFiveClientHandler::renderHUD);
    }

    private static void onHighFiveSuccess(double x, double y, double z, UUID player1, UUID player2, int tier) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        UUID myId = client.player.getUuid();


        if (myId.equals(player1) || myId.equals(player2)) {
            boolean before = raisedHands.getOrDefault(myId, false);
        }

        raisedHands.put(player1, false);
        raisedHands.put(player2, false);

        if (myId.equals(player1) || myId.equals(player2)) {
            boolean after = raisedHands.getOrDefault(myId, false);
        }


        long now = System.currentTimeMillis();
        highFiveAnimStart.put(player1, now);
        highFiveAnimStart.put(player2, now);

        if (myId.equals(player1) || myId.equals(player2)) {
            flashStartTime = now;
            currentTier = tier;

            client.player.swingHand(Hand.MAIN_HAND);

            // Message based on tier
            String message = switch (tier) {
                case 0 -> "§6 High Five!";
                case 1 -> "§e Nice High Five! ";
                case 2 -> "§a§l BIG HIGH FIVE! ";
                case 3 -> "§c§l EXPLOSIVE HIGH FIVE!";
                default -> "§6 High Five!";
            };
            client.player.sendMessage(Text.literal(message), true);
        }
    }

    private static void renderHUD(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

       
        long flashDuration = switch (currentTier) {
            case 0 -> 200;
            case 1 -> 250;
            case 2 -> 350;
            case 3 -> 500;
            default -> 200;
        };

        long timeSinceFlash = System.currentTimeMillis() - flashStartTime;
        if (timeSinceFlash < flashDuration) {
            float progress = (float) timeSinceFlash / flashDuration;

            int baseAlpha = switch (currentTier) {
                case 0 -> 120;
                case 1 -> 150;
                case 2 -> 200;
                case 3 -> 255;
                default -> 120;
            };

            int alpha = (int) ((1.0f - progress) * baseAlpha);

            int flashColor = switch (currentTier) {
                case 0 -> (alpha << 24) | 0xFFFF99;  // Yellow
                case 1 -> (alpha << 24) | 0xFFCC00;  // Gold
                case 2 -> (alpha << 24) | 0x00FF88;  // Green
                case 3 -> (alpha << 24) | 0xFFFFFF;  // WHITE
                default -> (alpha << 24) | 0xFFFF99;
            };

            context.fill(0, 0, screenWidth, screenHeight, flashColor);
        }

        UUID myId = client.player.getUuid();
        boolean handRaised = raisedHands.getOrDefault(myId, false);

        if (handRaised) {
            if (System.currentTimeMillis() % 2000 < 50) {  // Log every 2 seconds
            }

            String text = " Ready for High Five!";
            int textWidth = client.textRenderer.getWidth(text);
            int textX = (screenWidth - textWidth) / 2;
            int textY = screenHeight / 2 - 40;

            float pulse = (float) (Math.sin(System.currentTimeMillis() / 150.0) * 0.3 + 0.7);
            int alpha = (int) (pulse * 255);
            int color = (alpha << 24) | 0xFFFF00;

            context.drawText(client.textRenderer, text, textX, textY, color, true);
        }

        if (inComboWindow) {
            long elapsed = System.currentTimeMillis() - comboWindowStart;
            long remaining = COMBO_WINDOW_MS - elapsed;

            if (remaining > 0) {
                String keyName = highFiveKey.getBoundKeyLocalizedText().getString().toUpperCase();
                String text = "§e§l PRESS " + keyName + "! ";
                int textWidth = client.textRenderer.getWidth(text);
                int textX = (screenWidth - textWidth) / 2;
                int textY = screenHeight / 2 + 10;

                float pulse = (float) (Math.sin(System.currentTimeMillis() / 80.0) * 0.4 + 0.6);
                int alpha = (int) (pulse * 255);

                float timeProgress = (float) elapsed / COMBO_WINDOW_MS;
                int color;
                if (timeProgress < 0.5f) {
                    color = (alpha << 24) | 0xFFFF00;
                } else {
                    color = (alpha << 24) | 0xFF0000;
                }

                context.drawText(client.textRenderer, text, textX, textY, color, true);

                int barWidth = 100;
                int barHeight = 3;
                int barX = (screenWidth - barWidth) / 2;
                int barY = textY + 12;

                context.fill(barX, barY, barX + barWidth, barY + barHeight, 0x80000000);

                int fillWidth = (int) (barWidth * (1.0f - timeProgress));
                context.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFFFFFF00);
            }
        }
    }

    public static boolean hasHandRaised(UUID playerId) {
        return raisedHands.getOrDefault(playerId, false);
    }

  
    public static String getHighFiveBlockReason() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return "unknown";
        UUID myId = client.player.getUuid();

        if (highFiveKey != null && highFiveKey.isPressed()) {
            return "H key pressed";
        }

        if (raisedHands.getOrDefault(myId, false)) {
            return "raisedHands map = true";
        }

        Long animStart = highFiveAnimStart.get(myId);
        if (animStart != null) {
            long elapsed = System.currentTimeMillis() - animStart;
            if (elapsed <= HIGH_FIVE_ANIM_DURATION) {
                return "Animation playing (" + elapsed + "ms / " + HIGH_FIVE_ANIM_DURATION + "ms)";
            }
        }

        var animState = CoopAnimationHandler.getAnimState(myId);
        if (animState == CoopAnimationHandler.AnimState.HIGHFIVE_START
                || animState == CoopAnimationHandler.AnimState.HIGHFIVE_HIT) {
            return "Animation state = " + animState;
        }

        return "unknown";
    }

   
    public static boolean isLocalPlayerInHighFive() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        UUID myId = client.player.getUuid();

        if (highFiveKey != null && highFiveKey.isPressed()) {
            return true;
        }

        if (raisedHands.getOrDefault(myId, false)) {
            return true;
        }

        Long animStart = highFiveAnimStart.get(myId);
        if (animStart != null) {
            long elapsed = System.currentTimeMillis() - animStart;
            if (elapsed > HIGH_FIVE_ANIM_DURATION) {
                highFiveAnimStart.remove(myId);
                raisedHands.put(myId, false);  // Clear hand raised too!
            } else {
                return true;
            }
        }

        var animState = CoopAnimationHandler.getAnimState(myId);
        boolean inAnim = animState == CoopAnimationHandler.AnimState.HIGHFIVE_START
                || animState == CoopAnimationHandler.AnimState.HIGHFIVE_HIT;

        if (inAnim) {
        } else if (animState == CoopAnimationHandler.AnimState.HIGHFIVE_END) {
            raisedHands.put(myId, false);
            highFiveAnimStart.remove(myId);
        }

        return inAnim;
    }

    public static float getHighFiveAnimProgress(UUID playerId) {
        Long startTime = highFiveAnimStart.get(playerId);
        if (startTime == null) return -1f;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > HIGH_FIVE_ANIM_DURATION) {
            highFiveAnimStart.remove(playerId);
            return -1f;
        }

        return (float) elapsed / HIGH_FIVE_ANIM_DURATION;
    }

    
    public static void cleanup(UUID playerId) {
        raisedHands.remove(playerId);
        highFiveAnimStart.remove(playerId);
        frozenPlayers.remove(playerId);
    }

  
    public static boolean isLocalPlayerFrozen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        return frozenPlayers.getOrDefault(client.player.getUuid(), false);
    }

   
    public static boolean isPlayerFrozen(UUID playerId) {
        return frozenPlayers.getOrDefault(playerId, false);
    }
}
