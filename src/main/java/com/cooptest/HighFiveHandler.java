package com.cooptest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;

import java.util.*;


public class HighFiveHandler {

    // ==================== SETTINGS ====================
    public static final float HIGH_FIVE_RANGE = 1.6f;
    public static final long HAND_RAISED_DURATION = 2500;  // 2.5 sec before end anim plays
    public static final long COOLDOWN_MS = 1000;
    public static final long HIGH_FIVE_ANIM_DURATION = 800;  // Increased from 400 to let full animation play!

    public static final long START_ANIM_DELAY_MS = 0;        // Instant - no delay before can dap
    public static final long HIT_EFFECT_DELAY_MS = 100;    // 0.10 sec before effects on hit
    public static final long END_ANIM_DURATION_MS = 1500;   // 1.5 sec end animation

    public static final double SPEED_TIER_1 = 5.0;   
    public static final double SPEED_TIER_2 = 7.5;   
    public static final double SPEED_TIER_3 = 12.0;  
    public static final int SPEED_HISTORY_TICKS = 30;
    public static final Map<UUID, Long> handRaisedTime = new HashMap<>();
    public static final Map<UUID, Long> highFiveCooldown = new HashMap<>();
    public static final Map<UUID, Long> highFiveAnimStart = new HashMap<>();
    public static final Map<UUID, Long> startAnimTime = new HashMap<>();
    public static final Map<UUID, Long> endAnimTime = new HashMap<>();
    private static final Map<UUID, Long> comboWindowStart = new HashMap<>();  
    private static final Map<UUID, UUID> comboPartner = new HashMap<>();     
    private static final Map<UUID, Long> comboRequested = new HashMap<>();    
    private static final Map<UUID, Long> comboFreezeEnd = new HashMap<>();    
    private static final Map<UUID, ComboImpact> pendingComboImpacts = new HashMap<>();
    private static final long COMBO_WINDOW_MS = 400;  
    private static final long COMBO_FREEZE_MS = 2250; // Frozen until 2.25 seconds (full animation)
    private static final long COMBO_SECOND_HIT_MS = 1290; 

    private static final Map<UUID, Vec3d> frozenPositions = new HashMap<>();

    private static final Map<BlockPos, BeaconRemoval> pendingBeaconRemovals = new HashMap<>();

    private static class BeaconRemoval {
        BlockPos beaconPos, glassPos;
        ServerWorld world;
        long removeTime;
        BeaconRemoval(BlockPos beaconPos, BlockPos glassPos, ServerWorld world, long removeTime) {
            this.beaconPos = beaconPos;
            this.glassPos = glassPos;
            this.world = world;
            this.removeTime = removeTime;
        }
    }

  
    private static final List<ParticleBeam> activeParticleBeams = new ArrayList<>();

    private static class ParticleBeam {
        ServerWorld world;
        Vec3d startPos;
        boolean isYellow;
        long endTime;

        ParticleBeam(ServerWorld world, Vec3d startPos, boolean isYellow, long endTime) {
            this.world = world;
            this.startPos = startPos;
            this.isYellow = isYellow;
            this.endTime = endTime;
        }
    }

  
    private static void spawnParticleBeam(ServerWorld world, Vec3d playerPos, boolean isYellow) {
        long now = System.currentTimeMillis();
        long endTime = now + 1750; // Same duration as combo freeze

        activeParticleBeams.add(new ParticleBeam(world, playerPos.add(0, 0.5, 0), isYellow, endTime));
    }

 
    private static void tickParticleBeams(long now) {
        Iterator<ParticleBeam> it = activeParticleBeams.iterator();
        while (it.hasNext()) {
            ParticleBeam beam = it.next();

            if (now >= beam.endTime) {
                it.remove();
                continue;
            }

            for (int i = 0; i < 30; i++) {
                double y = beam.startPos.y + i * 0.5;

                if (beam.isYellow) {
                    beam.world.spawnParticles(
                            ParticleTypes.END_ROD,
                            beam.startPos.x, y, beam.startPos.z,
                            3, 0.15, 0, 0.15, 0  // Wider spread for visibility
                    );
                    beam.world.spawnParticles(
                            ParticleTypes.FLAME,
                            beam.startPos.x, y, beam.startPos.z,
                            2, 0.1, 0, 0.1, 0
                    );

                    double spiralAngle = i * 0.3;
                    double spiralRadius = 0.5;
                    double spiralX = beam.startPos.x + Math.cos(spiralAngle) * spiralRadius;
                    double spiralZ = beam.startPos.z + Math.sin(spiralAngle) * spiralRadius;
                    beam.world.spawnParticles(
                            ParticleTypes.END_ROD,
                            spiralX, y, spiralZ,
                            1, 0, 0, 0, 0
                    );
                } else {
                    beam.world.spawnParticles(
                            ParticleTypes.SQUID_INK,
                            beam.startPos.x, y, beam.startPos.z,
                            3, 0.15, 0, 0.15, 0  // Wider spread for visibility
                    );
                    beam.world.spawnParticles(
                            ParticleTypes.LARGE_SMOKE,
                            beam.startPos.x, y, beam.startPos.z,
                            2, 0.1, 0, 0.1, 0
                    );

                    double spiralAngle = i * 0.3;
                    double spiralRadius = 0.5;
                    double spiralX = beam.startPos.x + Math.cos(spiralAngle) * spiralRadius;
                    double spiralZ = beam.startPos.z + Math.sin(spiralAngle) * spiralRadius;
                    beam.world.spawnParticles(
                            ParticleTypes.SQUID_INK,
                            spiralX, y, spiralZ,
                            1, 0, 0, 0, 0
                    );
                }
            }
        }
    }

