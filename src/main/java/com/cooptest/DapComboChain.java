package com.cooptest;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class DapComboChain {

    // Stage 0: dap_hit (0.96s total)
    private static final int STAGE0_EVALUATE_TICK = 18;   // 0.92s - check QTE
    private static final int STAGE0_ANIM_END = 20;        // 1.0s - cleanup buffer

    // Stage 1: extandp1/extandp2 (2.75s / 2.83s)
    private static final int STAGE1_SOUND_1 = 8;          // 0.42s - crit
    private static final int STAGE1_SOUND_2 = 15;         // 0.75s - crit
    private static final int STAGE1_SOUND_3 = 24;         // 1.21s - crit (at evaluate)
    private static final int STAGE1_EVALUATE_TICK = 24;   // 1.21s - check QTE
    private static final int STAGE1_ANIM_END = 57;        // 2.85s - let longer anim finish

    // Stage 2: extand_both (3.5s)
    private static final int STAGE2_SOUND_1 = 6;          // 0.29s - crit
    private static final int STAGE2_SOUND_2 = 23;         // 1.13s - crit
    private static final int STAGE2_SOUND_3 = 28;         // 1.42s - crit
    private static final int STAGE2_SOUND_4 = 39;         // 1.96s - crit + epic_dap
    private static final int STAGE2_EVALUATE_TICK = 42;   // 2.08s - check QTE
    private static final int STAGE2_ANIM_END = 70;        // 3.5s - full animation

    // Stage 3: myboyp1 (2.54s) - free movement
    private static final int STAGE3_MYBOY_SOUND_1 = 4;    // 0.21s - small dap sound + arm particles
    private static final int STAGE3_MYBOY_SOUND_2 = 8;    // 0.38s - small dap sound + arm particles
    private static final int STAGE3_ANIM_END = 51;        // 2.54s - animation done, final cleanup

    private static final String[] BUTTONS = {"G", "H"};
    private static final Random RANDOM = new Random();

    private static final Map<UUID, ComboSession> activeCombos = new HashMap<>();

    public static class ComboSession {
        public final UUID p1Id, p2Id;
        public ServerPlayerEntity p1Ref, p2Ref;
        public ServerWorld world;
        public Vec3d impactPos;

        public int stage;          // 0=dap_hit, 1=extend1, 2=extend_both, 3=final, negative=failing
        public int ticksInStage;
        public boolean evaluated;

        // QTE tracking
        public String expectedButton;
        public boolean p1Pressed, p2Pressed;
        public boolean qteWindowOpen;
        public boolean qteSent;

        ComboSession(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d pos) {
            this.p1Id = p1.getUuid();
            this.p2Id = p2.getUuid();
            this.p1Ref = p1;
            this.p2Ref = p2;
            this.world = p1.getServerWorld();
            this.impactPos = pos;
            this.stage = 0;
            this.ticksInStage = 0;
            this.evaluated = false;
            this.expectedButton = BUTTONS[RANDOM.nextInt(BUTTONS.length)];
            this.p1Pressed = false;
            this.p2Pressed = false;
            this.qteWindowOpen = false;
            this.qteSent = false;
        }

        void resetQTE() {
            p1Pressed = false;
            p2Pressed = false;
            qteWindowOpen = false;
            qteSent = false;
            evaluated = false;
            String old = expectedButton;
            do {
                expectedButton = BUTTONS[RANDOM.nextInt(BUTTONS.length)];
            } while (expectedButton.equals(old) && BUTTONS.length > 1);
        }

        void advanceStage() {
            stage++;
            ticksInStage = 0;
            resetQTE();
        }
    }


    public static void startCombo(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d impactPos) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        if (activeCombos.containsKey(id1) || activeCombos.containsKey(id2)) return;

        ComboSession session = new ComboSession(p1, p2, impactPos);
        activeCombos.put(id1, session);
        activeCombos.put(id2, session);

        // Freeze both players
        ServerPlayNetworking.send(p1, new ChargedDapHandler.PerfectDapFreezePayload(true));
        ServerPlayNetworking.send(p2, new ChargedDapHandler.PerfectDapFreezePayload(true));

        // Open QTE immediately
        openQTEWindow(session);

    }


    public static boolean onButtonPress(ServerPlayerEntity player, String button) {
        UUID id = player.getUuid();
        ComboSession session = activeCombos.get(id);
        if (session == null) return false;
        if (!session.qteWindowOpen) return true;
        if (!button.equals(session.expectedButton)) return true;

        if (id.equals(session.p1Id)) session.p1Pressed = true;
        else if (id.equals(session.p2Id)) session.p2Pressed = true;

        return true;
    }

    public static boolean isInCombo(UUID playerId) {
        return activeCombos.containsKey(playerId);
    }

    public static void cancelCombo(UUID playerId) {
        ComboSession session = activeCombos.get(playerId);
        if (session == null) return;
        cleanup(session, false);
    }


    public static void tick(net.minecraft.server.MinecraftServer server) {
        List<ComboSession> toTick = activeCombos.values().stream().distinct().toList();
        for (ComboSession session : toTick) {
            ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(session.p1Id);
            ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(session.p2Id);
            if (p1 == null || p2 == null || p1.isDead() || p2.isDead()) {
                cleanup(session, false);
                continue;
            }
            session.p1Ref = p1;
            session.p2Ref = p2;
            tickSession(session);
        }
    }

    private static void tickSession(ComboSession s) {
        s.ticksInStage++;

        switch (s.stage) {
            case 0 -> tickStage0(s);
            case 1 -> tickStage1(s);
            case 2 -> tickStage2(s);
            case 3 -> tickStage3(s);
            // Negative stages = waiting for failed animation to finish
            case -1 -> { if (s.ticksInStage >= STAGE0_ANIM_END) cleanup(s, false); }
            case -2 -> { if (s.ticksInStage >= STAGE1_ANIM_END) cleanup(s, false); }
            case -3 -> { if (s.ticksInStage >= STAGE2_ANIM_END) cleanup(s, false); }
            default -> cleanup(s, false);
        }
    }


    private static void tickStage0(ComboSession s) {
        if (s.ticksInStage >= STAGE0_EVALUATE_TICK && !s.evaluated) {
            s.evaluated = true;
            closeQTEWindow(s);

            if (s.p1Pressed && s.p2Pressed) {
                startExtend1(s);
            } else {
                sendFailMessage(s);
                s.stage = -1; // Let dap_hit finish before cleanup
            }
        }
    }

    // ==================== STAGE 1: extandp1 / extandp2 ====================

    private static void startExtend1(ComboSession s) {
        s.advanceStage(); // stage = 1

        PoseNetworking.broadcastAnimState(s.p1Ref,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND1_P1.ordinal());
        PoseNetworking.broadcastAnimState(s.p2Ref,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND1_P2.ordinal());

        ServerPlayNetworking.send(s.p1Ref, new ChargedDapHandler.PerfectDapFreezePayload(true));
        ServerPlayNetworking.send(s.p2Ref, new ChargedDapHandler.PerfectDapFreezePayload(true));

        openQTEWindow(s);
    }

    private static void tickStage1(ComboSession s) {
        // Sound + particles at specific timestamps
        if (s.ticksInStage == STAGE1_SOUND_1 || s.ticksInStage == STAGE1_SOUND_2
                || s.ticksInStage == STAGE1_SOUND_3) {
            playCritEffects(s);
        }

        if (s.ticksInStage >= STAGE1_EVALUATE_TICK && !s.evaluated) {
            s.evaluated = true;
            closeQTEWindow(s);

            if (s.p1Pressed && s.p2Pressed) {
                startExtend2(s);
            } else {
                sendFailMessage(s);
                s.stage = -2; // Let animation finish
            }
        }
    }


    private static void startExtend2(ComboSession s) {
        s.advanceStage(); // stage = 2

        PoseNetworking.broadcastAnimState(s.p1Ref,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND_BOTH.ordinal());
        PoseNetworking.broadcastAnimState(s.p2Ref,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND_BOTH.ordinal());

        openQTEWindow(s);
    }

    private static void tickStage2(ComboSession s) {
        if (s.ticksInStage == STAGE2_SOUND_1 || s.ticksInStage == STAGE2_SOUND_2
                || s.ticksInStage == STAGE2_SOUND_3) {
            playCritEffects(s);
        }

        if (s.ticksInStage == STAGE2_SOUND_4) {
            playCritEffects(s);
            s.world.playSound(null, s.impactPos.x, s.impactPos.y, s.impactPos.z,
                    ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 1.5f, 1.0f);
        }

        if (s.ticksInStage >= STAGE2_EVALUATE_TICK && !s.evaluated) {
            s.evaluated = true;
            closeQTEWindow(s);

            if (s.p1Pressed && s.p2Pressed) {
                startFinal(s);
            } else {
                sendFailMessage(s);
                s.stage = -3;
            }
        }
    }


    private static void startFinal(ComboSession s) {
        s.advanceStage();

        DapSessionManager.removeSessionForPlayer(s.p1Id);

        PoseNetworking.broadcastAnimState(s.p1Ref,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_MYBOY_P1.ordinal());
        PoseNetworking.broadcastAnimState(s.p2Ref,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_MYBOY_P1.ordinal());

        ServerPlayNetworking.send(s.p1Ref, new ChargedDapHandler.PerfectDapFreezePayload(false));
        ServerPlayNetworking.send(s.p2Ref, new ChargedDapHandler.PerfectDapFreezePayload(false));

        s.p1Ref.sendMessage(Text.literal("§d§l★ MY HOMIE! "), true);
        s.p2Ref.sendMessage(Text.literal("§d§l★ MY HOMIE! "), true);

        spawnFinishEffect(s);
    }

    private static void tickStage3(ComboSession s) {
        // Small dap sounds + arm particles at 0.21s and 0.38s
        if (s.ticksInStage == STAGE3_MYBOY_SOUND_1 || s.ticksInStage == STAGE3_MYBOY_SOUND_2) {
            playSmallArmEffect(s);
        }

        // Animation done → final cleanup
        if (s.ticksInStage >= STAGE3_ANIM_END) {
            cleanup(s, true);
        }
    }


    private static void openQTEWindow(ComboSession s) {
        if (s.qteSent) return;
        s.qteSent = true;
        s.qteWindowOpen = true;

        long now = System.currentTimeMillis();
        int evaluateTick = switch (s.stage) {
            case 0 -> STAGE0_EVALUATE_TICK;
            case 1 -> STAGE1_EVALUATE_TICK;
            case 2 -> STAGE2_EVALUATE_TICK;
            default -> 20;
        };
        long windowDurationMs = (long) evaluateTick * 50;

        ServerPlayNetworking.send(s.p1Ref, new QTEWindowPayload(
                s.p1Id, s.expectedButton, s.stage + 1, now, now + windowDurationMs));
        ServerPlayNetworking.send(s.p2Ref, new QTEWindowPayload(
                s.p2Id, s.expectedButton, s.stage + 1, now, now + windowDurationMs));
    }

    private static void closeQTEWindow(ComboSession s) {
        s.qteWindowOpen = false;
        ServerPlayNetworking.send(s.p1Ref, new QTEClearPayload(s.p1Id));
        ServerPlayNetworking.send(s.p2Ref, new QTEClearPayload(s.p2Id));
    }


    private static void playCritEffects(ComboSession s) {
        for (ServerPlayerEntity player : List.of(s.p1Ref, s.p2Ref)) {
            Vec3d armPos = getRightArmTip(player);
            s.world.playSound(null, armPos.x, armPos.y, armPos.z,
                    SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.2f, 1.0f);
            s.world.spawnParticles(ParticleTypes.CRIT, armPos.x, armPos.y, armPos.z,
                    8, 0.12, 0.12, 0.12, 0.1);
            s.world.spawnParticles(ParticleTypes.ENCHANTED_HIT, armPos.x, armPos.y, armPos.z,
                    5, 0.08, 0.08, 0.08, 0.05);
        }
    }

    private static void playSmallArmEffect(ComboSession s) {
        for (ServerPlayerEntity player : List.of(s.p1Ref, s.p2Ref)) {
            Vec3d armPos = getRightArmTip(player);
            s.world.playSound(null, armPos.x, armPos.y, armPos.z,
                    ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 0.3f, 0.6f);
            s.world.spawnParticles(ParticleTypes.CRIT, armPos.x, armPos.y, armPos.z,
                    4, 0.08, 0.08, 0.08, 0.05);
        }
    }

    private static void spawnFinishEffect(ComboSession s) {
        Vec3d mid = s.p1Ref.getPos().add(s.p2Ref.getPos()).multiply(0.5).add(0, 1.0, 0);

        s.world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, mid.x, mid.y, mid.z,
                40, 0.5, 0.5, 0.5, 0.2);
        s.world.spawnParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z,
                20, 0.3, 0.8, 0.3, 0.1);
        s.world.spawnParticles(ParticleTypes.FLASH, mid.x, mid.y, mid.z,
                2, 0, 0, 0, 0);
        s.world.playSound(null, mid.x, mid.y, mid.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 2.0f, 0.8f);

        for (ServerPlayerEntity player : List.of(s.p1Ref, s.p2Ref)) {
            Vec3d armPos = getRightArmTip(player);
            s.world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, armPos.x, armPos.y, armPos.z,
                    15, 0.15, 0.15, 0.15, 0.15);
        }
    }

    private static Vec3d getRightArmTip(ServerPlayerEntity player) {
        double yawRad = Math.toRadians(player.getBodyYaw());
        double rightX = -Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        return new Vec3d(
                player.getX() + rightX * 0.3 + forwardX * 0.4,
                player.getY() + 1.0,
                player.getZ() + rightZ * 0.3 + forwardZ * 0.4
        );
    }


    private static void sendFailMessage(ComboSession s) {
        if (!s.p1Pressed && !s.p2Pressed) return; // Both missed → silence

        String missedName = !s.p1Pressed
                ? s.p1Ref.getName().getString()
                : s.p2Ref.getName().getString();

        Text msg = Text.literal("§c" + missedName + " missed the extend!");
        s.p1Ref.sendMessage(msg, true);
        s.p2Ref.sendMessage(msg, true);
    }


    private static void cleanup(ComboSession s, boolean success) {
        if (s.qteWindowOpen) closeQTEWindow(s);

        if (s.p1Ref != null)
            ServerPlayNetworking.send(s.p1Ref, new ChargedDapHandler.PerfectDapFreezePayload(false));
        if (s.p2Ref != null)
            ServerPlayNetworking.send(s.p2Ref, new ChargedDapHandler.PerfectDapFreezePayload(false));

        if (s.p1Ref != null)
            PoseNetworking.broadcastAnimState(s.p1Ref,
                    com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());
        if (s.p2Ref != null)
            PoseNetworking.broadcastAnimState(s.p2Ref,
                    com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());

        DapSessionManager.removeSessionForPlayer(s.p1Id);
        HighFiveHandler.cleanup(s.p1Id);
        HighFiveHandler.cleanup(s.p2Id);

        activeCombos.remove(s.p1Id);
        activeCombos.remove(s.p2Id);

    }
}