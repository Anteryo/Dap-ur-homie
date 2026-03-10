package com.cooptest.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundCategory;

/**
 * Client-side white screen overlay for Heaven Dap effect.
 *
 * Timeline:
 * 0-3s: Full white (100% opacity)
 * 3-3.5s: Fade to 30% opacity
 * 3.5-9.5s: Stay at 30% (heaven phase - 6 SECONDS)
 * 9.5-11.5s: Fade to 100% white (2 seconds)
 * 11.5s: TP back to world
 * 11.5-16.5s: Fade out to normal (5 seconds)
 * 16.5s: Complete
 */
public class HeavenWhiteOverlay {

    private static boolean active = false;
    private static float opacity = 0.0f;  // 0.0 to 1.0
    private static long phaseStartTime = 0;
    private static HeavenPhase currentPhase = HeavenPhase.NONE;

    // Sound muting
    private static float originalMasterVolume = 1.0f;
    private static float originalMusicVolume = 1.0f;
    private static boolean soundsMuted = false;

    public enum HeavenPhase {
        NONE,
        FULL_WHITE,     // 100% for 3 seconds
        FADE_TO_THIRTY, // 100% → 30% over 0.5s
        HEAVEN,         // 30% for 8 seconds
        FADE_OUT,       // 30% → 100% over 2 seconds (faster!)
        FADE_TO_NORMAL, // 100% → 0% over 5 seconds (fade out to normal)
        DONE            // Complete
    }


    public static void start() {
        active = true;
        opacity = 1.0f;  // Start full white
        currentPhase = HeavenPhase.FULL_WHITE;
        phaseStartTime = System.currentTimeMillis();

        // Mute all sounds
        muteSounds();
    }


    public static void stop() {
        active = false;
        opacity = 0.0f;
        currentPhase = HeavenPhase.NONE;

        unmuteSounds();
    }


    public static void render(DrawContext context, float tickDelta) {
        if (!active || opacity <= 0.0f) return;


        int alpha = (int)(opacity * 255);
        int color = (alpha << 24) | 0xFFFFFF;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        context.fill(0, 0, screenWidth, screenHeight, color);
    }


    public static void tick() {
        if (!active) return;

        long elapsed = System.currentTimeMillis() - phaseStartTime;

        switch (currentPhase) {
            case FULL_WHITE -> {
                opacity = 1.0f;  // Stay at 100%

                if (elapsed >= 3000) {
                    currentPhase = HeavenPhase.FADE_TO_THIRTY;
                    phaseStartTime = System.currentTimeMillis();
                    System.out.println("[Heaven Overlay] Fading to 30%");
                }
            }

            case FADE_TO_THIRTY -> {
                float progress = Math.min(elapsed / 500.0f, 1.0f);
                opacity = 1.0f - (progress * 0.7f);  // 1.0 → 0.3

                if (progress >= 1.0f) {
                    opacity = 0.3f;
                    currentPhase = HeavenPhase.HEAVEN;
                    phaseStartTime = System.currentTimeMillis();
                }
            }

            case HEAVEN -> {
                opacity = 0.3f;  // Stay at 30%

                if (elapsed >= 6000) {
                    currentPhase = HeavenPhase.FADE_OUT;
                    phaseStartTime = System.currentTimeMillis();
                }
            }

            case FADE_OUT -> {
                float progress = Math.min(elapsed / 2000.0f, 1.0f);
                opacity = 0.3f + (progress * 0.7f);  // 0.3 → 1.0

                if (progress >= 1.0f) {
                    opacity = 1.0f;
                    currentPhase = HeavenPhase.FADE_TO_NORMAL;
                    phaseStartTime = System.currentTimeMillis();
                }
            }

            case FADE_TO_NORMAL -> {
                float progress = Math.min(elapsed / 5000.0f, 1.0f);
                opacity = 1.0f - progress;  // 1.0 → 0.0

                if (progress >= 1.0f) {
                    opacity = 0.0f;
                    currentPhase = HeavenPhase.DONE;
                }
            }

            case DONE -> {
                opacity = 0.0f;
            }
        }
    }

    private static void muteSounds() {
        if (soundsMuted) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null && client.getSoundManager() != null) {
            originalMasterVolume = client.options.getSoundVolumeOption(SoundCategory.MASTER).getValue().floatValue();
            originalMusicVolume = client.options.getSoundVolumeOption(SoundCategory.MUSIC).getValue().floatValue();

            client.getSoundManager().stopAll();

            client.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);

            soundsMuted = true;
        }
    }


    private static void unmuteSounds() {
        if (!soundsMuted) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            // Restore master volume
            client.options.getSoundVolumeOption(SoundCategory.MASTER).setValue((double)originalMasterVolume);
            client.options.getSoundVolumeOption(SoundCategory.MUSIC).setValue((double)originalMusicVolume);

            soundsMuted = false;
        }
    }


    public static boolean isActive() {
        return active;
    }

    public static HeavenPhase getCurrentPhase() {
        return currentPhase;
    }

    public static float getOpacity() {
        return opacity;
    }
}