    private static class ComboImpact {
        ServerPlayerEntity p1, p2;
        long impactTime;
        ComboImpact(ServerPlayerEntity p1, ServerPlayerEntity p2, long impactTime) {
            this.p1 = p1;
            this.p2 = p2;
            this.impactTime = impactTime;
        }
    }

    private static final Map<UUID, PendingHighFive> pendingEffects = new HashMap<>();

    private static class PendingHighFive {
        ServerPlayerEntity p1, p2;
        Vec3d pos;
        int tier;
        long effectTime;

        PendingHighFive(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d pos, int tier, long effectTime) {
            this.p1 = p1;
            this.p2 = p2;
            this.pos = pos;
            this.tier = tier;
            this.effectTime = effectTime;
        }
    }

    public static final Map<UUID, LinkedList<Double>> speedHistory = new HashMap<>();

    public static final Identifier HIGH_FIVE_REQUEST_ID = Identifier.of("cooptest", "high_five_request");

    public record HighFiveRequestPayload() implements CustomPayload {
        public static final Id<HighFiveRequestPayload> ID = new Id<>(HIGH_FIVE_REQUEST_ID);
        public static final PacketCodec<PacketByteBuf, HighFiveRequestPayload> CODEC =
                PacketCodec.unit(new HighFiveRequestPayload());

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final Identifier HAND_RAISED_SYNC_ID = Identifier.of("cooptest", "hand_raised_sync");

    public record HandRaisedSyncPayload(UUID playerId, boolean raised) implements CustomPayload {
        public static final Id<HandRaisedSyncPayload> ID = new Id<>(HAND_RAISED_SYNC_ID);
        public static final PacketCodec<PacketByteBuf, HandRaisedSyncPayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> {
                            buf.writeUuid(payload.playerId);
                            buf.writeBoolean(payload.raised);
                        },
                        buf -> new HandRaisedSyncPayload(buf.readUuid(), buf.readBoolean())
                );

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final Identifier HIGH_FIVE_SUCCESS_ID = Identifier.of("cooptest", "high_five_success");

    public record HighFiveSuccessPayload(double x, double y, double z, UUID player1, UUID player2, int tier) implements CustomPayload {
        public static final Id<HighFiveSuccessPayload> ID = new Id<>(HIGH_FIVE_SUCCESS_ID);
        public static final PacketCodec<PacketByteBuf, HighFiveSuccessPayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> {
                            buf.writeDouble(payload.x);
                            buf.writeDouble(payload.y);
                            buf.writeDouble(payload.z);
                            buf.writeUuid(payload.player1);
                            buf.writeUuid(payload.player2);
                            buf.writeInt(payload.tier);
                        },
                        buf -> new HighFiveSuccessPayload(
                                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                                buf.readUuid(), buf.readUuid(), buf.readInt()
                        )
                );

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final Identifier HIGH_FIVE_ANIM_ID = Identifier.of("cooptest", "high_five_anim");

    public record HighFiveAnimPayload(UUID playerId, int animState) implements CustomPayload {
        public static final Id<HighFiveAnimPayload> ID = new Id<>(HIGH_FIVE_ANIM_ID);
        public static final PacketCodec<PacketByteBuf, HighFiveAnimPayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> {
                            buf.writeUuid(payload.playerId);
                            buf.writeInt(payload.animState);
                        },
                        buf -> new HighFiveAnimPayload(buf.readUuid(), buf.readInt())
                );

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // COMBO SYSTEM PAYLOADS
    public static final Identifier COMBO_REQUEST_ID = Identifier.of("cooptest", "highfive_combo_request");
    public static final Identifier COMBO_WINDOW_ID = Identifier.of("cooptest", "highfive_combo_window");
    public static final Identifier COMBO_WINDOW_CLOSE_ID = Identifier.of("cooptest", "highfive_combo_window_close");

    public record ComboRequestPayload() implements CustomPayload {
        public static final Id<ComboRequestPayload> ID = new Id<>(COMBO_REQUEST_ID);
        public static final PacketCodec<PacketByteBuf, ComboRequestPayload> CODEC =
                PacketCodec.unit(new ComboRequestPayload());

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ComboWindowPayload(UUID playerId) implements CustomPayload {
        public static final Id<ComboWindowPayload> ID = new Id<>(COMBO_WINDOW_ID);
        public static final PacketCodec<PacketByteBuf, ComboWindowPayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> buf.writeUuid(payload.playerId),
                        buf -> new ComboWindowPayload(buf.readUuid())
                );

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ComboWindowClosePayload(UUID playerId) implements CustomPayload {
        public static final Id<ComboWindowClosePayload> ID = new Id<>(COMBO_WINDOW_CLOSE_ID);
        public static final PacketCodec<PacketByteBuf, ComboWindowClosePayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> buf.writeUuid(payload.playerId),
                        buf -> new ComboWindowClosePayload(buf.readUuid())
                );

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final Identifier FREEZE_STATE_ID = Identifier.of("testcoop", "freeze_state");

