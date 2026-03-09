package com.cooptest.client;

import com.cooptest.ChargedDapHandler;
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
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public class ChargedDapClientHandler {

    private static KeyBinding chargedDapKey;
    private static boolean wasKeyPressed = false;
    private static boolean isCharging = false;
    private static boolean wasFireCharging = false;
    private static boolean fireChargeComplete = false;  // True when fire bar is full
    private static float lastFireLevel = 0f;
    private static long chargeStartTime = 0;

    private static long whiffCooldownEnd = 0;

    private static final Map<UUID, Float> otherPlayerCharges = new HashMap<>();
    private static final Map<UUID, Float> otherPlayerFire = new HashMap<>();
    private static final Map<UUID, Boolean> otherPlayerCharging = new HashMap<>();

    private static float localFireLevel = 0f;

    private static boolean isHeavenReady = false;
    private static long heavenReadyStartTime = 0;
    private static long flashStartTime = 0;
    private static int resultTier = 0;
    private static boolean resultPerfect = false;

    private static long perfectImpactStartTime = 0;

    private static boolean isPerfectDapFrozen = false;
    private static boolean perfectImpactActive = false;

    private static int perfectDapImpactFrame = 0; // 0 = none, 1-4 = frame index
    private static long perfectDapImpactFrameStartTime = 0;

  
    private static final long WHITE_FADE_DURATION = 30;
    private static final long IMPACT1_END = 80;    // 30 + 50ms
    private static final long IMPACT2_END = 130;   // 80 + 50ms
    private static final long IMPACT3_END = 180;   // 130 + 50ms

    private static net.minecraft.util.Identifier IMPACT1_TEXTURE;
    private static net.minecraft.util.Identifier IMPACT2_TEXTURE;
    private static net.minecraft.util.Identifier IMPACT3_TEXTURE;

    private static net.minecraft.util.Identifier PERFECT_FRAME0_TEXTURE;
    private static net.minecraft.util.Identifier PERFECT_FRAME1_TEXTURE;
    private static net.minecraft.util.Identifier PERFECT_FRAME2_TEXTURE;
    private static net.minecraft.util.Identifier PERFECT_FRAME3_TEXTURE;

    // ==================== FIRE DAP COMBO CLIENT STATE ====================
    private static KeyBinding fireDapComboKey;

    public static boolean isFireDapJKeyHeld() {
        return fireDapComboKey != null && fireDapComboKey.isPressed();
    }
    private static boolean fireDapWasKeyPressed = false;

    private static long fireDapComboWindowStart = 0;
    private static boolean inFireDapComboWindow = false;
    private static final long FIRE_DAP_COMBO_WINDOW_MS = 750;

    private static final Map<UUID, Boolean> fireDapFrozenPlayers = new HashMap<>();
    private static final Set<UUID> fireDapFirstPersonPlayers = new HashSet<>();
 
    private static final long CHARGE_TIME_MS = 250;            // 0.25 seconds to full charge
    private static final long FLASH_DURATION = 500;

 
    public static void forceStopCharging() {
        isCharging = false;
        localFireLevel = 0f;
        wasFireCharging = false;
        fireChargeComplete = false;
        lastFireLevel = 0f;
        System.out.println("[ChargedDapClient] Force stopped charging - server cancelled!");
    }

   
    public static boolean isLocalPlayerCharging() {
        return isCharging;
    }

    public static void register() {
        chargedDapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.coopmoves.dap", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.coopmoves"
        ));

        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.ChargeSyncPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        UUID playerId = payload.playerId();
                        MinecraftClient client = MinecraftClient.getInstance();

                        if (payload.isCharging()) {
                            otherPlayerCharges.put(playerId, payload.chargePercent());
                            otherPlayerFire.put(playerId, payload.firePercent());
                            otherPlayerCharging.put(playerId, true);

                            // Update local fire level immediately if this is us
                            if (client.player != null && client.player.getUuid().equals(playerId)) {
                                float newFireLevel = payload.firePercent();

                                if (newFireLevel < localFireLevel - 0.1f && wasFireCharging) {
                                    // Fire dropped - reset animation to dap_charge_idle
                                    wasFireCharging = false;
                                    fireChargeComplete = false;
                                    CoopAnimationHandler.playDapChargeIdle(client.player);
                                }

                                localFireLevel = newFireLevel;
                            }
                        } else {
                            otherPlayerCharges.remove(playerId);
                            otherPlayerFire.remove(playerId);
                            otherPlayerCharging.remove(playerId);

                            if (client.player != null && client.player.getUuid().equals(playerId)) {
                                localFireLevel = 0f;
                            }
                        }
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.WhiffCooldownPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        long duration = payload.cooldownDurationMs();
                        whiffCooldownEnd = System.currentTimeMillis() + duration;

                        // Force stop charging
                        isCharging = false;
                        localFireLevel = 0f;
                        wasFireCharging = false;
                        fireChargeComplete = false;
                        lastFireLevel = 0f;

                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.player != null) {
                            CoopAnimationHandler.stopDapCharge(client.player);
                        }
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.ImpactFramePayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        if (payload.grayscale()) {
                            perfectImpactStartTime = System.currentTimeMillis();
                            perfectImpactActive = true;

                            if (IMPACT1_TEXTURE == null) {
                                IMPACT1_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impact1.png");
                                IMPACT2_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impact2.png");
                                IMPACT3_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impact3.png");
                            }
                        }
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.DapResultPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        onDapResult(payload.x(), payload.y(), payload.z(),
                                payload.player1(), payload.player2(),
                                payload.tier(), payload.perfectHit());
                    });
                }
        );

        // Perfect dap freeze state
        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.PerfectDapFreezePayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        isPerfectDapFrozen = payload.frozen();
                    });
                }
        );

       
        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.PerfectDapImpactFramePayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        
                        if (!isPerfectDapFrozen) {
                            System.out.println("[ChargedDap] Ignoring impact frame - not frozen (spectator)");
                            return;
                        }


                        perfectDapImpactFrame = 1; // Start at frame 1
                        perfectDapImpactFrameStartTime = System.currentTimeMillis();
                        System.out.println("[ChargedDap] Starting impact frames - we're in perfect dap!");
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.HeavenReadyPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.player == null) return;

                        if (client.player.getUuid().equals(payload.playerId())) {
                            if (payload.ready()) {
                                isHeavenReady = true;
                                heavenReadyStartTime = System.currentTimeMillis();
                                System.out.println("[Heaven Dap] CLIENT: You are HEAVEN READY! (UI will break!)");
                            } else {
                                // Lost heaven ready status
                                isHeavenReady = false;
                                System.out.println("[Heaven Dap] CLIENT: Lost heaven ready status");
                            }
                        }
                    });
                }
        );

     
        fireDapComboKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.coopmoves.fire_dap_combo",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.coopmoves"
        ));

        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.FireDapWindowPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        fireDapComboWindowStart = System.currentTimeMillis();
                        inFireDapComboWindow = true;
                        System.out.println("[Fire Dap Combo Client]  WINDOW OPENED! PRESS J!");
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.FireDapFreezePayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        fireDapFrozenPlayers.put(payload.playerId(), payload.frozen());
                        System.out.println("[Fire Dap Combo Client] Freeze: " + payload.frozen());
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.FireDapFirstPersonPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        UUID playerId = payload.playerId();
                        boolean show = payload.showBothHands();

                        if (show) {
                            fireDapFirstPersonPlayers.add(playerId);
                            System.out.println("[Fire Dap Combo Client]  FIRST PERSON ENABLED for " + playerId);

                            var client = context.client();
                            if (client.player != null && client.player.getUuid().equals(playerId)) {
                            }
                        } else {
                            fireDapFirstPersonPlayers.remove(playerId);
                            System.out.println("[Fire Dap Combo Client]  FIRST PERSON DISABLED for " + playerId);

                            var client = context.client();
                            if (client.player != null && client.player.getUuid().equals(playerId)) {
                            }
                        }
                    });
                }
        );
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            boolean isKeyPressed = chargedDapKey.isPressed();

            boolean onCooldown = System.currentTimeMillis() < whiffCooldownEnd;

            boolean inHighFive = HighFiveClientHandler.isLocalPlayerInHighFive();

            boolean inBlocking = CoopAnimationHandler.isInBlockingState(client.player.getUuid());

            // ===== FREEZE CHECK FIRST! =====
            if (isKeyPressed && !wasKeyPressed) {
                if (isPerfectDapFrozen && !QTEClientHandler.isActive()) {
                    System.out.println("[G Key]  BLOCKED - Player frozen!");
                    wasKeyPressed = true;
                    return;
                }
            }

            if (isKeyPressed && !wasKeyPressed) {
                if (QTEClientHandler.isActive()) {
                    QTEClientHandler.handleKeyPress("G");
                    wasKeyPressed = true;
                    return; // Block normal G action during QTE
                }
            }
            // ============================================================

            if (isKeyPressed && !wasKeyPressed && !inBlocking) {
                if (inHighFive) {
                    String reason = HighFiveClientHandler.getHighFiveBlockReason();
                    client.player.sendMessage(Text.literal("§cCan't charge dap while hand is raised!"), true);
                } else if (onCooldown) {
                    long remaining = (whiffCooldownEnd - System.currentTimeMillis()) / 100;
                    client.player.sendMessage(Text.literal("§cDap on cooldown! " + (remaining / 10.0) + "s"), true);
                } else if (!client.player.getMainHandStack().isEmpty()) {
                    client.player.sendMessage(Text.literal("§cMain hand must be empty for charged dap!"), true);
                } else {
                    isCharging = true;
                    chargeStartTime = System.currentTimeMillis();
                    localFireLevel = 0f;
                    wasFireCharging = false;
                    fireChargeComplete = false;
                    lastFireLevel = 0f;
                    ClientPlayNetworking.send(new ChargedDapHandler.ChargeStartPayload());

                    // Start dap charge animation
                    CoopAnimationHandler.startDapCharge(client.player);
                }
            }

            // Force stop charging if blocking started
            if (isCharging && inBlocking) {
                isCharging = false;
                localFireLevel = 0f;
                wasFireCharging = false;
                fireChargeComplete = false;
                lastFireLevel = 0f;
                // Stop animation - return to NONE
                CoopAnimationHandler.stopDapCharge(client.player);
            }

            if (isCharging && !onCooldown && !inBlocking) {
                if (localFireLevel > 0.05f && !wasFireCharging) {
                    wasFireCharging = true;
                    fireChargeComplete = false;
                    CoopAnimationHandler.startFireDapCharge(client.player);
                }

                if (wasFireCharging && localFireLevel >= 0.99f && !fireChargeComplete) {
                    fireChargeComplete = true;
                    CoopAnimationHandler.playFireDapChargeIdle(client.player);
                }

                if (wasFireCharging && localFireLevel < 0.05f) {
                    wasFireCharging = false;
                    fireChargeComplete = false;
                    CoopAnimationHandler.playDapChargeIdle(client.player);
                }

                lastFireLevel = localFireLevel;
            }

            if (isCharging && onCooldown) {
                isCharging = false;
                localFireLevel = 0f;
                wasFireCharging = false;
                fireChargeComplete = false;
                lastFireLevel = 0f;
                CoopAnimationHandler.stopDapCharge(client.player);
            }

            if (!isKeyPressed && wasKeyPressed && isCharging) {
                isCharging = false;
                localFireLevel = 0f;
                wasFireCharging = false;
                fireChargeComplete = false;
                lastFireLevel = 0f;
                ClientPlayNetworking.send(new ChargedDapHandler.ChargeReleasePayload());

               
            }

            boolean fireDapJKeyPressed = fireDapComboKey.isPressed();

            if (inFireDapComboWindow && fireDapJKeyPressed && !fireDapWasKeyPressed) {
                System.out.println("[DEBUG Client] Sending J-press!");
                System.out.println("[DEBUG Client] Window start: " + fireDapComboWindowStart);
                System.out.println("[DEBUG Client] Current time: " + System.currentTimeMillis());
                System.out.println("[DEBUG Client] Elapsed: " + (System.currentTimeMillis() - fireDapComboWindowStart) + "ms");
                ClientPlayNetworking.send(new ChargedDapHandler.FireDapJPressPayload());
                inFireDapComboWindow = false;
                System.out.println("[Fire Dap Combo Client] Sent J press!");
            }

            if (inFireDapComboWindow && System.currentTimeMillis() - fireDapComboWindowStart > FIRE_DAP_COMBO_WINDOW_MS) {
                inFireDapComboWindow = false;
                System.out.println("[Fire Dap Combo Client] Window expired");
            }

            fireDapWasKeyPressed = fireDapJKeyPressed;
            // ===================================================================

            wasKeyPressed = isKeyPressed;
        });

        HudRenderCallback.EVENT.register(ChargedDapClientHandler::renderHUD);
    }

    private static void onDapResult(double x, double y, double z, UUID player1, UUID player2, int tier, boolean perfectHit) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        UUID myId = client.player.getUuid();

        boolean wasFullFireDap = fireChargeComplete || localFireLevel >= 0.99f;

        isCharging = false;
        localFireLevel = 0f;
        wasFireCharging = false;
        fireChargeComplete = false;
        lastFireLevel = 0f;
        otherPlayerCharges.clear();
        otherPlayerFire.clear();
        otherPlayerCharging.clear();

        CoopAnimationHandler.stopDapCharge(client.player);

        if (wasFullFireDap || tier >= 5) {
            CoopAnimationHandler.playFireDapHit(client.player);
        } else if (perfectHit && tier >= 3) {
            CoopAnimationHandler.playPerfectDapHit(client.player);
        } else {
            CoopAnimationHandler.playDapHit(client.player);
        }

        if (myId.equals(player1) || myId.equals(player2)) {
            flashStartTime = System.currentTimeMillis();
            resultTier = tier;
            resultPerfect = perfectHit;
        }

        spawnTierParticles(client, x, y, z, tier, perfectHit);
    }

    private static void spawnTierParticles(MinecraftClient client, double x, double y, double z, int tier, boolean perfect) {
        if (client.world == null) return;

        net.minecraft.particle.ParticleEffect particle;
        int particleCount;

        switch (tier) {
            case 0 -> { particle = net.minecraft.particle.ParticleTypes.SMOKE; particleCount = 5; }
            case 1 -> { particle = net.minecraft.particle.ParticleTypes.CRIT; particleCount = 10; }
            case 2 -> { particle = net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER; particleCount = 15; }
            case 3 -> { particle = net.minecraft.particle.ParticleTypes.ENCHANT; particleCount = 20; }
            case 4 -> { particle = net.minecraft.particle.ParticleTypes.TOTEM_OF_UNDYING; particleCount = 25; }
            case 5 -> { particle = net.minecraft.particle.ParticleTypes.FLAME; particleCount = 30; }
            default -> { particle = net.minecraft.particle.ParticleTypes.CRIT; particleCount = 5; }
        }

        for (int i = 0; i < particleCount; i++) {
            double offsetX = (Math.random() - 0.5) * 0.5;
            double offsetY = (Math.random() - 0.5) * 0.5;
            double offsetZ = (Math.random() - 0.5) * 0.5;
            double velX = (Math.random() - 0.5) * 0.3;
            double velY = Math.random() * 0.2;
            double velZ = (Math.random() - 0.5) * 0.3;

            client.world.addParticle(particle, x + offsetX, y + offsetY, z + offsetZ, velX, velY, velZ);
        }

        if (perfect) {
            for (int i = 0; i < 8; i++) {
                double angle = (i / 8.0) * Math.PI * 2;
                double offsetX = Math.cos(angle) * 0.3;
                double offsetZ = Math.sin(angle) * 0.3;
                client.world.addParticle(net.minecraft.particle.ParticleTypes.ENCHANT,
                        x + offsetX, y + 0.5, z + offsetZ, 0, 0.1, 0);
            }
        }
    }

    private static void renderHUD(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // ===== SCREEN FLASH =====
        long timeSinceFlash = System.currentTimeMillis() - flashStartTime;
        if (timeSinceFlash < FLASH_DURATION) {
            float progress = (float) timeSinceFlash / FLASH_DURATION;

            // Subtle flash - lower alpha values
            int baseAlpha = switch (resultTier) {
                case 0 -> 30;
                case 1 -> 40;
                case 2 -> 50;
                case 3 -> 60;
                case 4 -> 80;
                case 5 -> 100;
                default -> 30;
            };

            if (resultPerfect) baseAlpha = Math.min(baseAlpha + 40, 140);

            int alpha = (int) ((1.0f - progress) * baseAlpha);

            int flashColor = switch (resultTier) {
                case 0 -> (alpha << 24) | 0x888888;
                case 1 -> (alpha << 24) | 0xFFFF00;
                case 2 -> (alpha << 24) | 0x00FF00;
                case 3 -> (alpha << 24) | 0xFFAA00;
                case 4 -> (alpha << 24) | 0xFF00FF;
                case 5 -> (alpha << 24) | 0xFF4400;
                default -> (alpha << 24) | 0xFFFFFF;
            };

            context.fill(0, 0, screenWidth, screenHeight, flashColor);
        }

        // ===== PERFECT DAP IMPACT FRAMES =====
        if (perfectImpactActive) {
            long elapsed = System.currentTimeMillis() - perfectImpactStartTime;

            if (elapsed < WHITE_FADE_DURATION) {
                // White fade in
                float fadeProgress = (float) elapsed / WHITE_FADE_DURATION;
                int alpha = (int) (255 * fadeProgress);
                context.fill(0, 0, screenWidth, screenHeight, (alpha << 24) | 0xFFFFFF);
            } else if (elapsed < IMPACT1_END) {
                // Impact1
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                context.drawTexture(IMPACT1_TEXTURE, 0, 0, screenWidth, screenHeight, 0, 0, 1920, 1080, 1920, 1080);
            } else if (elapsed < IMPACT2_END) {
                // Impact2
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                context.drawTexture(IMPACT2_TEXTURE, 0, 0, screenWidth, screenHeight, 0, 0, 1920, 1080, 1920, 1080);
            } else if (elapsed < IMPACT3_END) {
                // Impact3
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                context.drawTexture(IMPACT3_TEXTURE, 0, 0, screenWidth, screenHeight, 0, 0, 1920, 1080, 1920, 1080);
            } else {
                // Done - reset
                perfectImpactActive = false;
            }
        }

        if (perfectDapImpactFrame > 0) {
            long elapsed = System.currentTimeMillis() - perfectDapImpactFrameStartTime;

            if (IMPACT3_TEXTURE == null) {
                IMPACT1_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impact1.png");
                IMPACT2_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impact2.png");
                IMPACT3_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/impact3.png");
            }

            if (PERFECT_FRAME0_TEXTURE == null) {
                PERFECT_FRAME0_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/frame0.png");
                PERFECT_FRAME1_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/frame1.png");
                PERFECT_FRAME2_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/frame2.png");
                PERFECT_FRAME3_TEXTURE = net.minecraft.util.Identifier.of("testcoop", "textures/gui/impact/frame3.png");
            }

            
            net.minecraft.util.Identifier frameTexture;
            if (elapsed < 33) {
                frameTexture = PERFECT_FRAME0_TEXTURE; // 0-33ms: frame0
            } else if (elapsed < 66) {
                frameTexture = PERFECT_FRAME1_TEXTURE; // 33-66ms: frame1
            } else if (elapsed < 100) {
                frameTexture = PERFECT_FRAME2_TEXTURE; // 66-100ms: frame2
            } else if (elapsed < 133) {
                frameTexture = PERFECT_FRAME0_TEXTURE; // 100-133ms: frame0
            } else if (elapsed < 166) {
                frameTexture = PERFECT_FRAME3_TEXTURE; // 133-166ms: frame3
            } else if (elapsed < 200) {
                frameTexture = PERFECT_FRAME0_TEXTURE; // 166-200ms: frame0
            } else {
                perfectDapImpactFrame = 0;
                return;
            }

            if (frameTexture == null) {
                perfectDapImpactFrame = 0;
                return;
            }

            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            context.drawTexture(frameTexture, 0, 0, screenWidth, screenHeight, 0, 0, 1920, 1080, 1920, 1080);
        }

        // ===== COOLDOWN INDICATOR =====
        boolean onCooldown = System.currentTimeMillis() < whiffCooldownEnd;
        if (onCooldown && !isCharging) {
            long remaining = whiffCooldownEnd - System.currentTimeMillis();
            float cooldownProgress = remaining / 800f; // 0.8 sec cooldown

            int barWidth = 40;
            int barHeight = 3;
            int barX = (screenWidth - barWidth) / 2;
            int barY = screenHeight / 2 + 20;

            // Red cooldown bar
            context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0x44000000);
            int fillWidth = (int) (barWidth * cooldownProgress);
            context.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xBBFF0000);
        }

        if (inFireDapComboWindow) {
            long elapsed = System.currentTimeMillis() - fireDapComboWindowStart;
            long remaining = FIRE_DAP_COMBO_WINDOW_MS - elapsed;

            if (remaining > 0) {
                String text = "§c§l PRESS J! ";
                int textWidth = client.textRenderer.getWidth(text);
                int textX = (screenWidth - textWidth) / 2;
                int textY = screenHeight / 2 + 10;

                float pulse = (float) (Math.sin(System.currentTimeMillis() / 80.0) * 0.4 + 0.6);
                int alpha = (int) (pulse * 255);

                float timeProgress = (float) elapsed / FIRE_DAP_COMBO_WINDOW_MS;
                int color = timeProgress < 0.5f ? (alpha << 24) | 0xFF8800 : (alpha << 24) | 0xFF0000;

                context.drawText(client.textRenderer, text, textX, textY, color, true);

                int barWidth = 100;
                int barHeight = 3;
                int barX = (screenWidth - barWidth) / 2;
                int barY = textY + 12;

                context.fill(barX, barY, barX + barWidth, barY + barHeight, 0x80000000);

                int fillWidth = (int) (barWidth * (1.0f - timeProgress));
                int barColor = timeProgress < 0.5f ? 0xFFFF8800 : 0xFFFF0000;
                context.fill(barX, barY, barX + fillWidth, barY + barHeight, barColor);
            }
        }
       
        if (isCharging && !onCooldown) {
            long elapsed = System.currentTimeMillis() - chargeStartTime;
            float chargePercent = Math.min(1.0f, (float) elapsed / CHARGE_TIME_MS);

            float myFire = localFireLevel;

            // Small bar
            int barWidth = 40;
            int barHeight = 3;
            int barX = (screenWidth - barWidth) / 2;
            int barY = screenHeight / 2 + 20;

            context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0x44000000);

            if (myFire > 0.05f) {
                int greenWidth = (int) (barWidth * chargePercent);
                context.fill(barX, barY, barX + greenWidth, barY + barHeight, 0xBB00FF00);

                int redWidth = (int) (barWidth * myFire);

                int shakeX = 0;
                int shakeY = 0;

                if (isHeavenReady) {
                    long timeSinceReady = System.currentTimeMillis() - heavenReadyStartTime;

                    shakeX = (int) ((Math.random() - 0.5) * 8);  // 8 pixels shake (was 4)
                    shakeY = (int) ((Math.random() - 0.5) * 6);  // 6 pixels shake (was 2)

                    if (timeSinceReady % 500 < 250) {
                        // Purple flash
                        context.fill(barX + shakeX, barY + shakeY,
                                barX + redWidth + shakeX, barY + barHeight + shakeY,
                                0xFFFF00FF);  // Bright magenta
                    } else {
                        context.fill(barX + shakeX, barY + shakeY,
                                barX + redWidth + shakeX, barY + barHeight + shakeY,
                                0xDDFF2200);
                    }

                   
                    context.fill(barX - 5 + shakeX, barY + 1 + shakeY,
                            barX + barWidth + 5 + shakeX, barY + 2 + shakeY,
                            0xFFFFFFFF);  // White crack

                    for (int i = 0; i < barWidth; i++) {
                        int crackY = barY + (i % 2) + shakeY;
                        context.fill(barX + i + shakeX, crackY,
                                barX + i + 1 + shakeX, crackY + 1,
                                0x88FFFFFF);  // Semi-transparent white
                    }

                    if (timeSinceReady % 100 < 50) {
                        context.fill(barX - 2 + shakeX, barY - 3 + shakeY,
                                barX - 1 + shakeX, barY - 2 + shakeY,
                                0xFFFFDD00);
                        context.fill(barX + barWidth + 1 + shakeX, barY - 3 + shakeY,
                                barX + barWidth + 2 + shakeX, barY - 2 + shakeY,
                                0xFFFFDD00);
                    }

                } else if (myFire >= 0.99f) {
                    shakeX = (int) ((Math.random() - 0.5) * 4);
                    shakeY = (int) ((Math.random() - 0.5) * 2);

                    context.fill(barX + shakeX, barY + shakeY,
                            barX + redWidth + shakeX, barY + barHeight + shakeY,
                            0xDDFF2200);
                } else {
                    context.fill(barX, barY, barX + redWidth, barY + barHeight, 0xDDFF2200);
                }
            } else {
                int fillWidth = (int) (barWidth * chargePercent);
                int fillColor;
                if (chargePercent >= 0.99f) {
                    fillColor = 0xBB00FF00; // Ready green
                } else {
                    fillColor = 0xBBFFAA00; // Charging orange
                }
                context.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);
            }

            int partnerY = barY + 8;
            for (Map.Entry<UUID, Float> entry : otherPlayerCharges.entrySet()) {
                if (!otherPlayerCharging.getOrDefault(entry.getKey(), false)) continue;
                if (entry.getKey().equals(client.player.getUuid())) continue;

                boolean inRange = false;
                if (client.world != null) {
                    for (var player : client.world.getPlayers()) {
                        if (player.getUuid().equals(entry.getKey())) {
                            if (client.player.distanceTo(player) <= 20.0) {
                                inRange = true;
                            }
                            break;
                        }
                    }
                }
                if (!inRange) continue;

                float partnerCharge = entry.getValue();
                float partnerFire = otherPlayerFire.getOrDefault(entry.getKey(), 0f);

                int pBarWidth = 30;
                int pBarHeight = 2;
                int pBarX = (screenWidth - pBarWidth) / 2;

                context.fill(pBarX - 1, partnerY - 1, pBarX + pBarWidth + 1, partnerY + pBarHeight + 1, 0x33000000);

                int pFillWidth = (int) (pBarWidth * partnerCharge);
                int pFillColor = partnerFire > 0.1f ? 0xAAFF4400 : (partnerCharge >= 0.99f ? 0xAA00FF00 : 0xAAFFAA00);
                context.fill(pBarX, partnerY, pBarX + pFillWidth, partnerY + pBarHeight, pFillColor);

                partnerY += 6;
            }
        }

        QTEClientHandler.renderHUD(context, screenWidth, screenHeight);
    }

    public static boolean isCurrentlyCharging() {
        return isCharging;
    }

    public static float getChargePercent() {
        if (!isCharging) return 0f;
        long elapsed = System.currentTimeMillis() - chargeStartTime;
        return Math.min(1.0f, (float) elapsed / CHARGE_TIME_MS);
    }

    public static float getFireLevel() {
        return localFireLevel;
    }

    public static boolean isPlayerCharging(UUID playerId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getUuid().equals(playerId)) {
            return isCharging;
        }
        return otherPlayerCharging.getOrDefault(playerId, false);
    }

    public static float getPlayerChargePercent(UUID playerId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getUuid().equals(playerId)) {
            return getChargePercent();
        }
        return otherPlayerCharges.getOrDefault(playerId, 0f);
    }

    public static float getPlayerFireLevel(UUID playerId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getUuid().equals(playerId)) {
            return localFireLevel;
        }
        return otherPlayerFire.getOrDefault(playerId, 0f);
    }

    
    public static boolean isOnWhiffCooldown() {
        return System.currentTimeMillis() < whiffCooldownEnd;
    }

    
    public static boolean isImpactFrameActive() {
        return perfectImpactActive;
    }

    
    public static boolean isLocalPlayerPerfectDapFrozen() {
        return isPerfectDapFrozen;
    }

    
    public static boolean isLocalPlayerFireDapFrozen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        return fireDapFrozenPlayers.getOrDefault(client.player.getUuid(), false);
    }

    
    public static boolean shouldShowFireDapFirstPerson() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        return fireDapFirstPersonPlayers.contains(client.player.getUuid());
    }

   
    public static boolean isInQTEWindow() {
        return QTEClientHandler.isActive();
    }

    public static boolean isPlayerFrozen() {
        return isPerfectDapFrozen;
    }

    
    public static String getQTEExpectedButton() {
        return QTEClientHandler.getExpectedButton();
    }

    
    public static long getQTEWindowStart() {
        return QTEClientHandler.getWindowStart();
    }

  
    public static long getQTEWindowEnd() {
        return QTEClientHandler.getWindowEnd();
    }

    
    public static int getQTEStage() {
        return QTEClientHandler.getStage();
    }
    
    public static void cleanup(UUID playerId) {
        otherPlayerCharges.remove(playerId);
        otherPlayerFire.remove(playerId);
        otherPlayerCharging.remove(playerId);

        fireDapFrozenPlayers.remove(playerId);
        fireDapFirstPersonPlayers.remove(playerId);
    }
}
