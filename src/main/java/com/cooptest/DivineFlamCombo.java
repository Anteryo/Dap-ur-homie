package com.cooptest;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.*;


public class DivineFlamCombo {

    private static final Map<UUID, Long> comboWindowStart = new HashMap<>();
    private static final Map<UUID, UUID> comboPartner = new HashMap<>();

    private static final Map<UUID, Long> comboFreezeEnd = new HashMap<>();

    private static final long COMBO_WINDOW_MS = 1460;
    private static final long COMBO_FREEZE_MS = 3800;

    public static final Identifier DIVINE_J_PRESS_ID = Identifier.of("cooptest", "divine_j_press");
    public static final Identifier DIVINE_START_ID = Identifier.of("cooptest", "divine_start");

    public record DivineJPressPayload() implements CustomPayload {
        public static final Id<DivineJPressPayload> ID = new Id<>(DIVINE_J_PRESS_ID);
        public static final PacketCodec<PacketByteBuf, DivineJPressPayload> CODEC =
                PacketCodec.unit(new DivineJPressPayload());
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record DivineStartPayload() implements CustomPayload {
        public static final Id<DivineStartPayload> ID = new Id<>(DIVINE_START_ID);
        public static final PacketCodec<PacketByteBuf, DivineStartPayload> CODEC =
                PacketCodec.unit(new DivineStartPayload());
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(DivineJPressPayload.ID, DivineJPressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DivineStartPayload.ID, DivineStartPayload.CODEC);
        System.out.println("[Divine Flame] Payloads registered");
    }

    public static void register() {
        // Listen for J key press
        ServerPlayNetworking.registerGlobalReceiver(DivineJPressPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> onPlayerPressJ(player));
        });
        System.out.println("[Divine Flame] Handlers registered");
    }


    public static void startDivineFlame(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d midpoint) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();

        System.out.println("[Divine Flame] ===== STARTING DIVINE FLAME =====");

        comboWindowStart.put(id1, now);
        comboWindowStart.put(id2, now);
        comboPartner.put(id1, id2);
        comboPartner.put(id2, id1);

        System.out.println("[Divine Flame] Broadcasting ordinal 36 to both players");
        PoseNetworking.broadcastAnimState(p1, 36);
        PoseNetworking.broadcastAnimState(p2, 36);

        comboFreezeEnd.put(id1, now + COMBO_WINDOW_MS);
        comboFreezeEnd.put(id2, now + COMBO_WINDOW_MS);

        ServerPlayNetworking.send(p1, new DivineStartPayload());
        ServerPlayNetworking.send(p2, new DivineStartPayload());

        ServerWorld world = p1.getServerWorld();
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 1.5f, 1.1f);

        System.out.println("[Divine Flame] Divine Flame window opened! Players have 1.46s to press J");
    }


    private static void onPlayerPressJ(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();


        Long windowStart = comboWindowStart.get(playerId);
        if (windowStart == null) return;

        long elapsed = System.currentTimeMillis() - windowStart;
        if (elapsed > COMBO_WINDOW_MS) {
            comboWindowStart.remove(playerId);
            comboPartner.remove(playerId);
            return;
        }

        // Get partner
        UUID partnerId = comboPartner.get(playerId);
        if (partnerId == null) return;

        ServerPlayerEntity partner = player.getServer().getPlayerManager().getPlayer(partnerId);
        if (partner == null) return;

        // Check if partner also in window
        if (!comboWindowStart.containsKey(partnerId)) return;

        executeCombo(player, partner);
    }

   
    private static void executeCombo(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();

        System.out.println("[Divine Flame] ===== EXECUTING COMBO =====");
        System.out.println("[Divine Flame] Both players pressed J!");

        // Clear window
        comboWindowStart.remove(id1);
        comboWindowStart.remove(id2);
        comboPartner.remove(id1);
        comboPartner.remove(id2);

        comboFreezeEnd.put(id1, now + COMBO_FREEZE_MS);
        comboFreezeEnd.put(id2, now + COMBO_FREEZE_MS);

        System.out.println("[Divine Flame] Broadcasting ordinal 37 (P1) and 38 (P2)");
        PoseNetworking.broadcastAnimState(p1, 37);
        PoseNetworking.broadcastAnimState(p2, 38);

        ServerWorld world = p1.getServerWorld();
        Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5);

        System.out.println("[Divine Flame] Spawning Divine Flame vortex at " + midpoint);

        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 10.0f, 0.2f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.PLAYERS, 5.0f, 0.5f);

        System.out.println("[Divine Flame] Spawning particles...");

        // Particles
        world.spawnParticles(ParticleTypes.FLAME, midpoint.x, midpoint.y + 1, midpoint.z, 100, 1.0, 1.0, 1.0, 0.2);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, midpoint.x, midpoint.y + 1, midpoint.z, 50, 0.8, 0.8, 0.8, 0.15);
        world.spawnParticles(ParticleTypes.LAVA, midpoint.x, midpoint.y + 1, midpoint.z, 30, 0.5, 0.5, 0.5, 0.1);

        System.out.println("[Divine Flame] DIVINE FLAME SPAWNED! Frozen for 3.8 seconds");
    }

 
    public static void tick(ServerWorld world) {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, UUID> entry : comboPartner.entrySet()) {
            UUID id1 = entry.getKey();
            UUID id2 = entry.getValue();

            ServerPlayerEntity p1 = world.getServer().getPlayerManager().getPlayer(id1);
            ServerPlayerEntity p2 = world.getServer().getPlayerManager().getPlayer(id2);

            if (p1 != null && p2 != null) {
                double distance = p1.getPos().distanceTo(p2.getPos());

                if (distance > 1.0) {
                    Vec3d p1Pos = p1.getPos();
                    Vec3d p2Pos = p2.getPos();

                    Vec3d direction = p2Pos.subtract(p1Pos).normalize();

                    double targetDistance = 0.8;

                    Vec3d midpoint = p1Pos.add(p2Pos).multiply(0.5);
                    Vec3d offset = direction.multiply(targetDistance / 2.0);

                    Vec3d targetP1 = midpoint.subtract(offset);
                    Vec3d targetP2 = midpoint.add(offset);

                    double dx = p2Pos.x - p1Pos.x;
                    double dz = p2Pos.z - p1Pos.z;
                    float yawP1 = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
                    float yawP2 = yawP1 + 180; // Face opposite direction

                    p1.teleport(p1.getServerWorld(), targetP1.x, targetP1.y, targetP1.z, yawP1, p1.getPitch());
                    p2.teleport(p2.getServerWorld(), targetP2.x, targetP2.y, targetP2.z, yawP2, p2.getPitch());
                }
            }
        }

        comboWindowStart.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > COMBO_WINDOW_MS) {
                UUID id = entry.getKey();
                comboPartner.remove(id);
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);
                if (player != null) {
                    PoseNetworking.broadcastAnimState(player, 0);
                }
                return true;
            }
            return false;
        });

        comboFreezeEnd.entrySet().removeIf(entry -> {
            if (now >= entry.getValue()) {
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
                if (player != null) {
                    PoseNetworking.broadcastAnimState(player, 0);
                }
                return true;
            }
            return false;
        });
    }

   
    public static void cleanup(UUID playerId) {
        comboWindowStart.remove(playerId);
        comboPartner.remove(playerId);
        comboFreezeEnd.remove(playerId);
    }

    
    public static boolean isInComboFreeze(UUID playerId) {
        return comboFreezeEnd.containsKey(playerId);
    }
}