    public record FreezeStatePayload(UUID playerId, boolean frozen) implements CustomPayload {
        public static final Id<FreezeStatePayload> ID = new Id<>(FREEZE_STATE_ID);
        public static final PacketCodec<PacketByteBuf, FreezeStatePayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> {
                            buf.writeUuid(payload.playerId);
                            buf.writeBoolean(payload.frozen);
                        },
                        buf -> new FreezeStatePayload(buf.readUuid(), buf.readBoolean())
                );

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final int ANIM_START = 1;
    public static final int ANIM_END = 2;
    public static final int ANIM_HIT = 3;

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(HighFiveRequestPayload.ID, HighFiveRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HighFiveSuccessPayload.ID, HighFiveSuccessPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HandRaisedSyncPayload.ID, HandRaisedSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HighFiveAnimPayload.ID, HighFiveAnimPayload.CODEC);
        // Combo system
        PayloadTypeRegistry.playC2S().register(ComboRequestPayload.ID, ComboRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ComboWindowPayload.ID, ComboWindowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ComboWindowClosePayload.ID, ComboWindowClosePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FreezeStatePayload.ID, FreezeStatePayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(HighFiveRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!CoopMovesConfig.get().enableHighFive) {
                    return;
                }
                onHighFiveRequest(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ComboRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                // Check config
                if (!CoopMovesConfig.get().enableHighFiveCombo) {
                    return;
                }
                onComboRequest(player);
            });
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();

            tickParticleBeams(now);

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();
                Vec3d velocity = player.getVelocity();

                double speed = velocity.length() * 20.0;

                LinkedList<Double> history = speedHistory.computeIfAbsent(id, k -> new LinkedList<>());

                history.addLast(speed);

                while (history.size() > SPEED_HISTORY_TICKS) {
                    history.removeFirst();
                }
            }

            // Check pending effects (0.25 sec delay for hit effects)
            Iterator<Map.Entry<UUID, PendingHighFive>> pendingIt = pendingEffects.entrySet().iterator();
            while (pendingIt.hasNext()) {
                Map.Entry<UUID, PendingHighFive> entry = pendingIt.next();
                PendingHighFive pending = entry.getValue();
                if (now >= pending.effectTime) {
                    // Execute the actual effects now
                    executeHighFiveEffects(pending.p1, pending.p2, pending.pos, pending.tier);
                    pendingIt.remove();
                }
            }

            // COMBO SYSTEM: Check expired combo windows
            Iterator<Map.Entry<UUID, Long>> comboWindowIt = comboWindowStart.entrySet().iterator();
            while (comboWindowIt.hasNext()) {
                Map.Entry<UUID, Long> entry = comboWindowIt.next();
                UUID playerId = entry.getKey();
                long windowStart = entry.getValue();

                if (now - windowStart > COMBO_WINDOW_MS) {
                    // Window expired - check who pressed and who didn't
                    boolean playerPressed = comboRequested.containsKey(playerId);
                    UUID partnerId = comboPartner.get(playerId);

                    if (playerPressed && partnerId != null) {
                        // This player pressed but partner didn't
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                        ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);

                        if (player != null && partner != null) {
                            boolean partnerPressed = comboRequested.containsKey(partnerId);
                            if (!partnerPressed) {
                                // Tell both who missed
                                player.sendMessage(net.minecraft.text.Text.literal("§c✗ " + partner.getName().getString() + " missed the combo!"), true);
                                partner.sendMessage(net.minecraft.text.Text.literal("§c✗ You missed the combo! " + player.getName().getString() + " pressed H!"), true);
                            }
                        }
                    }

                    comboWindowIt.remove();
                    comboPartner.remove(playerId);
                    comboRequested.remove(playerId);
                }
            }

            // COMBO SYSTEM: Check for pending second impacts
            Iterator<Map.Entry<UUID, ComboImpact>> comboImpactIt = pendingComboImpacts.entrySet().iterator();
            while (comboImpactIt.hasNext()) {
                Map.Entry<UUID, ComboImpact> entry = comboImpactIt.next();
                ComboImpact impact = entry.getValue();
                if (now >= impact.impactTime) {
                    // SECOND HIT! Execute at 0.63 seconds
                    executeSecondImpact(impact.p1, impact.p2);
                    comboImpactIt.remove();
                }
            }

