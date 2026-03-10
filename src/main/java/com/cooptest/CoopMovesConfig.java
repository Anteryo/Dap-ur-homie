package com.cooptest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class CoopMovesConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("coopmoves.json").toFile();

    private static CoopMovesConfig INSTANCE;
// NOT EVERY CONFIG WORK SOME DO NOTHING BC IM I WAS LAZY OR FORGOT pls understnd :(
    // ============ GRAB & THROW ============
    public boolean enableGrabThrow = true;
    public float throwMinPower = 0.5f;
    public float throwMaxPower = 1.5f;
    public int grabCooldownTicks = 20;
    public boolean allowForcedPickup = true;
    public boolean grabBreaksBlocks = false;

    // ============ DAP ============
    public boolean enableDap = true;
    public int dapChargeTimeMs = 1500;
    public int dapCooldownMs = 800;
    public int perfectWindowMs = 40;
    public boolean enableDapFirstPerson = true;
    public boolean dapCausesExplosion = false;   // Funny: Every dap explodes!

    // ============ FIRE DAP ============
    public boolean enableFireDap = true;
    public int fireChargeDelayMs = 2000;
    public int fireBuildTimeMs = 2000;
    public float fireExplosionPower = 3.0f;
    public int fireExplosionRadius = 50;  // Blocks
    public float fireKnockbackMultiplier = 15.0f;
    public boolean fireBreaksBlocks = false;  // Funny: Fire dap destroys terrain

    // ============ PERFECT LEGENDARY ============
    public boolean enablePerfectLegendary = true;
    public double perfectLegendaryMinSpeed = 10.0;
    public int perfectLegendaryLevitationSec = 10;
    public int perfectLegendarySlowFallSec = 30;
    public boolean perfectLegendaryKillsOnFail = true;
    public boolean perfectLegendaryGivesEffects = true;  // Give special effects on success

    // ============ HIGH FIVE ============
    public boolean enableHighFive = true;
    public int highFiveTimeoutMs = 2500;
    public int highFiveLeftHangingCooldownMs = 1500;
    public boolean enableHighFiveCombo = true;  // Enable double high five combo
    public int highFiveComboWindowMs = 400;     // Time to press H for combo
    public boolean highFiveComboAura = true;    // Black & gold aura effect
    public boolean highFiveComboBeam = true;    // Beam to sky effect
    public boolean enableHighFiveFirstPerson = true;  // Show hands in first person
    public boolean highFiveCausesLightning = false;  // Funny: Lightning strike on high five!

    // ============ HUG ============
    public boolean enableHug = true;
    public int hugDurationSec = 10;  // How long hug animation lasts
    public boolean enableHugFirstPerson = true;  // Show both arms in first person
    public boolean hugHealsPlayers = true;  // Funny: Hugging heals both players
    public float hugHealAmount = 2.0f;  // Hearts to heal (if enabled)

    // ============ PUSH ============
    public boolean enablePush = true;
    public float pushDistance = 2.5f;
    public int pushCooldownMs = 500;
    public boolean enablePushFirstPerson = true;  // Show both hands pushing
    public boolean pushCausesParticles = true;  // Show push particles
    public boolean pushIntoOrbit = false;  // Funny: Push sends players REALLY high

    // ============ CATCH ============
    public boolean enableCatch = true;
    public int catchWindowMs = 500;
    public int catchCooldownMs = 1000;
    public boolean catchNegatesFallDamage = true;  // Catching negates fall damage

    // ============ SQUASH ============
    public boolean enableSquash = true;
    public int squashDurationSec = 25;
    public float squashDamage = 10.0f;
    public int squashNauseaSec = 15;
    public boolean squashDropsItems = true;
    public boolean squashMakesFlat = true;  // Visual: Makes player model look flat

    // ============ MAHITO ============
    public boolean enableMahito = true;
    public int mahitoCurseDurationSec = 60;
    public boolean mahitoTransformsPlayer = true;  // Visual transformation effect

    // ============ MARIO JUMP ============
    public boolean enableMarioJump = true;
    public float marioJumpPower = 2.0f;  // Jump height multiplier
    public boolean marioJumpSound = true;  // Play Mario jump sound
    public boolean marioJumpParticles = true;  // Show particles

    // ============ SHIELD MODE ============
    public boolean enableShieldMode = true;
    public float shieldDamageReduction = 0.8f;  // 80% damage reduction for holder
    public boolean shieldBlocksProjectiles = true;  // Shield blocks arrows, etc
    public int shieldSwapCooldownMs = 3000;  // Cooldown to switch between shield/throw

    // ============ FIRST PERSON ANIMATIONS ============
    public boolean enableFirstPersonAnimations = true;  // Master toggle
    public float firstPersonArmForwardOffset = 3.0f;  // How far forward arms are (Z position)
    public float firstPersonArmHeightOffset = 2.0f;   // How high arms are (Y position)
    public boolean firstPersonSmoothEndings = true;   // Gradual fade-out instead of instant

    // ============ SOUNDS ============
    public float dapSoundVolume = 1.0f;
    public float explosionSoundVolume = 1.5f;
    public float epicDapSoundVolume = 2.0f;
    public float highFiveSoundVolume = 1.0f;
    public float pushSoundVolume = 1.0f;
    public boolean muteAllSounds = false;  // Disable all mod sounds

    // ============ VISUAL EFFECTS ============
    public boolean enableParticles = true;  // Master particle toggle
    public float particleDensity = 1.0f;  // Multiplier for particle count (0.5 = half, 2.0 = double)
    public boolean enableScreenShake = true;  // Camera shake on big impacts
    public boolean enableSlowMotion = false;  // Funny: Slow motion on perfect daps

    // ============ MISC ============
    public boolean showDapChargeBar = true;
    public boolean showFireChargeBar = true;
    public boolean announcePerectLegendaryInChat = true;
    public boolean announceMahitoInChat = true;
    public boolean announceComboInChat = true;  // Announce high five combo
    public boolean debugMode = false;  // Show debug messages in console

    // ============ FUNNY/CHAOS OPTIONS ============
    public boolean funnyMode = false;  // Enable all funny options
    public boolean randomizeKnockback = false;  // Knockback is random each time
    public boolean reverseGravity = false;  // All throws go UP instead of forward
    public boolean bouncyPlayers = false;  // Players bounce when they land
    public float chaosMultiplier = 1.0f;  // Multiply all effects by this (2.0 = double chaos!)

    /**
     * Get the config instance (loads from file if needed)
     */
    public static CoopMovesConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    /**
     * Load config from file, or create default if not exists
     */
    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, CoopMovesConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new CoopMovesConfig();
                }
            } catch (IOException e) {
                System.err.println("[CoopMoves] Failed to load config: " + e.getMessage());
                INSTANCE = new CoopMovesConfig();
            }
        } else {
            INSTANCE = new CoopMovesConfig();
            save(); // Create default config file
        }
    }

    /**
     * Save config to file
     */
    public static void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            System.err.println("[CoopMoves] Failed to save config: " + e.getMessage());
        }
    }

    /**
     * Reload config from file
     */
    public static void reload() {
        INSTANCE = null;
        load();
    }
}