            // COMBO SYSTEM: Check for combo freeze end (allow movement again)
            Iterator<Map.Entry<UUID, Long>> freezeIt = comboFreezeEnd.entrySet().iterator();
            while (freezeIt.hasNext()) {
                Map.Entry<UUID, Long> entry = freezeIt.next();
                if (now >= entry.getValue()) {
                    // Player can move again at 1.75 seconds
                    UUID playerId = entry.getKey();
                    freezeIt.remove();
                    frozenPositions.remove(playerId);

                    // CRITICAL: Clear hand raised state when combo ends (safety net!)
                    handRaisedTime.remove(playerId);
                    startAnimTime.remove(playerId);

                    // Tell client they can move again
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                    if (player != null) {
                        // Clear hand raised on client too!
                        syncHandRaised(player, false);

                        // Reset animation state to NONE!
                        PoseNetworking.broadcastAnimState(player, 0); // 0 = NONE

                        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                            ServerPlayNetworking.send(p, new FreezeStatePayload(playerId, false));
                        }
                        // CLOSE COMBO WINDOW - fixes persistent "PRESS H!" text!
                        ServerPlayNetworking.send(player, new ComboWindowClosePayload(playerId));
                        System.out.println("[HighFive] Combo ended - cleared hand raised and reset anim for " + player.getName().getString());
                    }
                }
            }

            // COMBO SYSTEM: Enforce frozen positions (teleport back if they move)
            for (Map.Entry<UUID, Vec3d> entry : frozenPositions.entrySet()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null) {
                    Vec3d frozenPos = entry.getValue();
                    Vec3d currentPos = player.getPos();

                    // If player moved more than 0.1 blocks, teleport back
                    if (currentPos.squaredDistanceTo(frozenPos) > 0.01) {
                        player.requestTeleport(frozenPos.x, frozenPos.y, frozenPos.z);
                        player.setVelocity(Vec3d.ZERO);
                        player.velocityModified = true;
                    }
                }
            }

            // CRITICAL FIX: Reset animation state after 400ms to prevent stuck HIGHFIVE_HIT!
            // But DON'T reset if player is in combo!
            Iterator<Map.Entry<UUID, Long>> animIt = highFiveAnimStart.entrySet().iterator();
            while (animIt.hasNext()) {
                Map.Entry<UUID, Long> entry = animIt.next();
                if (now - entry.getValue() > HIGH_FIVE_ANIM_DURATION) {
                    // Animation expired - reset to NONE!
                    UUID playerId = entry.getKey();

                    // DON'T reset if player is in combo freeze!
                    if (!comboFreezeEnd.containsKey(playerId)) {
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                        if (player != null) {
                            PoseNetworking.broadcastAnimState(player, 0); // 0 = NONE
                        }
                    } else {
                        // In combo - don't reset!
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                        if (player != null) {
                        }
                    }
                    animIt.remove();
                }
            }

            // COMBO SYSTEM: Remove expired beacons
            Iterator<Map.Entry<BlockPos, BeaconRemoval>> beaconIt = pendingBeaconRemovals.entrySet().iterator();
            while (beaconIt.hasNext()) {
                Map.Entry<BlockPos, BeaconRemoval> entry = beaconIt.next();
                BeaconRemoval removal = entry.getValue();
                if (now >= removal.removeTime) {
                    // Remove beacon and glass blocks
                    removal.world.setBlockState(removal.beaconPos, Blocks.AIR.getDefaultState());
                    removal.world.setBlockState(removal.glassPos, Blocks.AIR.getDefaultState());
                    beaconIt.remove();
                }
            }

            // Check end animation players (blocking state finished)
            Iterator<Map.Entry<UUID, Long>> endIt = endAnimTime.entrySet().iterator();
            while (endIt.hasNext()) {
                Map.Entry<UUID, Long> entry = endIt.next();
                if (now >= entry.getValue()) {
                    // End animation finished - player can act again
                    UUID playerId = entry.getKey();
                    endIt.remove();

                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                    if (player != null) {
                        // Reset animation state
                        PoseNetworking.broadcastAnimState(player, 0); // NONE
                    }
                }
            }

            Iterator<Map.Entry<UUID, Long>> it = handRaisedTime.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                UUID playerId = entry.getKey();
                long startAnimStartTime = startAnimTime.getOrDefault(playerId, entry.getValue());

                // Only timeout after the start animation delay has passed
                if (now - startAnimStartTime > START_ANIM_DELAY_MS + HAND_RAISED_DURATION) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                    it.remove();
                    startAnimTime.remove(playerId);

                    if (player != null) {
                        // Play end animation (blocking state!)
                        executeEndAnimation(player);
                        syncHandRaised(player, false);
                    }
                }
            }

            // Check for high five matches (only after start animation delay)
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID playerId = player.getUuid();
                if (!handRaisedTime.containsKey(playerId)) continue;
                if (isOnCooldown(playerId)) continue;
                if (isInBlockingState(playerId)) continue;

                // Check if start animation delay has passed
                Long startTime = startAnimTime.get(playerId);
                if (startTime != null && now - startTime < START_ANIM_DELAY_MS) {
                    continue; // Still in start animation, can't dap yet
                }

                ServerPlayerEntity partner = findHighFivePartner(player);
                if (partner != null) {
                    // Check partner is also past their start animation
                    Long partnerStartTime = startAnimTime.get(partner.getUuid());
                    if (partnerStartTime != null && now - partnerStartTime < START_ANIM_DELAY_MS) {
                        continue; // Partner still in start animation
                    }

                    executeHighFive(player, partner);
                }
            }
        });
    }

    /**
     * Check if player is in a blocking state (end animation playing)
     */
    public static boolean isInBlockingState(UUID playerId) {
        // Check if in ANY blocking animation (prevents G+H conflict)
        return isInBlockingAnimation(playerId)
                || FallDapHandler.isSquashed(playerId);
    }

    /**
     * Check if player is in high five mode (hand raised, waiting for partner)
     */
    public static boolean isInHighFiveMode(UUID playerId) {
        return handRaisedTime.containsKey(playerId) || startAnimTime.containsKey(playerId);
    }

    /**
     * Check if player is in ANY high five state (including hit animation)
     * This blocks ALL other actions until high five is completely done
     */
    public static boolean isInAnyHighFiveState(UUID playerId) {
        return isInHighFiveMode(playerId)
                || isInBlockingState(playerId)
                || highFiveAnimStart.containsKey(playerId);
    }

    
    public static boolean canPerformAction(UUID playerId) {
        return !isInBlockingState(playerId);
    }

    
    private static void executeEndAnimation(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        long now = System.currentTimeMillis();

        endAnimTime.put(playerId, now + END_ANIM_DURATION_MS);

        broadcastHighFiveAnim(player, ANIM_END);

        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos().add(0, 1.6, 0);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 0.5f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 0.4f, 1.2f);

        world.spawnParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 6, 0.15, 0.15, 0.15, 0.01);

        player.sendMessage(net.minecraft.text.Text.literal("§7*left hanging*"), true);
    }

   
    private static void broadcastHighFiveAnim(ServerPlayerEntity player, int animState) {
        var server = player.getServer();
        if (server == null) return;

        HighFiveAnimPayload payload = new HighFiveAnimPayload(player.getUuid(), animState);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

   
    private static double getMaxRecentSpeed(UUID playerId) {
        LinkedList<Double> history = speedHistory.get(playerId);
        if (history == null || history.isEmpty()) return 0.0;

        double maxSpeed = 0.0;
        for (Double speed : history) {
            if (speed > maxSpeed) maxSpeed = speed;
        }
        return maxSpeed;
    }

    private static void onHighFiveRequest(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        
        if (!handRaisedTime.containsKey(uuid) && ChargedDapHandler.isCharging(uuid)) {
            System.out.println("[HighFive] Blocked H raise - G is active!");
            syncHandRaised(player, false);
            return;
        }

        if (ChargedDapHandler.isInComboCooldown(uuid)) {
            System.out.println("[HighFive] Blocked H raise - combo cooldown active!");
            player.sendMessage(net.minecraft.text.Text.literal("§cWait 1 second after combo!"), true);
            syncHandRaised(player, false);
            return;
        }

        if (isInBlockingState(uuid)) return;

        if (FallCatchHandler.isInCatchReadyMode(uuid)) return;

        if (isOnCooldown(uuid)) return;

        if (!player.getMainHandStack().isEmpty()) {
            return;
        }

        if (handRaisedTime.containsKey(uuid)) {
            handRaisedTime.remove(uuid);
            startAnimTime.remove(uuid);
            syncHandRaised(player, false);

            executeEndAnimation(player);
        } else {
            long now = System.currentTimeMillis();
            handRaisedTime.put(uuid, now);
            startAnimTime.put(uuid, now);
            syncHandRaised(player, true);

            broadcastHighFiveAnim(player, ANIM_START);

            player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.3f, 1.5f);
        }
    }

    public static void syncHandRaised(ServerPlayerEntity player, boolean raised) {
        if (player == null) return;

        System.out.println("[HighFive Server] syncHandRaised: " + player.getName().getString() + " raised=" + raised);

        HandRaisedSyncPayload payload = new HandRaisedSyncPayload(player.getUuid(), raised);

        for (ServerPlayerEntity other : PlayerLookup.all(player.getServer())) {
            ServerPlayNetworking.send(other, payload);
        }
    }

    private static ServerPlayerEntity findHighFivePartner(ServerPlayerEntity player) {
        Box searchBox = player.getBoundingBox().expand(HIGH_FIVE_RANGE);
        long now = System.currentTimeMillis();

        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other == player) continue;
            if (!handRaisedTime.containsKey(other.getUuid())) continue;
            if (isOnCooldown(other.getUuid())) continue;
            if (isInBlockingState(other.getUuid())) continue;

            // Check if other player is past their start animation delay
            Long otherStartTime = startAnimTime.get(other.getUuid());
            if (otherStartTime != null && now - otherStartTime < START_ANIM_DELAY_MS) {
                continue; // Still in start animation
            }

            if (searchBox.intersects(other.getBoundingBox())) {
                return other;
            }
        }
        return null;
    }

    private static void executeHighFive(ServerPlayerEntity player1, ServerPlayerEntity player2) {
        long now = System.currentTimeMillis();
        highFiveCooldown.put(player1.getUuid(), now);
        highFiveCooldown.put(player2.getUuid(), now);

        handRaisedTime.remove(player1.getUuid());
        handRaisedTime.remove(player2.getUuid());
        startAnimTime.remove(player1.getUuid());
        startAnimTime.remove(player2.getUuid());

        syncHandRaised(player1, false);
        syncHandRaised(player2, false);
        System.out.println("[HighFive] Regular highfive - cleared hand raised for both players");

        highFiveAnimStart.put(player1.getUuid(), now);
        highFiveAnimStart.put(player2.getUuid(), now);

        broadcastHighFiveAnim(player1, ANIM_HIT);
        broadcastHighFiveAnim(player2, ANIM_HIT);

        PoseNetworking.broadcastAnimState(player1, 20); // HIGHFIVE_HIT
        PoseNetworking.broadcastAnimState(player2, 20); // HIGHFIVE_HIT

        double speed1 = getMaxRecentSpeed(player1.getUuid());
        double speed2 = getMaxRecentSpeed(player2.getUuid());
        double maxSpeed = Math.max(speed1, speed2);

        int tier = 0;
        if (maxSpeed >= SPEED_TIER_3) tier = 3;      // EXPLOSION
        else if (maxSpeed >= SPEED_TIER_2) tier = 2; // Big impact
        else if (maxSpeed >= SPEED_TIER_1) tier = 1; // Medium impact

        Vec3d pos1 = player1.getPos();
        Vec3d pos2 = player2.getPos();
        Vec3d highFivePos = pos1.add(pos2).multiply(0.5).add(0, 1.4, 0);

        pendingEffects.put(player1.getUuid(), new PendingHighFive(
                player1, player2, highFivePos, tier, now + HIT_EFFECT_DELAY_MS
        ));

        speedHistory.remove(player1.getUuid());
        speedHistory.remove(player2.getUuid());

        syncHandRaised(player1, false);
        syncHandRaised(player2, false);

        HighFiveSuccessPayload successPayload = new HighFiveSuccessPayload(
                highFivePos.x, highFivePos.y, highFivePos.z,
                player1.getUuid(), player2.getUuid(), tier
        );
        for (ServerPlayerEntity other : PlayerLookup.all(player1.getServer())) {
            ServerPlayNetworking.send(other, successPayload);
        }

        comboWindowStart.put(player1.getUuid(), now);
        comboWindowStart.put(player2.getUuid(), now);
        comboPartner.put(player1.getUuid(), player2.getUuid());
        comboPartner.put(player2.getUuid(), player1.getUuid());

        ServerPlayNetworking.send(player1, new ComboWindowPayload(player1.getUuid()));
        ServerPlayNetworking.send(player2, new ComboWindowPayload(player2.getUuid()));

        HighFiveHugHandler.startHugHold(player1, player2);
    }

    
    private static void executeHighFiveEffects(ServerPlayerEntity player1, ServerPlayerEntity player2,
                                               Vec3d highFivePos, int tier) {
        ServerWorld world = player1.getServerWorld();

        switch (tier) {
            case 0 -> executeTier0(world, highFivePos, player1, player2);
            case 1 -> executeTier1(world, highFivePos, player1, player2);
            case 2 -> executeTier2(world, highFivePos, player1, player2);
            case 3 -> executeTier3(world, highFivePos, player1, player2);
        }
    }

    private static void executeTier0(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.2f, 1.1f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.8f);

        spawnStarBurst(world, pos, 10, 0.3);
        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 8, 0.1, 0.1, 0.1, 0.08);
        world.spawnParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 6, 0.2, 0.2, 0.2, 0.02);

        // Complete stop on impact
        applyKnockback(p1, p2, pos, 0.1);
    }

    // ==================== TIER 1: Running - More impact ====================
    private static void executeTier1(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        // Louder sound
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.5f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 2.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.PLAYERS, 0.8f, 1.2f);

        // More particles
        spawnStarBurst(world, pos, 16, 0.5);
        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 15, 0.15, 0.15, 0.15, 0.12);
        world.spawnParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 10, 0.25, 0.25, 0.25, 0.03);
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 5, 0.2, 0.2, 0.2, 0.05);

        // Small knockback
        applyKnockback(p1, p2, pos, 0.4);
    }

    // ==================== TIER 2: Sprinting - BIG impact ====================
    private static void executeTier2(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        // Big sounds
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 2.0f, 0.9f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.2f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.2f, 2.0f);

        // Big particle burst
        spawnStarBurst(world, pos, 24, 0.7);
        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 25, 0.2, 0.2, 0.2, 0.18);
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 15, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 15, 0.3, 0.3, 0.3, 0.05);

        // Medium knockback
        applyKnockback(p1, p2, pos, 0.8);
    }

    // ==================== TIER 3: PERFECT DAP - BATTLE SHOCKWAVE! ====================
    private static void executeTier3(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 2.0f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 1.5f, 1.0f);

        // Plus explosion sounds
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.2f, 1.3f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 1.5f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 2.0f, 0.5f);

        spawnStarBurst(world, pos, 32, 1.0);
        world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 3, 0.5, 0.5, 0.5, 0);
        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 40, 0.3, 0.3, 0.3, 0.25);
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 25, 0.5, 0.5, 0.5, 0.15);
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 2, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 20, 0.4, 0.4, 0.4, 0.1);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 30, 0.5, 0.5, 0.5, 0.3);

        createBattleShockwave(world, pos, p1, p2, 10.0);

        ChargedDapHandler.applyImpactFreeze(p1, p2, 3);

        applyKnockback(p1, p2, pos, 0.3);

        p1.sendMessage(net.minecraft.text.Text.literal("§6§l⚡ SHOCKWAVE! ⚡"), true);
        p2.sendMessage(net.minecraft.text.Text.literal("§6§l⚡ SHOCKWAVE! ⚡"), true);
    }

  
    private static void createBattleShockwave(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, double radius) {
        for (int ring = 1; ring <= 5; ring++) {
            double r = ring * 2.0; // 2, 4, 6, 8, 10 blocks
            int points = (int)(r * 8);
            for (int i = 0; i < points; i++) {
                double angle = Math.toRadians((360.0 / points) * i);
                double x = pos.x + Math.cos(angle) * r;
                double z = pos.z + Math.sin(angle) * r;

                if (ring <= 2) {
                    world.spawnParticles(ParticleTypes.CLOUD, x, pos.y, z, 1, 0.1, 0.2, 0.1, 0.02);
                } else if (ring <= 4) {
                    world.spawnParticles(ParticleTypes.SWEEP_ATTACK, x, pos.y + 0.5, z, 1, 0, 0, 0, 0);
                } else {
                    world.spawnParticles(ParticleTypes.CRIT, x, pos.y + 0.5, z, 2, 0.1, 0.1, 0.1, 0.05);
                }
            }
        }

        Box pushBox = new Box(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );

        for (Entity entity : world.getOtherEntities(null, pushBox)) {
            if (entity == p1 || entity == p2) continue; // Don't push the high-fivers

            double dist = entity.getPos().distanceTo(pos);
            if (dist > radius || dist < 0.5) continue;

            double strength = (1.0 - (dist / radius)) * 4.0 + 1.0; // 1.0 to 5.0 (much stronger!)

            Vec3d dir = entity.getPos().subtract(pos).normalize();
            if (dir.lengthSquared() < 0.01) {
                dir = new Vec3d(Math.random() - 0.5, 0, Math.random() - 0.5).normalize();
            }

            entity.addVelocity(dir.x * strength, strength * 0.6, dir.z * strength);
            entity.velocityModified = true;

            world.spawnParticles(ParticleTypes.CRIT,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    5, 0.2, 0.2, 0.2, 0.1);
        }
    }

    private static void spawnStarBurst(ServerWorld world, Vec3d pos, int rays, double spread) {
        for (int i = 0; i < rays; i++) {
            double angle = (2 * Math.PI * i) / rays;
            double dx = Math.cos(angle) * spread;
            double dz = Math.sin(angle) * spread;

            world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 2, dx, 0.2, dz, 0.15);
        }
    }

    private static void applyKnockback(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d center, double strength) {
        Vec3d dir1 = p1.getPos().subtract(center).normalize();
        Vec3d dir2 = p2.getPos().subtract(center).normalize();

        if (dir1.lengthSquared() < 0.01) dir1 = new Vec3d(1, 0, 0);
        if (dir2.lengthSquared() < 0.01) dir2 = new Vec3d(-1, 0, 0);

        double push = 0.15 * strength;
        p1.setVelocity(dir1.x * push, 0.05, dir1.z * push);
        p2.setVelocity(dir2.x * push, 0.05, dir2.z * push);

        p1.velocityModified = true;
        p2.velocityModified = true;
    }

    private static void createHighFiveExplosion(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        double radius = 4.0;
        Box damageBox = new Box(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );

        for (Entity entity : world.getOtherEntities(null, damageBox)) {
            if (entity == p1 || entity == p2) continue; // Skip the high-fivers

            double dist = entity.getPos().distanceTo(pos);
            if (dist > radius) continue;

            double knockbackStrength = (1.0 - dist / radius) * 2.0;
            Vec3d knockDir = entity.getPos().subtract(pos).normalize();
            entity.addVelocity(knockDir.x * knockbackStrength, knockbackStrength * 0.5, knockDir.z * knockbackStrength);
            entity.velocityModified = true;

            if (entity instanceof ServerPlayerEntity target) {
                float damage = (float)((1.0 - dist / radius) * 8.0);
                target.damage(world.getDamageSources().explosion(null, null), damage);
            }
        }
    }

    private static boolean isOnCooldown(UUID uuid) {
        Long cooldownStart = highFiveCooldown.get(uuid);
        if (cooldownStart == null) return false;
        return System.currentTimeMillis() - cooldownStart < COOLDOWN_MS;
    }

    public static boolean hasHandRaised(UUID uuid) {
        return handRaisedTime.containsKey(uuid);
    }

   
    public static boolean isInBlockingAnimation(UUID uuid) {
        if (highFiveAnimStart.containsKey(uuid)) return true;
        if (endAnimTime.containsKey(uuid)) return true;
        if (comboFreezeEnd.containsKey(uuid)) return true;

        if (ChargedDapHandler.isInBlockingAnimation(uuid)) return true;

        return false;
    }

    public static float getHighFiveAnimProgress(UUID uuid) {
        Long startTime = highFiveAnimStart.get(uuid);
        if (startTime == null) return -1f;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > HIGH_FIVE_ANIM_DURATION) {
            highFiveAnimStart.remove(uuid);
            return -1f;
        }

        return (float) elapsed / HIGH_FIVE_ANIM_DURATION;
    }

    // ==================== COMBO SYSTEM ====================

   
    private static void onComboRequest(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        long now = System.currentTimeMillis();

        Long windowStart = comboWindowStart.get(playerId);
        if (windowStart == null) return;

        long elapsed = now - windowStart;
        if (elapsed > COMBO_WINDOW_MS) {
            comboWindowStart.remove(playerId);
            comboPartner.remove(playerId);
            comboRequested.remove(playerId);
            return;
        }

        comboRequested.put(playerId, now);

        UUID partnerId = comboPartner.get(playerId);
        if (partnerId == null) return;

        ServerPlayerEntity partner = player.getServer().getPlayerManager().getPlayer(partnerId);
        if (partner == null) return;

        if (!comboWindowStart.containsKey(partnerId)) {
            return;
        }

        if (comboRequested.containsKey(partnerId)) {
            executeCombo(player, partner);
        }
    }

    
    private static void executeCombo(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();

        comboWindowStart.remove(id1);
        comboWindowStart.remove(id2);
        comboPartner.remove(id1);
        comboPartner.remove(id2);
        comboRequested.remove(id1);
        comboRequested.remove(id2);

        handRaisedTime.remove(id1);
        handRaisedTime.remove(id2);
        startAnimTime.remove(id1);
        startAnimTime.remove(id2);
        syncHandRaised(p1, false);
        syncHandRaised(p2, false);
        System.out.println("[HighFive] Combo started - cleared hand raised for both players");

        comboFreezeEnd.put(id1, now + COMBO_FREEZE_MS);
        comboFreezeEnd.put(id2, now + COMBO_FREEZE_MS);

        frozenPositions.put(id1, p1.getPos());
        frozenPositions.put(id2, p2.getPos());

        p1.setVelocity(Vec3d.ZERO);
        p2.setVelocity(Vec3d.ZERO);
        p1.velocityModified = true;
        p2.velocityModified = true;

        for (ServerPlayerEntity p : PlayerLookup.all(p1.getServer())) {
            ServerPlayNetworking.send(p, new FreezeStatePayload(id1, true));
            ServerPlayNetworking.send(p, new FreezeStatePayload(id2, true));
        }

       
        PoseNetworking.broadcastAnimState(p1, 21); // HIGHFIVE_HIT_COMBO
        PoseNetworking.broadcastAnimState(p2, 21);

        pendingComboImpacts.put(id1, new ComboImpact(p1, p2, now + COMBO_SECOND_HIT_MS));

        p1.sendMessage(net.minecraft.text.Text.literal("§6§l COMBO! "), true);
        p2.sendMessage(net.minecraft.text.Text.literal("§6§l COMBO! "), true);
    }

   
    private static void executeSecondImpact(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        Vec3d pos = p1.getPos().add(p2.getPos()).multiply(0.5).add(0, 0.5, 0); // Lower - at waist level
        ServerWorld world = p1.getServerWorld();

        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);

        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.5f, 1.0f);

        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 30, 0.3, 0.3, 0.3, 0.1);

        world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 20, 0.3, 0.3, 0.3, 0.15);

        p1.sendMessage(net.minecraft.text.Text.literal("§e PERFECT! "), true);
        p2.sendMessage(net.minecraft.text.Text.literal("§e PERFECT! "), true);
    }

   
    private static void spawnComboAura(ServerWorld world, Vec3d pos1, Vec3d pos2) {
        int particleCount = 20;
        double radius = 1.5;

        for (int i = 0; i < particleCount; i++) {
            double angle = (2 * Math.PI * i) / particleCount;

           
            double x1 = pos1.x + Math.cos(angle) * radius;
            double z1 = pos1.z + Math.sin(angle) * radius;
            world.spawnParticles(ParticleTypes.SQUID_INK,
                    x1, pos1.y + 1, z1,
                    1, 0.1, 0.3, 0.1, 0.02);

       
            double innerRadius = radius * 0.7;
            double x1Inner = pos1.x + Math.cos(angle) * innerRadius;
            double z1Inner = pos1.z + Math.sin(angle) * innerRadius;
            world.spawnParticles(ParticleTypes.END_ROD,
                    x1Inner, pos1.y + 1, z1Inner,
                    1, 0.1, 0.3, 0.1, 0.02);

           
            double x2 = pos2.x + Math.cos(angle) * radius;
            double z2 = pos2.z + Math.sin(angle) * radius;
            world.spawnParticles(ParticleTypes.SQUID_INK,
                    x2, pos2.y + 1, z2,
                    1, 0.1, 0.3, 0.1, 0.02);

            // Gold particles (inner ring)
            double x2Inner = pos2.x + Math.cos(angle) * innerRadius;
            double z2Inner = pos2.z + Math.sin(angle) * innerRadius;
            world.spawnParticles(ParticleTypes.END_ROD,
                    x2Inner, pos2.y + 1, z2Inner,
                    1, 0.1, 0.3, 0.1, 0.02);
        }
    }

    
    public static boolean isInComboFreeze(UUID playerId) {
        Long freezeEnd = comboFreezeEnd.get(playerId);
        if (freezeEnd == null) return false;
        return System.currentTimeMillis() < freezeEnd;
    }

   
    public static void cleanup(UUID playerId) {
        handRaisedTime.remove(playerId);
        highFiveCooldown.remove(playerId);
        highFiveAnimStart.remove(playerId);
        startAnimTime.remove(playerId);
        endAnimTime.remove(playerId);
        speedHistory.remove(playerId);
        pendingEffects.remove(playerId);
        comboWindowStart.remove(playerId);
        comboPartner.remove(playerId);
        comboRequested.remove(playerId);
        comboFreezeEnd.remove(playerId);
        pendingComboImpacts.remove(playerId);
        frozenPositions.remove(playerId);
    }
}
