package com.cooptest;

import com.cooptest.HeavenDapPayloads;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;
import java.util.Random;


public class ChargedDapHandler {

    public static final float DAP_RANGE = 1.6f;
    public static final long CHARGE_TIME_MS = 250;             
    public static final long RELEASE_WINDOW_MS = 500;
    public static final long PERFECT_WINDOW_MS = 85;         
    public static final long GOOD_SYNC_MS = 300;
    public static final long COOLDOWN_MS = 1500;
    public static final long WHIFF_COOLDOWN_MS = 800;       
    public static final long FIRE_DELAY_MS = 2000;            
    public static final long FIRE_BUILD_TIME_MS = 2000;      
    public static final double MIN_MOVEMENT_SPEED = 1.5;     
    public static final long FIRE_GRACE_PERIOD_MS = 500;     
    public static final double SPEED_BONUS_THRESHOLD = 8.0;
    public static final double SPEED_TIER_4_THRESHOLD = 25.0; 
    public static final double PERFECT_LEGENDARY_MIN_INDIVIDUAL_SPEED = 10.0; 
    public static final int SPEED_HISTORY_TICKS = 40;
   
    public static final Map<UUID, Long> chargeStartTime = new HashMap<>();
    public static final Map<UUID, Long> releaseTime = new HashMap<>();
    public static final Map<UUID, UUID> waitingForPartner = new HashMap<>();
    public static final Map<UUID, Long> cooldowns = new HashMap<>();

    public static final Map<UUID, Long> fireStartTime = new HashMap<>();
    public static final Map<UUID, Long> fireGraceTime = new HashMap<>(); 
    public static final Map<UUID, Float> fireLevel = new HashMap<>(); 

    public static final Map<UUID, Long> fireMaxedStartTime = new HashMap<>();
    public static final Set<UUID> heavenReady = new HashSet<>();              
    public static final long HEAVEN_READY_TIME_MS = 5000;                     

    private static class HeavenParticleSpawner {
        final ServerWorld world;
        final Vec3d pos;
        final long startTime;
        final long endTime;

        HeavenParticleSpawner(ServerWorld world, Vec3d pos, long durationMs) {
            this.world = world;
            this.pos = pos;
            this.startTime = System.currentTimeMillis();
            this.endTime = this.startTime + durationMs;
        }
    }
    private static final List<HeavenParticleSpawner> activeHeavenParticles = new ArrayList<>();

    public static final Map<UUID, LinkedList<Double>> speedHistory = new HashMap<>();

    public static final Map<UUID, Integer> impactFreezeTicks = new HashMap<>();

    private static final Map<UUID, Long> perfectDapStartTime = new HashMap<>();
    private static final Map<UUID, UUID> perfectDapPartner = new HashMap<>();
    private static final Map<UUID, Long> perfectDapFreezeEnd = new HashMap<>();
    private static final Map<UUID, Boolean> perfectDapImpactSent = new HashMap<>();  // Track if impact frames sent
    private static final Map<UUID, Long> comboCooldown = new HashMap<>();  // Cooldown after combo ends


    private static final Map<UUID, Boolean> fireComboActive = new HashMap<>();

    private static final Map<UUID, Long> underwaterRemovalStart = new HashMap<>();
    private static final Map<UUID, Vec3d> underwaterRemovalPos = new HashMap<>();
    private static final Map<UUID, ServerWorld> underwaterRemovalWorld = new HashMap<>();

    
    private static final long FIRE_DAP_HIT_LENGTH = 2292; 
    private static final long FIRE_IMPACT_TIME = 210; 
    private static final long FIRE_J_WINDOW_START = 830;  
    private static final long FIRE_J_WINDOW_END = 2200;   
    private static final long FIRE_WINDOW_START = 710; 
    private static final long FIRE_WINDOW_END = 1420; 

    private static final long FIRE_COMBO_FREEZE_MS = 4000; // Frozen until 4 seconds (longer animations!)
    private static final long FIRE_COMBO_ARM_IMPACT = 1330; // 1.33s
    private static final long FIRE_COMBO_TORNADO = 1460; // 1.46s

    // Fire Dap state tracking
    private static final Map<UUID, Long> fireDapStartTime = new HashMap<>();
    private static final Map<UUID, UUID> fireDapPartner = new HashMap<>();
    private static final Map<UUID, Boolean> inFireDapHit = new HashMap<>();
    private static final Map<UUID, Boolean> fireCircleSpawned = new HashMap<>();  // Track if fire circle already spawned

    // Fire Combo tracking
    private static final Map<UUID, Long> fireDapComboRequestTime = new HashMap<>();
    private static final Map<UUID, Long> fireDapComboFreezeEnd = new HashMap<>();

    // Heaven Dap state tracking
    private static final Map<UUID, HeavenDapData> heavenPlayers = new HashMap<>();

    static class HeavenDapData {
        Vec3d originalMidpoint;
        ServerWorld world;
        long startTime;
        UUID partnerId;

        HeavenDapData(Vec3d originalMidpoint, ServerWorld world, long startTime, UUID partnerId) {
            this.originalMidpoint = originalMidpoint;
            this.world = world;
            this.startTime = startTime;
            this.partnerId = partnerId;
        }
    }

    private static class TornadoSwirlEntity {
        final Vec3d startCenter;
        final ServerWorld world;
        double angle;           // Current rotation angle
        double height;          // Current height above ground
        double radius;          // Distance from center
        int age;                // Ticks alive

        TornadoSwirlEntity(ServerWorld world, Vec3d center, double startAngle, double startRadius) {
            this.world = world;
            this.startCenter = center;
            this.angle = startAngle;
            this.height = 0;
            this.radius = startRadius;
            this.age = 0;
        }

        boolean tick() {
            height += 0.2;  
            angle += 18;  

            if (height < 30) {
                radius += 0.08;  // Expand as it rises
            } else {
                radius -= 0.05;  
            }

            double x = startCenter.x + Math.cos(Math.toRadians(angle)) * radius;
            double z = startCenter.z + Math.sin(Math.toRadians(angle)) * radius;
            double y = startCenter.y + height;

            world.spawnParticles(ParticleTypes.FLAME, x, y, z, 8, 0.3, 0.3, 0.3, 0.08);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 4, 0.2, 0.2, 0.2, 0.05);

            if (height % 5 < 0.5) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 6, 0.4, 0.4, 0.4, 0.06);
                world.spawnParticles(ParticleTypes.LAVA, x, y, z, 3, 0.2, 0.2, 0.2, 0.02);
            }

            age++;

            return height < 60 && age < 60;
        }
    }

    private static final List<TornadoSwirlEntity> activeTornadoSwirls = new ArrayList<>();
    private static long tornadoStartTime = 0;
    private static boolean tornadoActive = false;
    private static Vec3d tornadoCenter = null;
    private static ServerWorld tornadoWorld = null;

    private static long auraBeamStartTime = 0;
    private static boolean auraBeamsActive = false;
    private static UUID auraBeamPlayer1 = null;
    private static UUID auraBeamPlayer2 = null;

    private static final Map<UUID, Vec3d> smoothTPTarget = new HashMap<>();
    private static final Map<UUID, Integer> smoothTPProgress = new HashMap<>();
    private static final Map<UUID, net.minecraft.entity.decoration.ArmorStandEntity> fireDapArmorStands = new HashMap<>();
    private static final Map<UUID, net.minecraft.entity.decoration.ArmorStandEntity> perfectDapArmorStands = new HashMap<>();

    private static class FireDapScheduledEvent {
        final ServerPlayerEntity p1;
        final ServerPlayerEntity p2;
        final long executeTime;
        FireDapScheduledEvent(ServerPlayerEntity p1, ServerPlayerEntity p2, long executeTime) {
            this.p1 = p1;
            this.p2 = p2;
            this.executeTime = executeTime;
        }
    }
    private static final Map<UUID, FireDapScheduledEvent> pendingFireArmImpacts = new HashMap<>();
    private static final Map<UUID, FireDapScheduledEvent> pendingFireTornadoSpawns = new HashMap<>();

    private static class SaturnRing {
        final Vec3d center;
        final long startTime;
        final long endTime;
        SaturnRing(Vec3d center, long startTime) {
            this.center = center;
            this.startTime = startTime;
            this.endTime = startTime + 20000; // 20 seconds
        }
    }
    private static final List<SaturnRing> activeSaturnRings = new ArrayList<>();


    private static final Map<UUID, Long> impactFreezeEnd = new HashMap<>();
    private static final long IMPACT_FREEZE_MS = 90; // 0.09 seconds

    
    public static void freezeOnImpact(UUID playerId) {
        long now = System.currentTimeMillis();
        impactFreezeEnd.put(playerId, now + IMPACT_FREEZE_MS);
    }

  
    public static boolean isInImpactFreeze(UUID playerId) {
        Long freezeEnd = impactFreezeEnd.get(playerId);
        if (freezeEnd == null) return false;

        if (System.currentTimeMillis() >= freezeEnd) {
            impactFreezeEnd.remove(playerId);
            return false;
        }
        return true;
    }




    public static final Map<UUID, Long> blockingAnimEndTime = new HashMap<>();
    private static final List<ScheduledParticles> scheduledParticles = new ArrayList<>();
    private static final Set<UUID> highFivePartners = new HashSet<>();
    private static final Map<UUID, Long> perfectFriendshipLevitation = new HashMap<>();
    private static final Map<UUID, UUID> perfectFriendshipPartner = new HashMap<>(); 
    private record ScheduledParticles(ServerWorld world, double x, double y, double z, long spawnTime) {}
    private record ScheduledPerfectDapEffect(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, long effectTime) {}
    private static final List<ScheduledPerfectDapEffect> scheduledPerfectDapEffects = new ArrayList<>();
    public static final Identifier CHARGE_START_ID = Identifier.of("cooptest", "charged_dap_start");
    public static final Identifier CHARGE_RELEASE_ID = Identifier.of("cooptest", "charged_dap_release");
    public static final Identifier CHARGE_SYNC_ID = Identifier.of("cooptest", "charged_dap_sync");
    public static final Identifier DAP_RESULT_ID = Identifier.of("cooptest", "charged_dap_result");
    public static final Identifier WHIFF_COOLDOWN_ID = Identifier.of("cooptest", "whiff_cooldown");
    public static final Identifier IMPACT_FRAME_ID = Identifier.of("cooptest", "impact_frame");

    public record ChargeStartPayload() implements CustomPayload {
        public static final Id<ChargeStartPayload> ID = new Id<>(CHARGE_START_ID);
        public static final PacketCodec<PacketByteBuf, ChargeStartPayload> CODEC =
                PacketCodec.unit(new ChargeStartPayload());
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ChargeReleasePayload() implements CustomPayload {
        public static final Id<ChargeReleasePayload> ID = new Id<>(CHARGE_RELEASE_ID);
        public static final PacketCodec<PacketByteBuf, ChargeReleasePayload> CODEC =
                PacketCodec.unit(new ChargeReleasePayload());
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record WhiffCooldownPayload(long cooldownDurationMs) implements CustomPayload {
        public static final Id<WhiffCooldownPayload> ID = new Id<>(WHIFF_COOLDOWN_ID);
        public static final PacketCodec<PacketByteBuf, WhiffCooldownPayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> buf.writeLong(payload.cooldownDurationMs),
                        buf -> new WhiffCooldownPayload(buf.readLong())
                );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ImpactFramePayload(int durationMs, boolean grayscale) implements CustomPayload {
        public static final Id<ImpactFramePayload> ID = new Id<>(IMPACT_FRAME_ID);
        public static final PacketCodec<PacketByteBuf, ImpactFramePayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> {
                            buf.writeInt(payload.durationMs);
                            buf.writeBoolean(payload.grayscale);
                        },
                        buf -> new ImpactFramePayload(buf.readInt(), buf.readBoolean())
                );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // Perfect dap freeze state
    public static final Identifier PERFECT_DAP_FREEZE_ID = Identifier.of("cooptest", "perfect_dap_freeze");

    public record PerfectDapFreezePayload(boolean frozen) implements CustomPayload {
        public static final Id<PerfectDapFreezePayload> ID = new Id<>(PERFECT_DAP_FREEZE_ID);
        public static final PacketCodec<PacketByteBuf, PerfectDapFreezePayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> buf.writeBoolean(payload.frozen),
                        buf -> new PerfectDapFreezePayload(buf.readBoolean())
                );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final Identifier PERFECT_DAP_IMPACT_FRAME_ID = Identifier.of("cooptest", "perfect_dap_impact_frame");

    public record PerfectDapImpactFramePayload(int frameIndex) implements CustomPayload {
        public static final Id<PerfectDapImpactFramePayload> ID = new Id<>(PERFECT_DAP_IMPACT_FRAME_ID);
        public static final PacketCodec<PacketByteBuf, PerfectDapImpactFramePayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> buf.writeInt(payload.frameIndex),
                        buf -> new PerfectDapImpactFramePayload(buf.readInt())
                );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final Identifier FIRE_DAP_J_PRESS_ID = Identifier.of("cooptest", "fire_dap_j_press");
    public static final Identifier FIRE_DAP_WINDOW_ID = Identifier.of("cooptest", "fire_dap_window");
    public static final Identifier FIRE_DAP_FREEZE_ID = Identifier.of("cooptest", "fire_dap_freeze");
    public static final Identifier FIRE_DAP_FP_ID = Identifier.of("cooptest", "fire_dap_fp");

    public record FireDapJPressPayload() implements CustomPayload {
        public static final Id<FireDapJPressPayload> ID = new Id<>(FIRE_DAP_J_PRESS_ID);
        public static final PacketCodec<PacketByteBuf, FireDapJPressPayload> CODEC =
                PacketCodec.unit(new FireDapJPressPayload());
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record FireDapWindowPayload() implements CustomPayload {
        public static final Id<FireDapWindowPayload> ID = new Id<>(FIRE_DAP_WINDOW_ID);
        public static final PacketCodec<PacketByteBuf, FireDapWindowPayload> CODEC =
                PacketCodec.unit(new FireDapWindowPayload());
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record FireDapFreezePayload(UUID playerId, boolean frozen) implements CustomPayload {
        public static final Id<FireDapFreezePayload> ID = new Id<>(FIRE_DAP_FREEZE_ID);
        public static final PacketCodec<PacketByteBuf, FireDapFreezePayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.playerId);
                    buf.writeBoolean(payload.frozen);
                },
                buf -> new FireDapFreezePayload(buf.readUuid(), buf.readBoolean())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record FireDapFirstPersonPayload(UUID playerId, boolean showBothHands) implements CustomPayload {
        public static final Id<FireDapFirstPersonPayload> ID = new Id<>(FIRE_DAP_FP_ID);
        public static final PacketCodec<PacketByteBuf, FireDapFirstPersonPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.playerId);
                    buf.writeBoolean(payload.showBothHands);
                },
                buf -> new FireDapFirstPersonPayload(buf.readUuid(), buf.readBoolean())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }


    public record HeavenReadyPayload(UUID playerId, boolean ready) implements CustomPayload {
        public static final Id<HeavenReadyPayload> ID = new Id<>(Identifier.of("cooptest", "heaven_ready"));
        public static final PacketCodec<PacketByteBuf, HeavenReadyPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.playerId);
                    buf.writeBoolean(payload.ready);
                },
                buf -> new HeavenReadyPayload(buf.readUuid(), buf.readBoolean())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    

    public record ChargeSyncPayload(UUID playerId, float chargePercent, float firePercent, boolean isCharging) implements CustomPayload {
        public static final Id<ChargeSyncPayload> ID = new Id<>(CHARGE_SYNC_ID);
        public static final PacketCodec<PacketByteBuf, ChargeSyncPayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> {
                            buf.writeUuid(payload.playerId);
                            buf.writeFloat(payload.chargePercent);
                            buf.writeFloat(payload.firePercent);
                            buf.writeBoolean(payload.isCharging);
                        },
                        buf -> new ChargeSyncPayload(buf.readUuid(), buf.readFloat(), buf.readFloat(), buf.readBoolean())
                );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record DapResultPayload(double x, double y, double z, UUID player1, UUID player2,
                                   int tier, boolean perfectHit) implements CustomPayload {
        public static final Id<DapResultPayload> ID = new Id<>(DAP_RESULT_ID);
        public static final PacketCodec<PacketByteBuf, DapResultPayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> {
                            buf.writeDouble(payload.x);
                            buf.writeDouble(payload.y);
                            buf.writeDouble(payload.z);
                            buf.writeUuid(payload.player1);
                            buf.writeUuid(payload.player2);
                            buf.writeInt(payload.tier);
                            buf.writeBoolean(payload.perfectHit);
                        },
                        buf -> new DapResultPayload(
                                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                                buf.readUuid(), buf.readUuid(), buf.readInt(), buf.readBoolean()
                        )
                );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

   
    private static void broadcastHeavenReadyStatus(MinecraftServer server, UUID playerId, boolean ready) {
        HeavenReadyPayload payload = new HeavenReadyPayload(playerId, ready);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(ChargeStartPayload.ID, ChargeStartPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChargeReleasePayload.ID, ChargeReleasePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HeavenReadyPayload.ID, HeavenReadyPayload.CODEC);  // Heaven ready status (S→C)
        PayloadTypeRegistry.playS2C().register(ChargeSyncPayload.ID, ChargeSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapResultPayload.ID, DapResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WhiffCooldownPayload.ID, WhiffCooldownPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ImpactFramePayload.ID, ImpactFramePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PerfectDapFreezePayload.ID, PerfectDapFreezePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PerfectDapImpactFramePayload.ID, PerfectDapImpactFramePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(FireDapJPressPayload.ID, FireDapJPressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FireDapWindowPayload.ID, FireDapWindowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FireDapFreezePayload.ID, FireDapFreezePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FireDapFirstPersonPayload.ID, FireDapFirstPersonPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(QTEButtonPressPayload.ID, QTEButtonPressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QTEWindowPayload.ID, QTEWindowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QTEClearPayload.ID, QTEClearPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ChargeStartPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                // Check config
                if (!CoopMovesConfig.get().enableDap) {
                    return;
                }
                onChargeStart(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ChargeReleasePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!CoopMovesConfig.get().enableDap) {
                    return;
                }
                onChargeRelease(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(FireDapJPressPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> onFireDapJPress(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(QTEButtonPressPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            String button = payload.button();
            context.server().execute(() -> {
                // DapComboChain gets priority (it uses the same packets)
                if (DapComboChain.onButtonPress(player, button)) {
                    return; // Consumed by combo chain
                }
                QTEManager.onButtonPress(player, button);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();

            Iterator<ScheduledParticles> particleIt = scheduledParticles.iterator();
            while (particleIt.hasNext()) {
                ScheduledParticles sp = particleIt.next();
                if (now >= sp.spawnTime()) {
                    sp.world().spawnParticles(ParticleTypes.CRIT, sp.x(), sp.y(), sp.z(), 15, 0.3, 0.3, 0.3, 0.15);
                    sp.world().spawnParticles(ParticleTypes.ENCHANT, sp.x(), sp.y(), sp.z(), 10, 0.2, 0.2, 0.2, 0.1);
                    particleIt.remove();
                }
            }


            
            Iterator<ScheduledPerfectDapEffect> effectIt = scheduledPerfectDapEffects.iterator();
            while (effectIt.hasNext()) {
                ScheduledPerfectDapEffect effect = effectIt.next();
                if (now >= effect.effectTime()) {
                    net.minecraft.entity.decoration.ArmorStandEntity stand = perfectDapArmorStands.get(effect.p1().getUuid());
                    Vec3d pos;
                    if (stand != null && !stand.isRemoved()) {
                        pos = stand.getEntityPos();  // PRECISION!
                    } else {
                        pos = effect.pos();  // Fallback
                    }
                    ServerWorld world = effect.world();

                    ServerPlayNetworking.send(effect.p1(), new PerfectDapImpactFramePayload(1));
                    ServerPlayNetworking.send(effect.p2(), new PerfectDapImpactFramePayload(1));
                    world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 5, 0.2, 0.2, 0.2, 0);
                    world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 40, 0.4, 0.4, 0.4, 0.12);
                    world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 50, 0.5, 0.5, 0.5, 0.15);
                    world.playSound(null, pos.x, pos.y, pos.z,
                            ModSounds.DAP_HIT, SoundCategory.PLAYERS, 1.5f, 1.0f);
                    world.playSound(null, pos.x, pos.y, pos.z,
                            ModSounds.IMPACT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    world.playSound(null, pos.x, pos.y, pos.z,
                            SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.2f, 1.0f);

                    createExplosion(world, pos, effect.p1(), effect.p2(), 3.5, 6.0f);
                    createShockwave(world, pos, effect.p1(), effect.p2(), 10.0, 2.0);
                    handleUnderwaterPerfectDap(world, pos, effect.p1(), effect.p2());
                    effectIt.remove();
                }
            }

            Iterator<Map.Entry<UUID, Long>> underwaterIt = underwaterRemovalStart.entrySet().iterator();
            while (underwaterIt.hasNext()) {
                Map.Entry<UUID, Long> entry = underwaterIt.next();
                UUID trackId = entry.getKey();
                long startTime = entry.getValue();
                long elapsed = now - startTime;

                if (elapsed >= 5000) {  
                    underwaterIt.remove();
                    underwaterRemovalPos.remove(trackId);
                    underwaterRemovalWorld.remove(trackId);
                    System.out.println("[Perfect Dap] 🌊 Stopped water removal after 5 seconds");
                    continue;
                }

              
                Vec3d pos = underwaterRemovalPos.get(trackId);
                ServerWorld world = underwaterRemovalWorld.get(trackId);
                if (pos == null || world == null) continue;

                BlockPos centerPos = BlockPos.ofFloored(pos);
                double radius = 3.0;
                int radiusInt = (int) Math.ceil(radius);

                for (int x = -radiusInt; x <= radiusInt; x++) {
                    for (int y = -radiusInt; y <= radiusInt; y++) {
                        for (int z = -radiusInt; z <= radiusInt; z++) {
                            double distance = Math.sqrt(x*x + y*y + z*z);
                            if (distance <= radius) {
                                BlockPos blockPos = centerPos.add(x, y, z);
                                if (world.getBlockState(blockPos).isOf(net.minecraft.block.Blocks.WATER)) {
                                    world.setBlockState(blockPos, net.minecraft.block.Blocks.AIR.getDefaultState());
                                }
                            }
                        }
                    }
                }
            }

            Iterator<HeavenParticleSpawner> heavenParticleIt = activeHeavenParticles.iterator();
            while (heavenParticleIt.hasNext()) {
                HeavenParticleSpawner spawner = heavenParticleIt.next();

                if (now >= spawner.endTime) {
                    // 30 seconds over - stop spawning
                    heavenParticleIt.remove();
                    System.out.println("[Heaven Dap] 30-second particle spawner finished!");
                    continue;
                }

                ServerWorld world = spawner.world;
                Vec3d pos = spawner.pos;

                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 2, 3, 3, 3, 0);
                world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 10, 5, 5, 5, 0.3);
                world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 5, 4, 4, 4, 0.2);
                world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            }
            Iterator<Map.Entry<UUID, Long>> perfectDapIt = perfectDapStartTime.entrySet().iterator();
            while (perfectDapIt.hasNext()) {
                Map.Entry<UUID, Long> entry = perfectDapIt.next();
                UUID playerId = entry.getKey();
                long startTime = entry.getValue();
                long elapsed = now - startTime;

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player == null) {
                    perfectDapIt.remove();
                    perfectDapPartner.remove(playerId);
                    perfectDapFreezeEnd.remove(playerId);
                    perfectDapImpactSent.remove(playerId);
                    net.minecraft.entity.decoration.ArmorStandEntity stand = perfectDapArmorStands.remove(playerId);
                    if (stand != null && !stand.isRemoved()) {
                        stand.discard();
                    }
                    continue;
                }

                UUID partnerId = perfectDapPartner.get(playerId);
                ServerPlayerEntity partner = partnerId != null ? server.getPlayerManager().getPlayer(partnerId) : null;

                if (partner != null) {
                    net.minecraft.entity.decoration.ArmorStandEntity stand = perfectDapArmorStands.get(playerId);
                    if (stand != null && !stand.isRemoved()) {
                        Vec3d p1Hand = player.getEntityPos().add(0, 1.4, 0);
                        Vec3d p2Hand = partner.getEntityPos().add(0, 1.4, 0);
                        Vec3d handMid = p1Hand.add(p2Hand).multiply(0.5);
                        stand.setPosition(handMid.x, handMid.y, handMid.z);
                        stand.setFireTicks(0);  // Keep it NOT on fire!
                    }

              
                    smoothDapDescent(player, stand);
                }

                if (elapsed >= 150 && elapsed <= 1330 && partner != null) {
                    ServerWorld world = player.getEntityWorld();
                    Vec3d particlePos;
                    net.minecraft.entity.decoration.ArmorStandEntity stand = perfectDapArmorStands.get(playerId);
                    if (stand != null && !stand.isRemoved()) {
                        particlePos = stand.getEntityPos();
                    } else {
                        particlePos = player.getEntityPos().add(partner.getEntityPos()).multiply(0.5).add(0, 1.4, 0);
                    }

                }

                if (elapsed >= 290 && elapsed < 310 && partner != null) {  // 20ms window to trigger once
                    if (!perfectDapImpactSent.getOrDefault(playerId, false)) {
                        ServerPlayNetworking.send(player, new PerfectDapImpactFramePayload(1));
                        perfectDapImpactSent.put(playerId, true);
                        System.out.println("[Perfect Dap] 🎬 Impact frames sent at 0.29s!");
                    }
                }

                if (elapsed >= 1330 && perfectDapFreezeEnd.containsKey(playerId)) {
                    perfectDapFreezeEnd.remove(playerId);

                    ServerPlayNetworking.send(player, new PerfectDapFreezePayload(false));

                    if (partnerId != null) {
                        perfectDapFreezeEnd.remove(partnerId);

                        // Notify partner of unfreeze
                        ServerPlayerEntity partner2 = server.getPlayerManager().getPlayer(partnerId);
                        if (partner2 != null) {
                            ServerPlayNetworking.send(partner2, new PerfectDapFreezePayload(false));
                        }
                    }
                }

                if (elapsed >= 1625) {
                    perfectDapIt.remove();
                    perfectDapPartner.remove(playerId);
                    perfectDapFreezeEnd.remove(playerId);
                    perfectDapImpactSent.remove(playerId);

                    net.minecraft.entity.decoration.ArmorStandEntity stand = perfectDapArmorStands.remove(playerId);
                    if (stand != null && !stand.isRemoved()) {
                        stand.discard();
                        System.out.println("[Perfect Dap] Armor stand cleaned up");
                    }
                }
            }

            Iterator<Map.Entry<UUID, HeavenDapData>> heavenIt = heavenPlayers.entrySet().iterator();
            while (heavenIt.hasNext()) {
                Map.Entry<UUID, HeavenDapData> entry = heavenIt.next();
                UUID playerId = entry.getKey();
                HeavenDapData data = entry.getValue();
                long elapsed = now - data.startTime;

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player == null) {
                    heavenIt.remove();
                    continue;
                }

        
                if (elapsed >= 3000 && elapsed <= 9000) {
                    Vec3d pos = player.getEntityPos();

                    data.world.spawnParticles(ParticleTypes.WHITE_ASH,
                            pos.x, pos.y + 1, pos.z,
                            5, 1.0, 1.0, 1.0, 0.02);

                    data.world.spawnParticles(ParticleTypes.CLOUD,
                            pos.x, pos.y, pos.z,
                            3, 0.5, 0.5, 0.5, 0.01);
                }

                if (elapsed >= 9500 && elapsed < 9600) {  // Only trigger once
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 40, 0, false, false));
                }

              
                if (elapsed >= 11500) {
                    Vec3d returnPos = data.originalMidpoint;
                    UUID partnerId = data.partnerId;
                    ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);

                    if (partner != null) {
                        double dx = partner.getX() - player.getX();
                        double dz = partner.getZ() - player.getZ();
                        float yawTowardsPartner = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
                        float yawAwayFromPartner = yawTowardsPartner + 180;  // Turn around!

                        player.teleport(data.world, returnPos.x, returnPos.y, returnPos.z, yawAwayFromPartner, 0);

                        player.stopFallFlying();
                        player.setVelocity(Vec3d.ZERO);
                        player.velocityDirty = true;

                        player.removeStatusEffect(StatusEffects.NAUSEA);

                        data.world.spawnParticles(ParticleTypes.FLASH,
                                returnPos.x, returnPos.y + 1, returnPos.z,
                                1, 0, 0, 0, 0);
                        data.world.spawnParticles(ParticleTypes.WHITE_ASH,
                                returnPos.x, returnPos.y + 1, returnPos.z,
                                50, 0.5, 1.0, 0.5, 0.1);

                        final ServerPlayerEntity finalPlayer = player;
                        new Thread(() -> {
                            try {
                                Thread.sleep(3000);  // 3 second delay
                                finalPlayer.getEntityWorld().getServer().execute(() -> {
                                    ServerPlayNetworking.send(finalPlayer, new HeavenDapPayloads.RestoreVolumePayload());
                                    System.out.println("[Heaven Dap] Volume restored for " + finalPlayer.getName().getString());
                                });
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }).start();

                        System.out.println("[Heaven Dap] Player " + player.getName().getString() + " returned (backs turned)");

                   
                        if (partner != null && heavenPlayers.containsKey(partnerId)) {
                            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                                p.sendMessage(net.minecraft.text.Text.literal(
                                        "§d§l✨ " + player.getName().getString() + " §7and §d§l" +
                                                partner.getName().getString() + " §7have achieved §d§lPERFECT FRIENDSHIP! ✨"
                                ), false);
                            }

                            server.getOverworld().playSound(null, returnPos.x, returnPos.y, returnPos.z,
                                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 3.0f, 1.0f);
                            server.getOverworld().playSound(null, returnPos.x, returnPos.y, returnPos.z,
                                    SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 2.0f, 1.5f);
                        }
                    }

                 

                    heavenIt.remove();
                }
            }

           
            Iterator<Map.Entry<UUID, Long>> fireDapIt = fireDapStartTime.entrySet().iterator();
            while (fireDapIt.hasNext()) {
                Map.Entry<UUID, Long> entry = fireDapIt.next();
                UUID playerId = entry.getKey();
                long startTime = entry.getValue();
                long elapsed = now - startTime;

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player == null) {
                    fireDapIt.remove();
                    inFireDapHit.remove(playerId);
                    fireDapPartner.remove(playerId);
                    continue;
                }

                if (elapsed >= FIRE_IMPACT_TIME && !fireCircleSpawned.getOrDefault(playerId, true) && inFireDapHit.getOrDefault(playerId, false)) {
                    UUID partnerId = fireDapPartner.get(playerId);
                    if (partnerId != null && fireDapStartTime.containsKey(partnerId)) {
                        ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);
                        if (partner != null && !fireCircleSpawned.getOrDefault(partnerId, true)) {
                            spawnFireCircle(player, partner);

                            long freezeEnd = now + (FIRE_DAP_HIT_LENGTH - elapsed);
                            fireDapComboFreezeEnd.put(playerId, freezeEnd);
                            fireDapComboFreezeEnd.put(partnerId, freezeEnd);

                            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                                ServerPlayNetworking.send(p, new FireDapFreezePayload(playerId, true));
                                ServerPlayNetworking.send(p, new FireDapFreezePayload(partnerId, true));
                            }


                            fireCircleSpawned.put(playerId, true);
                            fireCircleSpawned.put(partnerId, true);
                        }
                    }
                }

                if (elapsed >= FIRE_IMPACT_TIME && inFireDapHit.getOrDefault(playerId, false)) {
                    UUID partnerId = fireDapPartner.get(playerId);
                    if (partnerId != null) {
                        ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);
                        if (partner != null) {
                            net.minecraft.entity.decoration.ArmorStandEntity stand = fireDapArmorStands.get(playerId);
                            if (stand != null && !stand.isRemoved()) {
                                Vec3d p1Hand = player.getEntityPos().add(0, 1.4, 0);
                                Vec3d p2Hand = partner.getEntityPos().add(0, 1.4, 0);
                                Vec3d handMid = p1Hand.add(p2Hand).multiply(0.5);
                                stand.setPosition(handMid.x, handMid.y, handMid.z);
                                stand.setFireTicks(0);  // Keep it NOT on fire!

                                smoothDapDescent(player, stand);
                                smoothDapDescent(partner, stand);
                            }
                        }
                    }
                }

                if (elapsed >= FIRE_DAP_HIT_LENGTH && inFireDapHit.getOrDefault(playerId, false)) {
                    Long jpressTime = fireDapComboRequestTime.get(playerId);
                    if (jpressTime != null && (now - jpressTime) < 2000) {

                        if (now - jpressTime >= 1000) {
                            UUID partnerId = fireDapPartner.get(playerId);
                            if (partnerId != null) {
                                ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);
                                if (partner != null) {
                                    partner.sendMessage(net.minecraft.text.Text.literal("§c✗ You missed the combo! " + player.getName().getString() + " pressed J!"), true);
                                    player.sendMessage(net.minecraft.text.Text.literal("§c✗ " + partner.getName().getString() + " missed the combo!"), true);
                                }
                            }
                        } else {
                            continue;  
                        }
                    }

                    fireDapIt.remove();
                    inFireDapHit.remove(playerId);
                    fireDapComboRequestTime.remove(playerId);
                    DapSessionManager.removeSessionForPlayer(playerId);
                    if (fireDapComboFreezeEnd.containsKey(playerId)) {
                        fireDapComboFreezeEnd.remove(playerId);
                        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                            ServerPlayNetworking.send(p, new FireDapFreezePayload(playerId, false));
                        }
                        PoseNetworking.broadcastAnimState(player, 0);
                    }
                }
            }

            for (Map.Entry<UUID, Long> entry : new HashMap<>(fireDapComboFreezeEnd).entrySet()) {
                UUID playerId = entry.getKey();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null && !inFireDapHit.getOrDefault(playerId, false)) {
                    UUID partnerId = fireDapPartner.get(playerId);
                    if (partnerId != null) {
                        ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);
                        if (partner != null) {
                            teleportFireDapFacingEachOther(player, partner, 1.2);
                        }
                    }
                }
            }

            Iterator<Map.Entry<UUID, FireDapScheduledEvent>> armIt = pendingFireArmImpacts.entrySet().iterator();
            while (armIt.hasNext()) {
                Map.Entry<UUID, FireDapScheduledEvent> entry = armIt.next();
                FireDapScheduledEvent event = entry.getValue();
                if (now >= event.executeTime) {
                    executeFireArmImpact(event.p1, event.p2);
                    armIt.remove();
                }
            }

            Iterator<Map.Entry<UUID, FireDapScheduledEvent>> tornadoIt = pendingFireTornadoSpawns.entrySet().iterator();
            while (tornadoIt.hasNext()) {
                Map.Entry<UUID, FireDapScheduledEvent> entry = tornadoIt.next();
                FireDapScheduledEvent event = entry.getValue();
                if (now >= event.executeTime) {
                    spawnFireTornado(event.p1, event.p2);
                    tornadoIt.remove();
                }
            }

            if (auraBeamsActive && server != null) {
                long elapsed = now - auraBeamStartTime;

                if (elapsed < 4000) {
                    ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(auraBeamPlayer1);
                    ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(auraBeamPlayer2);

                    if (p1 != null && p2 != null) {
                        spawnAnimatedAuraBeam(p1, elapsed);
                        spawnAnimatedAuraBeam(p2, elapsed);
                    }
                } else {
                    auraBeamsActive = false;
                }
            }

            Iterator<Map.Entry<UUID, Vec3d>> tpIt = smoothTPTarget.entrySet().iterator();
            while (tpIt.hasNext()) {
                Map.Entry<UUID, Vec3d> entry = tpIt.next();
                UUID playerId = entry.getKey();
                Vec3d target = entry.getValue();

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player == null) {
                    tpIt.remove();
                    smoothTPProgress.remove(playerId);
                    continue;
                }

                int progress = smoothTPProgress.getOrDefault(playerId, 0);

                if (progress < 10) {
                    Vec3d current = player.getEntityPos();
                    double t = (progress + 1) / 10.0;  // Linear interpolation

                    Vec3d newPos = new Vec3d(
                            current.x + (target.x - current.x) * t,
                            current.y + (target.y - current.y) * t,
                            current.z + (target.z - current.z) * t
                    );

                    UUID partnerId = fireDapPartner.get(playerId);
                    if (partnerId != null) {
                        ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);
                        if (partner != null) {
                            double dx = partner.getEntityPos().x - newPos.x;
                            double dz = partner.getEntityPos().z - newPos.z;
                            float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;

                            player.teleport(player.getEntityWorld(), newPos.x, newPos.y, newPos.z, yaw, player.getPitch());
                        }
                    }

                    smoothTPProgress.put(playerId, progress + 1);
                } else {
                    tpIt.remove();
                    smoothTPProgress.remove(playerId);
                }
            }

            if (tornadoActive && tornadoCenter != null && tornadoWorld != null) {
                long elapsed = now - tornadoStartTime;

                if (elapsed < 5000) {
                    if (elapsed % 40 == 0) {  // Every 2 ticks
                        for (int i = 0; i < 12; i++) {
                            double startAngle = i * 30;  // Evenly spaced around circle
                            double startRadius = 3.0 + (Math.random() * 2);  // Random radius 3-5 blocks

                            TornadoSwirlEntity swirl = new TornadoSwirlEntity(tornadoWorld, tornadoCenter, startAngle, startRadius);
                            activeTornadoSwirls.add(swirl);
                        }
                    }
                } else {
                    tornadoActive = false;
                    activeTornadoSwirls.clear();
                }

                Iterator<TornadoSwirlEntity> swirlIt = activeTornadoSwirls.iterator();
                while (swirlIt.hasNext()) {
                    TornadoSwirlEntity swirl = swirlIt.next();
                    boolean stillAlive = swirl.tick();  // Move and spawn particles!
                    if (!stillAlive) {
                        swirlIt.remove();  
                    }
                }

                if (tornadoCenter != null && tornadoWorld != null) {
                    double tornadoRadius = 30;  // 30 block radius shield

                    UUID shieldPlayer1 = auraBeamPlayer1;
                    UUID shieldPlayer2 = auraBeamPlayer2;

                    // Check all entities in range
                    Box searchBox = new Box(
                            tornadoCenter.x - tornadoRadius, tornadoCenter.y, tornadoCenter.z - tornadoRadius,
                            tornadoCenter.x + tornadoRadius, tornadoCenter.y + 70, tornadoCenter.z + tornadoRadius
                    );

                    for (Entity entity : tornadoWorld.getOtherEntities(null, searchBox)) {
                        if (entity.getUuid().equals(shieldPlayer1) || entity.getUuid().equals(shieldPlayer2)) {
                            continue;
                        }

                        Vec3d entityPos = entity.getEntityPos();
                        double dx = entityPos.x - tornadoCenter.x;
                        double dz = entityPos.z - tornadoCenter.z;
                        double distanceToCenter = Math.sqrt(dx * dx + dz * dz);

                        if (distanceToCenter >= tornadoRadius - 2 && distanceToCenter <= tornadoRadius + 2) {
                            Vec3d direction = new Vec3d(dx, 0, dz).normalize();

                            entity.setVelocity(
                                    direction.x * 2.0,
                                    0.5,
                                    direction.z * 2.0
                            );
                            entity.velocityDirty = true;

                            if (entity instanceof net.minecraft.entity.LivingEntity living) {
                                living.damage(living.getDamageSources().magic(), 5.0f);
                            }
                        }

                        if (entity instanceof net.minecraft.entity.projectile.ProjectileEntity) {
                            if (distanceToCenter >= tornadoRadius - 3) {
                                entity.discard();
                            }
                        }
                    }
                }
            }

            Iterator<Map.Entry<UUID, Long>> freezeIt = fireDapComboFreezeEnd.entrySet().iterator();
            while (freezeIt.hasNext()) {
                Map.Entry<UUID, Long> entry = freezeIt.next();
                if (now >= entry.getValue()) {
                    UUID playerId = entry.getKey();

                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        ServerPlayNetworking.send(player, new FireDapFreezePayload(playerId, false));
                    }

                    freezeIt.remove();
                    fireDapPartner.remove(playerId);

                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                    if (player != null) {
                        PoseNetworking.broadcastAnimState(player, 0);
                    }

                }
            }

            Iterator<SaturnRing> ringIt = activeSaturnRings.iterator();
            while (ringIt.hasNext()) {
                SaturnRing ring = ringIt.next();
                if (now >= ring.endTime) {
                    ringIt.remove();  // Ring finished
                    continue;
                }

                long elapsed = now - ring.startTime;
                double rotationAngle = (elapsed / 100.0) * 360.0; // Complete rotation every 10 seconds
                double radius = 50.0;

                ServerWorld world = server.getOverworld();

                for (double angle = 0; angle < 360; angle += 5) {
                    double rad = Math.toRadians(angle + rotationAngle);
                    double x = ring.center.x + Math.cos(rad) * radius;
                    double z = ring.center.z + Math.sin(rad) * radius;
                    double y = ring.center.y;

                    world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.1, 0.1, 0.1, 0.01);
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.5, z, 1, 0.05, 0.05, 0.05, 0);
                }
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();

                if (impactFreezeTicks.containsKey(id)) {
                    int remaining = impactFreezeTicks.get(id);
                    if (remaining > 0) {
                        player.setVelocity(0, Math.min(0, player.getVelocity().y), 0); // Allow falling
                        player.velocityDirty = true;
                        impactFreezeTicks.put(id, remaining - 1);
                    } else {
                        impactFreezeTicks.remove(id);
                    }
                }

                Vec3d velocity = getEffectiveVelocity(player);
                double speed = velocity.length() * 20.0;

                // Update speed history
                LinkedList<Double> history = speedHistory.computeIfAbsent(id, k -> new LinkedList<>());
                history.addLast(speed);
                while (history.size() > SPEED_HISTORY_TICKS) {
                    history.removeFirst();
                }

                // Update fire level - starts after 2 sec of full charge, resets if stop moving (with grace period)
                if (chargeStartTime.containsKey(id)) {
                    float charge = getChargePercent(id);

                    // Calculate horizontal speed (ignore Y) - use effective velocity for vehicles
                    double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;

                    if (charge >= 0.99f) {
                        // Charge is full - check if moving
                        boolean isMoving = horizontalSpeed >= MIN_MOVEMENT_SPEED;

                        if (isMoving) {
                            // Moving! Clear grace timer
                            fireGraceTime.remove(id);

                            // Track time at full charge
                            if (!fireStartTime.containsKey(id)) {
                                fireStartTime.put(id, now);
                            }

                            long timeAtFullCharge = now - fireStartTime.get(id);

                            if (timeAtFullCharge >= FIRE_DELAY_MS) {
                                // Past the 2 second delay - now building fire!
                                long fireBuildTime = timeAtFullCharge - FIRE_DELAY_MS;
                                float fire = Math.min(1.0f, (float) fireBuildTime / FIRE_BUILD_TIME_MS);
                                fireLevel.put(id, fire);

                                // Spawn fire particles on player's hand
                                spawnFireHandParticles(player, fire);

                                // ========== HEAVEN DAP - CHECK IF FIRE MAXED FOR 5+ SECONDS ==========
                                if (fire >= 0.99f) {
                                    // Fire is maxed! Track how long it's been maxed
                                    if (!fireMaxedStartTime.containsKey(id)) {
                                        fireMaxedStartTime.put(id, now);
                                        System.out.println("[Heaven Dap] " + player.getName().getString() + " fire reached 100%!");
                                    }

                                    long timeAtMaxFire = now - fireMaxedStartTime.get(id);
                                    if (timeAtMaxFire >= HEAVEN_READY_TIME_MS && !heavenReady.contains(id)) {
                                        // HEAVEN READY! Fire been maxed for 5+ seconds!
                                        heavenReady.add(id);

                                        // Play glass breaking sound
                                        player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                                                net.minecraft.sound.SoundEvents.BLOCK_GLASS_BREAK, net.minecraft.sound.SoundCategory.PLAYERS,
                                                1.0f, 0.8f);

                                        player.sendMessage(net.minecraft.text.Text.literal("§d§l✨ HEAVEN READY! ✨ §7(Fire UI broken!)"), true);
                                        System.out.println("[Heaven Dap] " + player.getName().getString() + " is HEAVEN READY! (fire maxed for 5s - UI broken!)");

                                        // Broadcast to all clients for visual effects
                                        HeavenReadyPayload payload = new HeavenReadyPayload(id, true);
                                        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                                            ServerPlayNetworking.send(p, payload);
                                        }
                                    }
                                } else {
                                    fireMaxedStartTime.remove(id);
                                }
                            } else {
                                fireLevel.put(id, 0f);
                            }
                        } else {
                            if (!fireGraceTime.containsKey(id)) {
                                fireGraceTime.put(id, now);
                            }

                            long graceDuration = now - fireGraceTime.get(id);
                            if (graceDuration > FIRE_GRACE_PERIOD_MS) {
                                // Grace period expired - reset fire!
                                fireStartTime.remove(id);
                                fireLevel.put(id, 0f);
                                fireMaxedStartTime.remove(id);

                                if (heavenReady.remove(id)) {
                                    broadcastHeavenReadyStatus(server, id, false);
                                    System.out.println("[Heaven Dap] " + player.getName().getString() + " lost heaven ready (grace period expired)");
                                }
                            }
                           
                        }
                    } else {
                        fireStartTime.remove(id);
                        fireGraceTime.remove(id);
                        fireLevel.put(id, 0f);
                        fireMaxedStartTime.remove(id);

                        if (heavenReady.remove(id)) {
                            broadcastHeavenReadyStatus(server, id, false);
                            System.out.println("[Heaven Dap] " + player.getName().getString() + " lost heaven ready (charge dropped)");
                        }
                    }
                } else {
                    fireStartTime.remove(id);
                    fireGraceTime.remove(id);
                    fireLevel.remove(id);
                    fireMaxedStartTime.remove(id);

                    // Clear heaven ready and broadcast to clients
                    if (heavenReady.remove(id)) {
                        broadcastHeavenReadyStatus(server, id, false);
                        System.out.println("[Heaven Dap] " + player.getName().getString() + " lost heaven ready (not charging)");
                    }
                }
            }

            Iterator<Map.Entry<UUID, UUID>> it = waitingForPartner.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, UUID> entry = it.next();
                UUID waiterId = entry.getKey();
                Long releaseT = releaseTime.get(waiterId);

                if (releaseT != null && now - releaseT > RELEASE_WINDOW_MS) {
                    ServerPlayerEntity waiter = server.getPlayerManager().getPlayer(waiterId);
                    ServerPlayerEntity partner = server.getPlayerManager().getPlayer(entry.getValue());

                    if (waiter != null && partner != null) {
                        executeFizzle(waiter, partner);
                    }

                    it.remove();
                    releaseTime.remove(waiterId);
                    chargeStartTime.remove(waiterId);
                    chargeStartTime.remove(entry.getValue());
                    broadcastChargeCancel(waiter);
                    if (partner != null) broadcastChargeCancel(partner);
                }
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!chargeStartTime.containsKey(player.getUuid())) continue;

                float charge = getChargePercent(player.getUuid());
                float fire = fireLevel.getOrDefault(player.getUuid(), 0f);
                ChargeSyncPayload syncPayload = new ChargeSyncPayload(player.getUuid(), charge, fire, true);

                for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
                    ServerPlayNetworking.send(other, syncPayload);
                }
                ServerPlayNetworking.send(player, syncPayload);
            }
        });
    }

    private static void spawnFireHandParticles(ServerPlayerEntity player, float fireLevel) {
        if (Math.random() > 0.33) return;

        ServerWorld world = player.getEntityWorld();
        Vec3d pos = player.getEntityPos();

        float yaw = player.getYaw();
        double yawRad = Math.toRadians(yaw);

        double rightX = -Math.cos(yawRad) * 0.4;
        double rightZ = -Math.sin(yawRad) * 0.4;

        // Hand position
        double handX = pos.x + rightX;
        double handY = pos.y + 1.3; // Shoulder height
        double handZ = pos.z + rightZ;

        world.spawnParticles(ParticleTypes.FLAME, handX, handY, handZ, 1, 0.06, 0.06, 0.06, 0.005);

        if (fireLevel > 0.6f) {
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, handX, handY, handZ, 1, 0.05, 0.05, 0.05, 0.003);
        }
    }

    private static void onChargeStart(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        if (HighFiveHandler.hasHandRaised(uuid)) {
            System.out.println("[ChargedDap] Blocked G - H is active!");
            broadcastChargeCancel(player);
            return;
        }
        if (HighFiveHandler.isInBlockingAnimation(uuid)) {
            System.out.println("[ChargedDap] Blocked G - H animation playing!");
            broadcastChargeCancel(player);
            return;
        }

        if (isInComboCooldown(uuid)) {
            System.out.println("[ChargedDap] Blocked G - combo cooldown active!");
            player.sendMessage(net.minecraft.text.Text.literal("§cWait 1 second after combo!"), true);
            broadcastChargeCancel(player);
            return;
        }
        if (FallCatchHandler.isInCatchReadyMode(uuid)) return;
        if (isOnCooldown(uuid)) return;
        if (!player.getMainHandStack().isEmpty()) return;
        chargeStartTime.put(uuid, System.currentTimeMillis());
        fireLevel.put(uuid, 0f);
        ChargeSyncPayload payload = new ChargeSyncPayload(uuid, 0f, 0f, true);
        ServerPlayNetworking.send(player, payload); // Send to self
        for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
            ServerPlayNetworking.send(other, payload);
        }

        player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.5f, 0.8f);
    }

    private static void onChargeRelease(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        if (!chargeStartTime.containsKey(uuid)) return;

        long now = System.currentTimeMillis();
        float myCharge = getChargePercent(uuid);
        float myFire = fireLevel.getOrDefault(uuid, 0f);

        ServerPlayerEntity partner = findAnyDapPartner(player);

        if (partner == null) {
            executeWhiff(player);
            chargeStartTime.remove(uuid);
            fireLevel.remove(uuid);
            fireStartTime.remove(uuid);
            if (heavenReady.remove(uuid)) {
                broadcastHeavenReadyStatus(player.getEntityWorld().getServer(), uuid, false);
                System.out.println("[Heaven Dap] " + player.getName().getString() + " lost heaven ready (whiff)");
            }

            broadcastChargeCancel(player);

            long cooldownEnd = now + WHIFF_COOLDOWN_MS;
            cooldowns.put(uuid, cooldownEnd);
            broadcastWhiffCooldown(player, cooldownEnd);

            player.sendMessage(net.minecraft.text.Text.literal("§c✗ Whiff! 0.8s cooldown"), true);
            return;
        }

        UUID partnerId = partner.getUuid();

        if (HighFiveHandler.hasHandRaised(partnerId) && !chargeStartTime.containsKey(partnerId)) {
            if (DapHoldHandler.tryDetect(player, partner)) {
                chargeStartTime.remove(uuid);
                fireLevel.remove(uuid);
                fireStartTime.remove(uuid);

                if (heavenReady.remove(uuid)) {
                    broadcastHeavenReadyStatus(player.getEntityWorld().getServer(), uuid, false);
                    System.out.println("[Heaven Dap] " + player.getName().getString() + " lost heaven ready (DapHold took over)");
                }

                broadcastChargeCancel(player);
                return;
            }

            // DapHold didn't trigger - do normal Dap + High Five combo
            // Partner just receives it - tier based entirely on dapper's charge + speed
            highFivePartners.add(partnerId);

            // Pass dapper's charge for BOTH so avgCharge = dapper's charge (no averaging down)
            executeDap(player, partner, myCharge, myCharge, myFire, myFire, 1.0f, now, now);
            highFivePartners.remove(partnerId);

            HighFiveHandler.handRaisedTime.remove(partnerId);
            HighFiveHandler.startAnimTime.remove(partnerId);
            HighFiveHandler.highFiveCooldown.put(partnerId, now);
            HighFiveHandler.syncHandRaised(partner, false);

            chargeStartTime.remove(uuid);
            fireLevel.remove(uuid);
            fireStartTime.remove(uuid);

            // Clear heaven-ready status and broadcast
            if (heavenReady.remove(uuid)) {
                broadcastHeavenReadyStatus(player.getEntityWorld().getServer(), uuid, false);
                System.out.println("[Heaven Dap] " + player.getName().getString() + " lost heaven ready (dap+highfive completed)");
            }

            broadcastChargeCancel(player);
            return;
        }

        // Check if partner already released
        if (waitingForPartner.containsKey(partnerId) && waitingForPartner.get(partnerId).equals(uuid)) {
            long partnerReleaseTime = releaseTime.get(partnerId);
            float partnerCharge = getChargePercent(partnerId);
            float partnerFire = fireLevel.getOrDefault(partnerId, 0f);

            executeDap(player, partner, myCharge, partnerCharge, myFire, partnerFire, 1.0f, partnerReleaseTime, now);

            waitingForPartner.remove(partnerId);
            releaseTime.remove(partnerId);
            chargeStartTime.remove(uuid);
            chargeStartTime.remove(partnerId);
            fireLevel.remove(uuid);
            fireLevel.remove(partnerId);
            fireStartTime.remove(uuid);
            fireStartTime.remove(partnerId);

            // Clear heaven-ready status for both and broadcast
            if (heavenReady.remove(uuid)) {
                broadcastHeavenReadyStatus(player.getEntityWorld().getServer(), uuid, false);
                System.out.println("[Heaven Dap] " + player.getName().getString() + " lost heaven ready (dap completed)");
            }
            if (heavenReady.remove(partnerId)) {
                broadcastHeavenReadyStatus(partner.getEntityWorld().getServer(), partnerId, false);
                System.out.println("[Heaven Dap] " + partner.getName().getString() + " lost heaven ready (dap completed)");
            }

            broadcastChargeCancel(player);
            broadcastChargeCancel(partner);

        } else {
            releaseTime.put(uuid, now);
            waitingForPartner.put(uuid, partnerId);

            player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.7f, 1.2f);
        }
    }

    private static ServerPlayerEntity findAnyDapPartner(ServerPlayerEntity player) {
        Box searchBox = player.getBoundingBox().expand(DAP_RANGE);

        for (ServerPlayerEntity other : player.getEntityWorld().getPlayers()) {
            if (other == player) continue;
            if (isOnCooldown(other.getUuid())) continue;

            boolean isReady = chargeStartTime.containsKey(other.getUuid()) ||
                    HighFiveHandler.hasHandRaised(other.getUuid());

            if (!isReady) continue;

            if (searchBox.intersects(other.getBoundingBox())) {
                return other;
            }
        }
        return null;
    }

    private static void executeDap(ServerPlayerEntity p1, ServerPlayerEntity p2,
                                   float charge1, float charge2, float fire1, float fire2,
                                   float syncQuality, long releaseTime1, long releaseTime2) {
        if (DapHoldHandler.isInDapHold(p1.getUuid()) || DapHoldHandler.isInDapHold(p2.getUuid())) {
            System.out.println("[Dap] Blocked - player(s) in DapHold interaction");
            return;
        }

        long now = System.currentTimeMillis();
        cooldowns.put(p1.getUuid(), now + COOLDOWN_MS);
        cooldowns.put(p2.getUuid(), now + COOLDOWN_MS);

        float avgCharge = (charge1 + charge2) / 2.0f;
        float avgFire = (fire1 + fire2) / 2.0f;

        double speed1 = getMaxRecentSpeed(p1.getUuid());
        double speed2 = getMaxRecentSpeed(p2.getUuid());
        double combinedSpeed = speed1 + speed2;

        // Calculate timing difference
        long timeDiff = Math.abs(releaseTime1 - releaseTime2);
        boolean perfectHit = timeDiff <= PERFECT_WINDOW_MS;
        boolean bothCharging = chargeStartTime.containsKey(p1.getUuid()) && chargeStartTime.containsKey(p2.getUuid());


        // Calculate tier
        int tier = calculateTier(avgCharge, combinedSpeed, avgFire, fire1, fire2);

        // Perfect window only applies to tier 3+ and only if both were charging
        if (tier >= 3 && bothCharging && !perfectHit) {
            // Missed perfect window - check if still in normal window
            if (timeDiff > RELEASE_WINDOW_MS) {
                // Too slow - fizzle already handled
                return;
            }
        }

        // PERFECT DAPS (tier 3+, perfect timing) ignore eye contact requirement
        // This includes: Perfect tier 3, Perfect tier 4, Perfect FIRE DAP (tier 5)
        boolean isPerfectDap = (tier >= 3 && bothCharging && perfectHit);

        //  EYE CONTACT CHECK 
        // Low-tier daps (tier 0-2 and non-perfect tier 3) require facing each other
        // Perfect daps (tier 3+ with perfect timing) skip this check DONST WORK
        if (!isPerfectDap) {
            if (!arePlayersFacingEachOther(p1, p2)) {
                // Not facing → show message and cancel
                p1.sendMessage(net.minecraft.text.Text.literal("§c§lKeep eye contact!"), true);
                p2.sendMessage(net.minecraft.text.Text.literal("§c§lKeep eye contact!"), true);

                // Refund cooldown (shorter penalty for eye contact fail)
                cooldowns.put(p1.getUuid(), now + 300);
                cooldowns.put(p2.getUuid(), now + 300);
                return;
            }
        }

        Vec3d pos1 = p1.getEntityPos();
        Vec3d pos2 = p2.getEntityPos();
        Vec3d dapPos = pos1.add(pos2).multiply(0.5).add(0, 0.5, 0);

        ServerWorld world = p1.getEntityWorld();

        switch (tier) {
            case 0 -> executeTier0(world, dapPos, p1, p2);
            case 1 -> executeTier1(world, dapPos, p1, p2);
            case 2 -> executeTier2(world, dapPos, p1, p2);
            case 3 -> executeTier3Great(world, dapPos, p1, p2, perfectHit, bothCharging);
            case 4 -> executeTier4Legendary(world, dapPos, p1, p2, perfectHit, bothCharging, speed1, speed2);
            case 5 -> executeTier5FireDap(world, dapPos, p1, p2, perfectHit);
        }

        p1.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        p2.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);

        MahitoTrollHandler.checkForMahitoTroll(p1, p2);

        speedHistory.remove(p1.getUuid());
        speedHistory.remove(p2.getUuid());
        chargeStartTime.remove(p1.getUuid());
        chargeStartTime.remove(p2.getUuid());

        broadcastChargeCancel(p1);
        broadcastChargeCancel(p2);

        if (highFivePartners.contains(p1.getUuid())) {
            PoseNetworking.broadcastAnimState(p1,
                    com.cooptest.client.CoopAnimationHandler.AnimState.HIGHFIVE_HIT.ordinal());
            PoseNetworking.broadcastAnimState(p2,
                    com.cooptest.client.CoopAnimationHandler.AnimState.DAP_HIT.ordinal());
        } else if (highFivePartners.contains(p2.getUuid())) {
            PoseNetworking.broadcastAnimState(p2,
                    com.cooptest.client.CoopAnimationHandler.AnimState.HIGHFIVE_HIT.ordinal());
            PoseNetworking.broadcastAnimState(p1,
                    com.cooptest.client.CoopAnimationHandler.AnimState.DAP_HIT.ordinal());
        }
        
        long particleSpawnTime = System.currentTimeMillis() + 800;
        scheduledParticles.add(new ScheduledParticles(world, dapPos.x, dapPos.y, dapPos.z, particleSpawnTime));

        // Send result
        DapResultPayload result = new DapResultPayload(
                dapPos.x, dapPos.y, dapPos.z,
                p1.getUuid(), p2.getUuid(), tier, perfectHit
        );
        for (ServerPlayerEntity other : PlayerLookup.all(p1.getEntityWorld().getServer())) {
            ServerPlayNetworking.send(other, result);
        }
    }

    private static int calculateTier(float avgCharge, double combinedSpeed, float avgFire, float fire1, float fire2) {

        if (fire1 >= 0.90f && fire2 >= 0.90f) {
            return 5;
        } else {
        }

        if (avgCharge >= 0.8f && combinedSpeed >= SPEED_TIER_4_THRESHOLD) {
            return 4;
        }

        float chargeScore = avgCharge * 100;
        float speedBonus = 0;
        if (combinedSpeed >= SPEED_BONUS_THRESHOLD) {
            speedBonus = (float)((combinedSpeed - SPEED_BONUS_THRESHOLD) / (SPEED_TIER_4_THRESHOLD - SPEED_BONUS_THRESHOLD) * 30);
        }
        float finalScore = chargeScore + speedBonus;

        if (finalScore >= 100) return 3;
        if (finalScore >= 70) return 2;
        if (finalScore >= 40) return 1;
        return 0;
    }

    private static void executeFizzle(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getEntityWorld();
        Vec3d pos = p1.getEntityPos().add(p2.getEntityPos()).multiply(0.5).add(0, 1.4, 0);

        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.5f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.8f, 0.5f);

        world.spawnParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 12, 0.4, 0.3, 0.4, 0.03);
        world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 8, 0.3, 0.3, 0.3, 0.02);

        p1.sendMessage(net.minecraft.text.Text.literal("§7*missed!* timing off..."), true);
        p2.sendMessage(net.minecraft.text.Text.literal("§7*missed!* timing off..."), true);

        chargeStartTime.remove(p1.getUuid());
        chargeStartTime.remove(p2.getUuid());
        fireLevel.remove(p1.getUuid());
        fireLevel.remove(p2.getUuid());

        if (heavenReady.remove(p1.getUuid())) {
            broadcastHeavenReadyStatus(p1.getEntityWorld().getServer(), p1.getUuid(), false);
            System.out.println("[Heaven Dap] " + p1.getName().getString() + " lost heaven ready (fizzle)");
        }
        if (heavenReady.remove(p2.getUuid())) {
            broadcastHeavenReadyStatus(p2.getEntityWorld().getServer(), p2.getUuid(), false);
            System.out.println("[Heaven Dap] " + p2.getName().getString() + " lost heaven ready (fizzle)");
        }

        broadcastChargeCancel(p1);
        broadcastChargeCancel(p2);

        PoseNetworking.broadcastAnimState(p1, 0); // 0 = NONE
        PoseNetworking.broadcastAnimState(p2, 0); // 0 = NONE

        UUID p1Id = p1.getUuid();
        UUID p2Id = p2.getUuid();
        for (ServerPlayerEntity p : p1.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, new FireDapFirstPersonPayload(p1Id, false));
            ServerPlayNetworking.send(p, new FireDapFirstPersonPayload(p2Id, false));
        }

        System.out.println("[ChargedDap] Fizzle - reset animation + FP for both players");

        long now = System.currentTimeMillis();
        cooldowns.put(p1.getUuid(), now + 500);  
        cooldowns.put(p2.getUuid(), now + 500);
    }

    
    public static void executeWhiff(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();

        // Position in front of player's hand
        Vec3d pos = player.getEntityPos().add(0, 1.4, 0);
        Vec3d look = player.getRotationVector();
        pos = pos.add(look.multiply(0.5));

        // 10% chance for custom miss sound, 90% old sounds
        if (Math.random() < 0.1) {
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_MISS, SoundCategory.PLAYERS, 1.0f, 1.0f);
        } else {
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.6f);
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 0.5f, 1.5f);
        }

        // Poof/smoke particles
        world.spawnParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 8, 0.2, 0.2, 0.2, 0.02);
        world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 5, 0.15, 0.15, 0.15, 0.01);

        // Block actions during dap_down animation (0.33 sec = 330ms)
        setBlockingAnimation(player.getUuid(), 330);

        PoseNetworking.broadcastAnimState(player, 0); // 0 = NONE

        // FIX: Hide FP display when whiffing (both-miss bug fix!)
        UUID playerId = player.getUuid();
        for (ServerPlayerEntity p : player.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, new FireDapFirstPersonPayload(playerId, false));
        }

        System.out.println("[ChargedDap] Whiff - reset animation + FP to NONE");

        player.sendMessage(net.minecraft.text.Text.literal("§7*whoosh*"), true);
    }

    //  TIER 0: Weak 
    private static void executeTier0(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        // Create DapSession for smooth positioning + whole body rotation
        DapSession session = DapSessionManager.createSession(
                id1, id2,
                1.4,  // Distance apart (closer for better hand touch)
                DapSession.DapType.NORMAL_DAP
        );

        if (session == null) {
            System.out.println("[Tier Dap] ⚠️ DapSession creation failed - using instant fallback");
            // Fallback if session creation fails
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            spawnPrecisionDapParticles(world, pos, 0);
            p1.sendMessage(net.minecraft.text.Text.literal("§7Weak dap..."), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§7Weak dap..."), true);
            return;
        }

        // Set callback for when positioning completes
        session.onComplete(() -> {
            // Schedule sound and particles at 0.25s after positioning
            new Thread(() -> {
                try {
                    Thread.sleep(250);
                    world.getWorld().getServer().execute(() -> {
                        Vec3d midpoint = p1.getEntityPos().add(p2.getEntityPos()).multiply(0.5);
                        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                                ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        spawnPrecisionDapParticles(world, midpoint, 0);
                    });
                } catch (InterruptedException e) {}
            }).start();

            applyKnockback(p1, p2, pos, 0.1);
            p1.sendMessage(net.minecraft.text.Text.literal("§7Weak dap..."), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§7Weak dap..."), true);
        });
    }

    //  TIER 1: Decent 
    private static void executeTier1(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        // Create DapSession for smooth positioning + whole body rotation
        DapSession session = DapSessionManager.createSession(
                id1, id2,
                1.4,  // Distance apart (closer)
                DapSession.DapType.NORMAL_DAP
        );

        if (session == null) {
            // Fallback
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            spawnPrecisionDapParticles(world, pos, 1);
            p1.sendMessage(net.minecraft.text.Text.literal("§e✋ Decent Dap!"), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§e✋ Decent Dap!"), true);
            return;
        }

        session.onComplete(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(250);
                    world.getServer().execute(() -> {
                        Vec3d midpoint = p1.getEntityPos().add(p2.getEntityPos()).multiply(0.5);
                        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                                ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        spawnPrecisionDapParticles(world, midpoint, 1);
                    });
                } catch (InterruptedException e) {}
            }).start();

            applyKnockback(p1, p2, pos, 0.3);
            p1.sendMessage(net.minecraft.text.Text.literal("§e✋ Decent Dap!"), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§e✋ Decent Dap!"), true);
        });
    }

    //  TIER 2: Good 
    private static void executeTier2(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        DapSession session = DapSessionManager.createSession(
                id1, id2,
                1.4,  // Distance apart (closer)
                DapSession.DapType.NORMAL_DAP
        );

        if (session == null) {
            // Fallback
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            spawnPrecisionDapParticles(world, pos, 2);
            p1.sendMessage(net.minecraft.text.Text.literal("§a✋ Good Dap! ✋"), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§a✋ Good Dap! ✋"), true);
            return;
        }

        session.onComplete(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(250);
                    world.getServer().execute(() -> {
                        Vec3d midpoint = p1.getEntityPos().add(p2.getEntityPos()).multiply(0.5);
                        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                                ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        spawnPrecisionDapParticles(world, midpoint, 2);
                    });
                } catch (InterruptedException e) {}
            }).start();

            applyKnockback(p1, p2, pos, 0.6);
            p1.sendMessage(net.minecraft.text.Text.literal("§a Good Dap! "), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§a Good Dap! "), true);
        });
    }

    //  TIER 3: Great - Has Perfect Window! 
    private static void executeTier3Great(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2,
                                          boolean perfectHit, boolean bothCharging) {
        if (bothCharging && perfectHit) {
            // Smoothly rotate players to face each other
            rotateBothPlayersToFaceEachOther(p1, p2);

            // ========== PERFECT GREAT DAP - WITH DAPSESSION SMOOTH POSITIONING! ==========

            long now = System.currentTimeMillis();
            UUID id1 = p1.getUuid();
            UUID id2 = p2.getUuid();

            System.out.println("[Perfect Dap Tier 3]  Starting DapSession for SMOOTH positioning!");

            // Create DapSession with 1.5 blocks distance for smooth positioning
            DapSession session = DapSessionManager.createSession(
                    id1, id2,
                    1.5,  // Perfect dap distance
                    DapSession.DapType.PERFECT_DAP
            );

            if (session == null) {
                System.out.println("[Perfect Dap]  Could not create session - falling back to normal dap");
                executeTier3Normal(world, pos, p1, p2);
                return;
            }

            // Track perfect dap state
            perfectDapStartTime.put(id1, now);
            perfectDapStartTime.put(id2, now);
            perfectDapPartner.put(id1, id2);
            perfectDapPartner.put(id2, id1);

            // Set callback for when positioning is complete
            session.onComplete(() -> {
                startPerfectDapTier3Animation(world, pos, p1, p2);
            });

            System.out.println("[Perfect Dap Tier 3]  DapSession created!");

        } else {
            executeTier3Normal(world, pos, p1, p2);
        }
    }

    /**
     * Start Perfect Dap Tier 3 animation AFTER smooth positioning completes
     */
    private static void startPerfectDapTier3Animation(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        long now = System.currentTimeMillis();
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        System.out.println("[Perfect Dap Tier 3] 🎬 Positioning complete - starting animation!");

        // LEFT CLICK to rotate body to match head! (User's brilliant solution!)
        p1.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        p2.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        System.out.println("[Perfect Dap]  Left click swing - body rotation synced!");

        // Spawn armor stand for PARTICLE POSITIONING (not player teleporting!)
        Vec3d p1Hand = p1.getEntityPos().add(0, 1.4, 0);
        Vec3d p2Hand = p2.getEntityPos().add(0, 1.4, 0);
        Vec3d handMid = p1Hand.add(p2Hand).multiply(0.5);

        net.minecraft.entity.decoration.ArmorStandEntity stand =
                new net.minecraft.entity.decoration.ArmorStandEntity(net.minecraft.entity.EntityType.ARMOR_STAND, world);
        stand.setPosition(handMid.x, handMid.y, handMid.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setFireTicks(0);  // Make sure it's NOT on fire!
        world.spawnEntity(stand);
        perfectDapArmorStands.put(id1, stand);
        System.out.println("[Perfect Dap] Armor stand spawned for PARTICLES at " + handMid);

        // Play PERFECT_DAP_HIT animation AFTER swing!
        PoseNetworking.broadcastAnimState(p1,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_HIT.ordinal());
        PoseNetworking.broadcastAnimState(p2,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_HIT.ordinal());

        // FREEZE for 1.29 seconds
        perfectDapFreezeEnd.put(id1, now + 1290);
        perfectDapFreezeEnd.put(id2, now + 1290);

        // Notify clients of freeze
        ServerPlayNetworking.send(p1, new PerfectDapFreezePayload(true));
        ServerPlayNetworking.send(p2, new PerfectDapFreezePayload(true));

        // Block actions during entire animation (1625ms)
        setBlockingAnimation(id1, 1625);
        setBlockingAnimation(id2, 1625);

        // Schedule effects at 0.15s
        long effectTime = now + 150;
        scheduledPerfectDapEffects.add(new ScheduledPerfectDapEffect(
                world, pos, p1, p2, effectTime
        ));

        p1.sendMessage(net.minecraft.text.Text.literal("§6§l PERFECT GREAT DAP! "), true);
        p2.sendMessage(net.minecraft.text.Text.literal("§6§l PERFECT GREAT DAP! "), true);

        DapSessionManager.removeSession(id1);
    }

   
    private static void executeTier3Normal(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();

        DapSession session = DapSessionManager.createSession(
                id1, id2,
                1.4,
                DapSession.DapType.NORMAL_DAP
        );

        if (session == null) {
            // Fallback
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            spawnPrecisionDapParticles(world, pos, 3);
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            createExplosion(world, pos, p1, p2, 3.5, 6.0f);
            applyKnockback(p1, p2, pos, 1.0);
            p1.sendMessage(net.minecraft.text.Text.literal("§6§l GREAT DAP! "), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§6§l GREAT DAP! "), true);
            return;
        }

        // Set callback
        session.onComplete(() -> {
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);

            spawnPrecisionDapParticles(world, pos, 3);
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            createExplosion(world, pos, p1, p2, 3.5, 6.0f);
            applyKnockback(p1, p2, pos, 1.0);
            p1.sendMessage(net.minecraft.text.Text.literal("§6§l GREAT DAP! "), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§6§l GREAT DAP! "), true);

         
            DapComboChain.startCombo(p1, p2, pos);
        });
    }

    
    private static void startStage2Extender(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        ServerWorld world = p1.getEntityWorld();
        long now = System.currentTimeMillis();

        System.out.println("[Normal Dap QTE] ");
        System.out.println("[Normal Dap QTE]  BOTH PRESSED! Starting extender!");
        System.out.println("[Normal Dap QTE] P1: " + p1.getName().getString());
        System.out.println("[Normal Dap QTE] P2: " + p2.getName().getString());

        // QTE state already cleaned up by QTEManager (it clears before calling callback)
        // Just clear the UI on clients
        ServerPlayNetworking.send(p1, new QTEClearPayload(id1));
        ServerPlayNetworking.send(p2, new QTEClearPayload(id2));

        // Freeze ONLY the two dapping players (not everyone on server!)
        System.out.println("[Normal Dap QTE] Freezing BOTH players...");
        ServerPlayNetworking.send(p1, new PerfectDapFreezePayload(true));
        ServerPlayNetworking.send(p2, new PerfectDapFreezePayload(true));

        // Track freeze end time
        perfectDapFreezeEnd.put(id1, now + 4500);
        perfectDapFreezeEnd.put(id2, now + 4500);

        // Broadcast P1 animation
        PoseNetworking.broadcastAnimState(p1,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND1_P1.ordinal());

        // Broadcast P2 animation
        PoseNetworking.broadcastAnimState(p2,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND1_P2.ordinal());

        p1.sendMessage(net.minecraft.text.Text.literal("§d§l★ EXTENDER DAP! ★"), true);
        p2.sendMessage(net.minecraft.text.Text.literal("§d§l★ EXTENDER DAP! ★"), true);

        System.out.println("[Normal Dap QTE] Animations started! Waiting 4.5s...");

      
        new Thread(() -> {
            try {
                Thread.sleep(4500);
                world.getServer().execute(() -> {
                    System.out.println("[Normal Dap QTE] Animation complete! Unfreezing...");

                    ServerPlayNetworking.send(p1, new PerfectDapFreezePayload(false));
                    ServerPlayNetworking.send(p2, new PerfectDapFreezePayload(false));

                    perfectDapFreezeEnd.remove(id1);
                    perfectDapFreezeEnd.remove(id2);

                    // Reset to NONE
                    PoseNetworking.broadcastAnimState(p1,
                            com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());
                    PoseNetworking.broadcastAnimState(p2,
                            com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());

                    // Cleanup
                    HighFiveHandler.cleanup(id1);
                    HighFiveHandler.cleanup(id2);

                    // Set 1 second combo cooldown
                    long cooldownTime = System.currentTimeMillis() + 1000;
                    comboCooldown.put(id1, cooldownTime);
                    comboCooldown.put(id2, cooldownTime);

                    System.out.println("[Normal Dap QTE] COMPLETE! Players unfrozen!");
                    System.out.println("[Normal Dap QTE] ");
                });
            } catch (InterruptedException e) {}
        }).start();
    }

    
    public static boolean isInQTE(UUID playerId) {
        return QTEManager.isInQTE(playerId) || DapComboChain.isInCombo(playerId);
    }

    // Track when to restore tick rate
    private static long tickSpeedRestoreTime = 0;

    //  TIER 4: LEGENDARY - Has Perfect Window! 
    private static void executeTier4Legendary(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2,
                                              boolean perfectHit, boolean bothCharging, double speed1, double speed2) {
        var server = world.getServer();

      

        UUID p1Id = p1.getUuid();
        UUID p2Id = p2.getUuid();
        boolean bothHeavenReady = heavenReady.contains(p1Id) && heavenReady.contains(p2Id);
        boolean bothMovingFast = speed1 >= PERFECT_LEGENDARY_MIN_INDIVIDUAL_SPEED &&
                speed2 >= PERFECT_LEGENDARY_MIN_INDIVIDUAL_SPEED;

        // DEBUG: Show requirements
        System.out.println("[Heaven Dap] Requirements check:");
        System.out.println("  bothHeavenReady (G held 5s): " + bothHeavenReady);
        System.out.println("    " + p1.getName().getString() + " heaven ready: " + heavenReady.contains(p1Id));
        System.out.println("    " + p2.getName().getString() + " heaven ready: " + heavenReady.contains(p2Id));
        System.out.println("  perfectHit: " + perfectHit);
        System.out.println("  bothMovingFast: " + bothMovingFast + " (need >=10 b/s)");
        System.out.println("    " + p1.getName().getString() + " speed: " + String.format("%.2f", speed1) + " b/s");
        System.out.println("    " + p2.getName().getString() + " speed: " + String.format("%.2f", speed2) + " b/s");

        if (bothHeavenReady && perfectHit && bothMovingFast) {
            System.out.println("[Legendary Dap]  PERFECT! Starting heaven + destruction...");

            startHeavenDap(p1, p2, pos, world);

            new Thread(() -> {
                try {
                    Thread.sleep(500);  // Small delay so they don't see it happen

                    world.getServer().execute(() -> {
                        System.out.println("[Heaven Dap] 💥 Spawning MASSIVE 100-BLOCK SPHERE NUKE...");

                        
                        world.createExplosion(null, pos.x, pos.y, pos.z, 100.0f, true,
                                net.minecraft.world.World.ExplosionSourceType.TNT);

                        spawnExpandingLegendarySonicBoom(world, pos);

                        activeHeavenParticles.add(new HeavenParticleSpawner(world, pos, 30000));
                        System.out.println("[Heaven Dap] Started 30-second particle spawner!");

                        // INITIAL MASSIVE PARTICLES
                        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 20, 5, 5, 5, 0);
                        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 300, 10, 10, 10, 0.5);
                        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 200, 8, 8, 8, 0.4);
                        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 10, 0, 0, 0, 0);

                        // Starburst
                        spawnStarBurst(world, pos, 100, 5.0);

                        // Shockwave
                        createMassiveShockwave(world, pos, p1, p2, 50.0, 10.0);

                        activeSaturnRings.add(new SaturnRing(pos, System.currentTimeMillis()));

                        System.out.println("[Heaven Dap]  Destruction complete! Players will see it when they return.");
                    });

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();

        } else if (bothCharging) {
           
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 2.0f, 0.5f);
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.0f, 0.7f);
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.PLAYERS, 2.0f, 0.8f);

            spawnStarBurst(world, pos, 40, 1.5);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 3, 1, 1, 1, 0);
            world.spawnParticles(ParticleTypes.SOUL, pos.x, pos.y, pos.z, 50, 0.5, 0.5, 0.5, 0.2);
            world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 100, 1.0, 1.0, 1.0, 0.1);

            world.createExplosion(null, pos.x, pos.y, pos.z, 6.0f, true,
                    net.minecraft.world.World.ExplosionSourceType.MOB);

            removeTotem(p1);
            removeTotem(p2);

            p1.setHealth(0);
            p2.setHealth(0);
            p1.onDeath(world.getDamageSources().magic());
            p2.onDeath(world.getDamageSources().magic());

            p1.sendMessage(net.minecraft.text.Text.literal("§4§l☠ THE POWER WAS TOO GREAT! ☠"), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§4§l☠ THE POWER WAS TOO GREAT! ☠"), true);

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(net.minecraft.text.Text.literal(
                        "§4§l☠ " + p1.getName().getString() + " §7and §4" + p2.getName().getString() +
                                " §7failed to achieve Perfect Friendship... §c§lTHEY PERISHED!"
                ), false);
            }
        } else {
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 2.0f, 0.9f);
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.0f, 1.0f);
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 2.0f, 0.9f);

            spawnStarBurst(world, pos, 30, 1.0);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 40, 0.5, 0.5, 0.5, 0.25);

            // Normal explosion
            world.createExplosion(null, pos.x, pos.y, pos.z, 5.0f, true,
                    net.minecraft.world.World.ExplosionSourceType.MOB);

            applyKnockback(p1, p2, pos, 2.0);

            p1.sendMessage(net.minecraft.text.Text.literal("§d§l LEGENDARY DAP! "), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§d§l LEGENDARY DAP! "), true);
        }
    }

    
    private static void removeTotem(ServerPlayerEntity player) {
        // Check main hand
        if (player.getMainHandStack().isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) {
            player.getMainHandStack().setCount(0);
        }
        // Check off hand
        if (player.getOffHandStack().isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) {
            player.getOffHandStack().setCount(0);
        }
    }

    public static void checkTickSpeedRestore(net.minecraft.server.MinecraftServer server) {
        long now = System.currentTimeMillis();

        if (tickSpeedRestoreTime > 0 && now >= tickSpeedRestoreTime) {
            // Restore tick rate to normal (20 tps)
            server.getCommandManager().executeWithPrefix(
                    server.getCommandSource().withSilent(),
                    "tick rate 20"
            );
            tickSpeedRestoreTime = 0;

            // Notify players
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(net.minecraft.text.Text.literal("§7Time returns to normal..."), false);
            }
        }

        // Check perfect friendship levitation end
        Iterator<Map.Entry<UUID, Long>> it = perfectFriendshipLevitation.entrySet().iterator();
        Set<UUID> processed = new HashSet<>();

        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            UUID playerId = entry.getKey();
            long endTime = entry.getValue();

            if (now >= endTime && !processed.contains(playerId)) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                UUID partnerId = perfectFriendshipPartner.get(playerId);
                ServerPlayerEntity partner = partnerId != null ? server.getPlayerManager().getPlayer(partnerId) : null;

                if (player != null) {
                    // Give slow falling for 30 seconds (600 ticks)
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 600, 0, false, true));
                    processed.add(playerId);
                }

                // Announce PERFECT FRIENDSHIP if both players are online and we haven't announced yet
                if (player != null && partner != null && !processed.contains(partnerId)) {
                    // Give partner slow falling too
                    partner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 600, 0, false, true));
                    processed.add(partnerId);

                    // Announce in chat!
                    for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                        p.sendMessage(net.minecraft.text.Text.literal(
                                "§d§l " + player.getName().getString() + " §7and §d§l" + partner.getName().getString() +
                                        " §7have achieved §b§lPERFECT FRIENDSHIP§7! §d§l"
                        ), false);
                    }
                }

                it.remove();
            }
        }

        // Clean up partner tracking for processed players
        for (UUID id : processed) {
            perfectFriendshipPartner.remove(id);
            perfectFriendshipLevitation.remove(id);
        }
    }

   
    private static void startHeavenDap(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d midpoint, ServerWorld world) {
        long now = System.currentTimeMillis();
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        System.out.println("[Heaven Dap]  STARTING HEAVEN DAP! ");
        System.out.println("[Heaven Dap] Players: " + p1.getName().getString() + " & " + p2.getName().getString());

        // ========== STEP 1: IMPACT FRAMES + SOUND + EXPLOSION ==========

        // Send impact frames to BOTH players
        System.out.println("[Heaven Dap] 🎬 Sending impact frames...");
        ServerPlayNetworking.send(p1, new PerfectDapImpactFramePayload(1));
        ServerPlayNetworking.send(p2, new PerfectDapImpactFramePayload(1));

        // Play EPIC DAP SOUND!
        System.out.println("[Heaven Dap] 🔊 Playing dap sound...");
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 3.0f, 1.2f);  // Louder!
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 2.0f, 1.0f);

        // MASSIVE EXPLOSION at impact point!
        System.out.println("[Heaven Dap] 💥 Creating explosion...");
        world.createExplosion(null, midpoint.x, midpoint.y, midpoint.z, 8.0f, false,
                World.ExplosionSourceType.MOB);

        // SONIC BOOM PARTICLES - 3 expanding white circles!
        System.out.println("[Heaven Dap] ⚪ Spawning sonic boom...");
        spawnSonicBoomCircles(world, midpoint);

        // Additional epic particles
        world.spawnParticles(ParticleTypes.FLASH, midpoint.x, midpoint.y, midpoint.z, 5, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, midpoint.x, midpoint.y, midpoint.z, 3, 0.5, 0.5, 0.5, 0);
        world.spawnParticles(ParticleTypes.END_ROD, midpoint.x, midpoint.y, midpoint.z, 50, 1.0, 1.0, 1.0, 0.3);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, midpoint.x, midpoint.y, midpoint.z, 40, 0.8, 0.8, 0.8, 0.2);


        // Save original midpoint and state
        heavenPlayers.put(id1, new HeavenDapData(midpoint, world, now, id2));
        heavenPlayers.put(id2, new HeavenDapData(midpoint, world, now, id1));

        // Wait 300ms for impact frames to finish
        new Thread(() -> {
            try {
                Thread.sleep(300);  // Impact frames are 300ms now (was 800ms)

                p1.getEntityWorld().getServer().execute(() -> {
                    if (p1.isRemoved() || p2.isRemoved()) return;

                    System.out.println("[Heaven Dap] Impact frames done, WHITE SCREEN + teleporting to heaven...");

                    double heavenY = 500.0;
                    Vec3d heavenMid = new Vec3d(midpoint.x, heavenY, midpoint.z);

                    // Get direction between players
                    Vec3d dir = p2.getEntityPos().subtract(p1.getEntityPos()).normalize();

                    // Position them 5 blocks apart (2.5 from center each)
                    Vec3d pos1 = heavenMid.add(dir.multiply(-2.5));
                    Vec3d pos2 = heavenMid.add(dir.multiply(2.5));

                    // Calculate yaw to face each other
                    double dx = pos2.x - pos1.x;
                    double dz = pos2.z - pos1.z;
                    float yaw1 = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
                    float yaw2 = yaw1 + 180;

                    // TP both to heaven
                    p1.teleport(world, pos1.x, pos1.y, pos1.z, yaw1, 0);
                    p2.teleport(world, pos2.x, pos2.y, pos2.z, yaw2, 0);

                    p1.stopFallFlying();
                    p2.stopFallFlying();
                    p1.setVelocity(Vec3d.ZERO);
                    p2.setVelocity(Vec3d.ZERO);
                    p1.velocityDirty = true;
                    p2.velocityDirty = true;

                    // Apply slow falling (20 seconds - covers full sequence)
                    p1.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 400, 0, false, false));
                    p2.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 400, 0, false, false));

                    // Apply nausea for 2 seconds at start of heaven (disorienting arrival)
                    p1.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 40, 0, false, false));
                    p2.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 40, 0, false, false));

                    // Tell clients to show white overlay + MUTE SOUND
                    ServerPlayNetworking.send(p1, new HeavenDapPayloads.HeavenDapStartPayload());
                    ServerPlayNetworking.send(p2, new HeavenDapPayloads.HeavenDapStartPayload());

                    System.out.println("[Heaven Dap] Players in heaven at Y=" + heavenY + ", 5 blocks apart");
                });

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    
    private static void spawnSonicBoomCircles(ServerWorld world, Vec3d pos) {
        new Thread(() -> {
            try {
                for (int circle = 0; circle < 3; circle++) {
                    final int circleNum = circle;
                    final double baseRadius = 2.0 + (circle * 2.0); 

                    world.getServer().execute(() -> {
                        for (int i = 0; i < 60; i++) {  
                            double angle = Math.toRadians(i * 6);  
                            double x = pos.x + Math.cos(angle) * baseRadius;
                            double z = pos.z + Math.sin(angle) * baseRadius;

                            world.spawnParticles(ParticleTypes.WHITE_ASH,
                                    x, pos.y + 0.5, z,
                                    3, 0.1, 0.3, 0.1, 0.05);

                            world.spawnParticles(ParticleTypes.CLOUD,
                                    x, pos.y + 0.5, z,
                                    2, 0.05, 0.2, 0.05, 0.02);
                        }
                        System.out.println("[Heaven Dap] Sonic boom circle " + (circleNum + 1) + " spawned (radius: " + baseRadius + ")");
                    });

                    Thread.sleep(100);  
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

   
    private static void spawnPerfectDapSonicBoom(ServerWorld world, Vec3d center) {
        // Single expanding ring: 0.5 → 3 blocks
        spawnExpandingRing(world, center, 0.5, 3.0, 200);
        System.out.println("[Perfect Dap] ⚪ Sonic boom ring (0.5→3 blocks)");
    }

    /**
     * Spawn 1 expanding FIRE circle for fire dap shockwave effect
     */
    private static void spawnFireDapSonicBoom(ServerWorld world, Vec3d pos) {
        new Thread(() -> {
            try {
                // Ring 1: 2 → 10 blocks (FIRE)
                spawnExpandingFireRing(world, pos, 2.0, 10.0, 250);
                Thread.sleep(100);

                // Ring 2: 5 → 15 blocks (FIRE - BIGGER!)
                spawnExpandingFireRing(world, pos, 5.0, 15.0, 350);

                System.out.println("[Fire Dap] 🔥 2 fire sonic boom rings spawned (10 & 15 blocks)");

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Spawn expanding FIRE ring
     */
    private static void spawnExpandingFireRing(ServerWorld world, Vec3d center, double startRadius, double endRadius, int durationMs) {
        int steps = 15;
        double radiusStep = (endRadius - startRadius) / steps;
        long stepDuration = durationMs / steps;

        new Thread(() -> {
            try {
                for (int step = 0; step < steps; step++) {
                    double radius = startRadius + (radiusStep * step);
                    final double currentRadius = radius;

                    world.getServer().execute(() -> {
                        int particleCount = (int) (currentRadius * 15);  // More particles for bigger rings
                        for (int i = 0; i < particleCount; i++) {
                            double angle = Math.toRadians((360.0 / particleCount) * i);

                            double x = center.x + Math.cos(angle) * currentRadius;
                            double z = center.z + Math.sin(angle) * currentRadius;
                            double y = center.y + 0.5;  // LOWERED - under arm!

                            // Outward velocity - HORIZONTAL ONLY!
                            double vx = Math.cos(angle) * 0.35;
                            double vz = Math.sin(angle) * 0.35;

                            // FIRE particles - NO UPWARD VELOCITY!
                            world.spawnParticles(ParticleTypes.FLAME,
                                    x, y, z, 0, vx, 0.0, vz, 0.5);  // Y = 0!
                            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                                    x, y, z, 0, vx * 0.8, 0.0, vz * 0.8, 0.4);  // Y = 0!

                            // Lava every few particles
                            if (i % 3 == 0) {
                                world.spawnParticles(ParticleTypes.LAVA,
                                        x, y, z, 1, 0, 0, 0, 0);
                            }
                        }
                    });

                    Thread.sleep(stepDuration);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Spawn EXPANDING LEGENDARY SONIC BOOM - 3 massive rings!
     * Rings grow: 1→6→15→30 blocks
     * Players in heaven when this happens - they see aftermath when they return!
     */
    private static void spawnExpandingLegendarySonicBoom(ServerWorld world, Vec3d center) {
        new Thread(() -> {
            try {
                // Ring 1: 1 → 6 blocks
                spawnExpandingRing(world, center, 1.0, 6.0, 300);
                Thread.sleep(200);

                // Ring 2: 6 → 15 blocks
                spawnExpandingRing(world, center, 6.0, 15.0, 400);
                Thread.sleep(200);

                // Ring 3: 15 → 30 blocks (MASSIVE!)
                spawnExpandingRing(world, center, 15.0, 30.0, 500);

                System.out.println("[Legendary Dap] 🌀 3 EXPANDING SONIC BOOM RINGS COMPLETE!");

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Spawn a single expanding ring with outward velocity (YouTube style!)
     */
    private static void spawnExpandingRing(ServerWorld world, Vec3d center, double startRadius, double endRadius, int durationMs) {
        int steps = 20;  // Smooth animation
        double radiusStep = (endRadius - startRadius) / steps;
        long stepDuration = durationMs / steps;

        new Thread(() -> {
            try {
                for (int step = 0; step < steps; step++) {
                    double radius = startRadius + (radiusStep * step);
                    final double currentRadius = radius;

                    world.getServer().execute(() -> {
                        // Spawn circle with OUTWARD velocity particles
                        int particleCount = (int) (currentRadius * 20);  // More particles for bigger rings
                        for (int i = 0; i < particleCount; i++) {
                            double angle = Math.toRadians((360.0 / particleCount) * i);

                            // Position on circle UNDER ARM (lowered!)
                            double x = center.x + Math.cos(angle) * currentRadius;
                            double z = center.z + Math.sin(angle) * currentRadius;
                            double y = center.y + 0.5;  // LOWERED - under arm!

                            // OUTWARD velocity - HORIZONTAL ONLY!
                            double vx = Math.cos(angle) * 0.4;
                            double vz = Math.sin(angle) * 0.4;

                            // White particles - NO UPWARD VELOCITY!
                            world.spawnParticles(ParticleTypes.CLOUD,
                                    x, y, z, 0, vx, 0.0, vz, 0.6);  // Y velocity = 0!
                            world.spawnParticles(ParticleTypes.WHITE_ASH,
                                    x, y, z, 0, vx, 0.0, vz, 0.5);  // Y velocity = 0!

                            // Vertical beams every 15 degrees - STAY AT GROUND!
                            if (i % (particleCount / 24) == 0) {
                                world.spawnParticles(ParticleTypes.END_ROD,
                                        x, y, z, 1, 0, 0, 0, 0);  // No upward movement!
                            }
                        }
                    });

                    Thread.sleep(stepDuration);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Create massive shockwave for legendary dap
     */
    private static void createMassiveShockwave(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2,
                                               double radius, double strength) {
        Box shockwaveBox = new Box(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );

        // Push all entities away MASSIVELY
        for (Entity entity : world.getOtherEntities(null, shockwaveBox)) {
            if (entity == p1 || entity == p2) continue;  // Players are in heaven

            double dist = entity.getEntityPos().distanceTo(pos);
            if (dist > radius || dist < 0.5) continue;

            // Massive knockback
            double knockbackStrength = (1.0 - dist / radius) * strength;
            Vec3d knockDir = entity.getEntityPos().subtract(pos).normalize();

            entity.addVelocity(
                    knockDir.x * knockbackStrength * 2.0,
                    knockbackStrength * 1.5,
                    knockDir.z * knockbackStrength * 2.0
            );
            entity.velocityDirty = true;
        }

        // Sound
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 3.0f, 0.5f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.PLAYERS, 3.0f, 0.6f);
    }

    //  TIER 5: FIRE DAP - The Ultimate! 
    private static void executeTier5FireDap(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2,
                                            boolean perfectHit) {

        // Epic Dap sound!
        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 2.0f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.FIRE_IMPACT, SoundCategory.PLAYERS, 2.0f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 1.5f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.5f, 1.3f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 2.0f, 0.8f);

        spawnFireDapSonicBoom(world, pos);

        Random rand = new Random();
        int sphereParticles = 24; 
        for (int i = 0; i < sphereParticles; i++) {
            double theta = rand.nextDouble() * 2 * Math.PI;  // Random angle around Y
            double phi = Math.acos(2 * rand.nextDouble() - 1); // Random angle from top
            double radius = 0.5 + rand.nextDouble() * 0.5;     // Start at 0.5-1 block radius

            // Calculate position on sphere
            double dx = Math.sin(phi) * Math.cos(theta) * radius;
            double dy = Math.cos(phi) * radius;
            double dz = Math.sin(phi) * Math.sin(theta) * radius;

            // Spawn with velocity going OUTWARD (same direction as position offset)
            double speed = 0.15 + rand.nextDouble() * 0.15;
            world.spawnParticles(ParticleTypes.FLAME,
                    pos.x + dx, pos.y + dy + 1.0, pos.z + dz,
                    1, dx * speed, dy * speed, dz * speed, 0.1);
        }

        // Core impact particles
        world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y + 1.0, pos.z, 30, 0.3, 0.3, 0.3, 0.2);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 1.0, pos.z, 15, 0.2, 0.2, 0.2, 0.15);
        world.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y + 1.0, pos.z, 8, 0.3, 0.3, 0.3, 0);
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 3, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z, 20, 0.5, 0.5, 0.5, 0.15);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z, 25, 0.5, 0.5, 0.5, 0.3);

        // Fire ring expanding outward on ground
        for (int i = 0; i < 16; i++) {
            double angle = Math.toRadians(i * 22.5);
            for (double r = 1; r <= 2.5; r += 0.5) {
                double ringX = Math.cos(angle) * r;
                double ringZ = Math.sin(angle) * r;
                world.spawnParticles(ParticleTypes.FLAME, pos.x + ringX, pos.y, pos.z + ringZ, 1, 0.05, 0.1, 0.05, 0.02);
            }
        }

        // MASSIVE shockwave - pushes ALL entities
        createFireShockwave(world, pos, p1, p2);

        if (perfectHit) {
            // PERFECT FIRE DAP! No sounds/freeze here - FireDapCombo handles everything!

            // Extra particles for perfect
            world.spawnParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y, pos.z, 40, 0.8, 0.8, 0.8, 0.15);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 50, 0.6, 0.6, 0.6, 0.3);

            p1.sendMessage(net.minecraft.text.Text.literal("§c§l PERFECT FIRE DAP! "), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§c§l🔥 PERFECT FIRE DAP! "), true);
        } else {
            // Normal fire dap - still epic!
            p1.sendMessage(net.minecraft.text.Text.literal("§c§l FIRE DAP! "), true);
            p2.sendMessage(net.minecraft.text.Text.literal("§c§l FIRE DAP! "), true);
        }

        // START FIRE DAP COMBO SYSTEM - works for ALL fire daps (perfect or not)!
        startFireDap(p1, p2, pos);
        System.out.println("[Fire Dap]  Calling startFireDap for tier 5!");

        // Announce to everyone
        for (ServerPlayerEntity nearby : PlayerLookup.around(world, pos, 50)) {
            if (nearby != p1 && nearby != p2) {
                String prefix = perfectHit ? "§c§lPERFECT " : "§c§l";
                nearby.sendMessage(net.minecraft.text.Text.literal(
                        prefix + " " + p1.getName().getString() + " §7and §c" + p2.getName().getString() +
                                " §7unleashed a §c§lFIRE DAP§7!"
                ), false);
            }
        }
    }


   
    private static void spawnPrecisionDapParticles(ServerWorld world, Vec3d pos, int tier) {
        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        stand.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0f, 0.0f);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setCustomNameVisible(false);

        world.spawnEntity(stand);

        // Get exact position (chest height where hands meet)
        Vec3d exactPos = stand.getEntityPos().add(0, 1.0, 0);

        // Particle count based on tier (higher tier = more particles)
        int particleCount = 15 + (tier * 5);

        // Spawn CRIT particles that EXPLODE outward!
        world.spawnParticles(
                ParticleTypes.CRIT,
                exactPos.x, exactPos.y, exactPos.z,
                particleCount,
                0.3, 0.3, 0.3,  // Spread in all directions
                0.15  // Velocity - particles shoot outward!
        );

        // Add enchanted crit for tier 3+ (perfect daps)
        if (tier >= 3) {
            world.spawnParticles(
                    ParticleTypes.ENCHANTED_HIT,
                    exactPos.x, exactPos.y, exactPos.z,
                    particleCount / 2,
                    0.4, 0.4, 0.4,  // Larger spread
                    0.2  // Higher velocity for tier 3+
            );
        }

        new Thread(() -> {
            try {
                Thread.sleep(50);
                world.getServer().execute(() -> stand.discard());
            } catch (InterruptedException e) {
                world.getServer().execute(() -> stand.discard());
            }
        }).start();
    }


   
    private static float calculateYawToFace(ServerPlayerEntity from, ServerPlayerEntity to) {
        Vec3d fromPos = from.getEntityPos();
        Vec3d toPos = to.getEntityPos();
        double dx = toPos.x - fromPos.x;
        double dz = toPos.z - fromPos.z;
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        return (float) angle;
    }

    
    private static void smoothRotateToFacePartner(ServerPlayerEntity player, ServerPlayerEntity partner) {
        float targetYaw = calculateYawToFace(player, partner);
        float currentYaw = player.getYaw();

        targetYaw = ((targetYaw % 360) + 540) % 360 - 180;
        currentYaw = ((currentYaw % 360) + 540) % 360 - 180;

        float diff = targetYaw - currentYaw;
        if (diff > 180) diff -= 360;
        if (diff < -180) diff += 360;

        final float finalCurrentYaw = currentYaw;
        final float finalDiff = diff;
        final int steps = 10;
        final long delayPerStep = 50; // 50ms = 0.5 seconds total

        new Thread(() -> {
            for (int i = 1; i <= steps; i++) {
                final int step = i;
                try {
                    Thread.sleep(delayPerStep);
                    player.getEntityWorld().getServer().execute(() -> {
                        float progress = (float) step / steps;
                        float newYaw = finalCurrentYaw + (finalDiff * progress);
                        player.setYaw(newYaw);
                        // Don't control camera/head - only rotate body
                        player.networkHandler.sendPacket(
                                new net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket(player)
                        );
                    });
                } catch (InterruptedException e) { break; }
            }
        }).start();
    }

    
    private static void rotateBothPlayersToFaceEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        smoothRotateToFacePartner(p1, p2);
        smoothRotateToFacePartner(p2, p1);
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
        p1.setVelocity(0, 0, 0);
        p2.setVelocity(0, 0, 0);

        p1.velocityDirty = true;
        p2.velocityDirty = true;
    }

    
    public static void applyImpactFreeze(ServerPlayerEntity p1, ServerPlayerEntity p2, int ticks) {
        if (ticks <= 0) return;

        p1.setVelocity(0, 0, 0);
        p2.setVelocity(0, 0, 0);
        p1.velocityDirty = true;
        p2.velocityDirty = true;

        impactFreezeTicks.put(p1.getUuid(), ticks);
        impactFreezeTicks.put(p2.getUuid(), ticks);
    }

    private static void createExplosion(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2,
                                        double radius, float maxDamage) {
        Box damageBox = new Box(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );

        for (Entity entity : world.getOtherEntities(null, damageBox)) {
            if (entity == p1 || entity == p2) continue;

            double dist = entity.getEntityPos().distanceTo(pos);
            if (dist > radius) continue;

            double knockbackStrength = (1.0 - dist / radius) * 2.0;
            Vec3d knockDir = entity.getEntityPos().subtract(pos).normalize();
            entity.addVelocity(knockDir.x * knockbackStrength, knockbackStrength * 0.5, knockDir.z * knockbackStrength);
            entity.velocityDirty = true;

            if (entity instanceof ServerPlayerEntity target) {
                float damage = (float)((1.0 - dist / radius) * maxDamage);
                target.damage(world.getDamageSources().explosion(null, null), damage);
            }
        }
    }

    
    private static void createShockwave(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2,
                                        double radius, double strength) {
        Box shockwaveBox = new Box(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );

        for (int i = 0; i < 36; i++) {
            double angle = (i / 36.0) * Math.PI * 2;
            for (double r = 1; r <= radius; r += 2) {
                double px = pos.x + Math.cos(angle) * r;
                double pz = pos.z + Math.sin(angle) * r;
                world.spawnParticles(ParticleTypes.CLOUD, px, pos.y, pz, 1, 0, 0.1, 0, 0.02);
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, px, pos.y + 0.5, pz, 1, 0, 0, 0, 0);
            }
        }

        for (Entity entity : world.getOtherEntities(null, shockwaveBox)) {
            if (entity == p1 || entity == p2) continue;

            double dist = entity.getEntityPos().distanceTo(pos);
            if (dist > radius || dist < 0.5) continue;
            double knockbackStrength = (1.0 - dist / radius) * strength;
            Vec3d knockDir = entity.getEntityPos().subtract(pos).normalize();
            entity.addVelocity(
                    knockDir.x * knockbackStrength * 1.5,
                    knockbackStrength * 0.6,
                    knockDir.z * knockbackStrength * 1.5
            );
            entity.velocityDirty = true;

            if (entity instanceof ServerPlayerEntity target) {
                world.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 0.8f);
            }
        }

        // Shockwave sound
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.6f, 1.5f);
    }

   
    private static void handleUnderwaterPerfectDap(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (!p1.isSubmergedInWater() && !p2.isSubmergedInWater()) {
            return;
        }

        System.out.println("[Perfect Dap] 🌊 UNDERWATER! Starting continuous water removal for 5 seconds...");

        p1.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.WATER_BREATHING, 120, 0, false, false));
        p2.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.WATER_BREATHING, 120, 0, false, false));

        world.spawnParticles(ParticleTypes.SPLASH,
                pos.x, pos.y, pos.z,
                80, 2.0, 2.0, 2.0, 0.4);
        world.spawnParticles(ParticleTypes.BUBBLE_POP,
                pos.x, pos.y, pos.z,
                40, 1.5, 1.5, 1.5, 0.3);

        UUID trackId = UUID.randomUUID();
        underwaterRemovalStart.put(trackId, System.currentTimeMillis());
        underwaterRemovalPos.put(trackId, pos);
        underwaterRemovalWorld.put(trackId, world);

        System.out.println("[Perfect Dap] 🌊 Registered continuous water removal (ID: " + trackId + ")");
    }

    private static void createLegendaryExplosion(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        double radius = 6.0;
        Box damageBox = new Box(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );

        for (Entity entity : world.getOtherEntities(null, damageBox)) {
            if (entity == p1 || entity == p2) continue;

            double dist = entity.getEntityPos().distanceTo(pos);
            if (dist > radius) continue;

            double knockbackStrength = (1.0 - dist / radius) * 3.0;
            Vec3d knockDir = entity.getEntityPos().subtract(pos).normalize();
            entity.addVelocity(knockDir.x * knockbackStrength, knockbackStrength * 0.7, knockDir.z * knockbackStrength);
            entity.velocityDirty = true;

            float damage;
            if (entity instanceof ServerPlayerEntity) {
                damage = (float)((1.0 - dist / radius) * 8.0);
            } else {
                damage = (float)((1.0 - dist / radius) * 20.0);
            }
            entity.damage(world.getDamageSources().explosion(null, null), damage);
        }
    }

    private static void createFireShockwave(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        double radius = 50.0; 
        Box damageBox = new Box(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );

        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 1, false, false, true));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 1, false, false, true));

        for (Entity entity : world.getOtherEntities(null, damageBox)) {
            if (entity == p1 || entity == p2) continue;

            double dist = entity.getEntityPos().distanceTo(pos);
            if (dist > radius) continue;

            double knockbackStrength = (1.0 - dist / radius) * 15.0; // INSANE knockback!
            Vec3d knockDir = entity.getEntityPos().subtract(pos).normalize();

            entity.addVelocity(
                    knockDir.x * knockbackStrength,
                    knockbackStrength * 1.5,  // Strong upward force
                    knockDir.z * knockbackStrength
            );
            entity.velocityDirty = true;

            // Set entities on fire for 5 seconds
            entity.setOnFireFor(5);
        }

      
        for (double ringRadius = 3.0; ringRadius <= 15.0; ringRadius += 1.5) {
            int points = (int)(ringRadius * 8); // More points for bigger circles
            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI * i) / points;
                double fireX = pos.x + Math.cos(angle) * ringRadius;
                double fireZ = pos.z + Math.sin(angle) * ringRadius;

                // Find ground level
                BlockPos groundPos = world.getTopPosition(
                        net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.ofFloored(fireX, pos.y, fireZ)
                );

                BlockPos firePos = groundPos.up();
                if (world.getBlockState(firePos).isAir()) {
                    world.setBlockState(firePos, net.minecraft.block.Blocks.FIRE.getDefaultState());

                    // Schedule fire removal after 3 seconds (60 ticks)
                    world.scheduleBlockTick(firePos, net.minecraft.block.Blocks.FIRE, 60);
                }
            }
        }

        // Spawn TONS of fire particles in the area
        Random rand = new Random();
        for (int i = 0; i < 200; i++) {
            double particleRadius = rand.nextDouble() * 20.0;
            double particleAngle = rand.nextDouble() * 2 * Math.PI;
            double px = pos.x + Math.cos(particleAngle) * particleRadius;
            double pz = pos.z + Math.sin(particleAngle) * particleRadius;
            double py = pos.y + rand.nextDouble() * 3.0;

            world.spawnParticles(ParticleTypes.FLAME, px, py, pz, 1, 0, 0.5, 0, 0.05);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1, 0, 0.3, 0, 0.03);
        }
    }

    private static void broadcastChargeCancel(ServerPlayerEntity player) {
        if (player == null) return;
        ChargeSyncPayload payload = new ChargeSyncPayload(player.getUuid(), 0f, 0f, false);
        for (ServerPlayerEntity other : PlayerLookup.all(player.getEntityWorld().getServer())) {
            ServerPlayNetworking.send(other, payload);
        }
    }

    /**
     * Broadcast whiff cooldown to client so UI knows not to show charge
     */
    private static void broadcastWhiffCooldown(ServerPlayerEntity player, long cooldownEnd) {
        if (player == null) return;
        WhiffCooldownPayload payload = new WhiffCooldownPayload(WHIFF_COOLDOWN_MS);  // Send duration, not end time
        ServerPlayNetworking.send(player, payload);

        // Also broadcast animation reset to all clients
        PoseNetworking.broadcastAnimState(player,
                com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());
    }

   
    public static void broadcastImpactFrame(ServerPlayerEntity p1, ServerPlayerEntity p2, int durationMs, boolean grayscale) {
        ImpactFramePayload payload = new ImpactFramePayload(durationMs, grayscale);
        if (p1 != null) ServerPlayNetworking.send(p1, payload);
        if (p2 != null) ServerPlayNetworking.send(p2, payload);

        if (p1 != null) {
            for (ServerPlayerEntity nearby : PlayerLookup.around(p1.getEntityWorld(), p1.getEntityPos(), 20)) {
                if (nearby != p1 && nearby != p2) {
                    ServerPlayNetworking.send(nearby, payload);
                }
            }
        }
    }

    private static boolean arePlayersFacingEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        Vec3d p1Pos = p1.getEntityPos();
        Vec3d p2Pos = p2.getEntityPos();

        Vec3d p1ToP2 = new Vec3d(p2Pos.x - p1Pos.x, 0, p2Pos.z - p1Pos.z).normalize();
        Vec3d p2ToP1 = p1ToP2.negate();

        double yaw1Rad = Math.toRadians(p1.getYaw());
        Vec3d look1 = new Vec3d(-Math.sin(yaw1Rad), 0, Math.cos(yaw1Rad));

        double yaw2Rad = Math.toRadians(p2.getYaw());
        Vec3d look2 = new Vec3d(-Math.sin(yaw2Rad), 0, Math.cos(yaw2Rad));

      
        double dot1 = look1.dotProduct(p1ToP2);
        double dot2 = look2.dotProduct(p2ToP1);

      
        return dot1 > -0.3 && dot2 > -0.3;
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

    
    private static Vec3d getEffectiveVelocity(ServerPlayerEntity player) {
        if (player.hasVehicle()) {
            Entity vehicle = player.getVehicle();
            if (vehicle != null) {
                return vehicle.getVelocity();
            }
        }

        if (player.isFallFlying()) {
            return player.getVelocity();
        }

        return player.getVelocity();
    }

    public static float getChargePercent(UUID playerId) {
        Long startTime = chargeStartTime.get(playerId);
        if (startTime == null) return 0f;

        long elapsed = System.currentTimeMillis() - startTime;
        return Math.min(1.0f, (float) elapsed / CHARGE_TIME_MS);
    }

    public static float getFireLevel(UUID playerId) {
        return fireLevel.getOrDefault(playerId, 0f);
    }

    public static boolean isCharging(UUID playerId) {
        return chargeStartTime.containsKey(playerId);
    }

    public static boolean isFullyCharged(UUID playerId) {
        Long startTime = chargeStartTime.get(playerId);
        if (startTime == null) return false;
        long chargeTime = System.currentTimeMillis() - startTime;
        float chargePercent = Math.min(chargeTime / (float)CHARGE_TIME_MS, 1.0f);
        return chargePercent >= 0.8f;
    }

 
    public static Long getChargeStartTime(UUID playerId) {
        return chargeStartTime.get(playerId);
    }

 
    public static void resetChargeStartTime(UUID playerId) {
        chargeStartTime.put(playerId, System.currentTimeMillis());
    }

    private static boolean isOnCooldown(UUID uuid) {
        Long cooldownEnd = cooldowns.get(uuid);
        if (cooldownEnd == null) return false;
        return System.currentTimeMillis() < cooldownEnd;
    }

    public static void cleanup(UUID uuid) {
        chargeStartTime.remove(uuid);
        releaseTime.remove(uuid);
        waitingForPartner.remove(uuid);
        cooldowns.remove(uuid);
        speedHistory.remove(uuid);
        fireStartTime.remove(uuid);
        fireGraceTime.remove(uuid);
        fireLevel.remove(uuid);
        impactFreezeTicks.remove(uuid);
        blockingAnimEndTime.remove(uuid);

        perfectDapStartTime.remove(uuid);
        perfectDapPartner.remove(uuid);
        perfectDapFreezeEnd.remove(uuid);

        // Fire Dap Combo cleanup
        fireDapStartTime.remove(uuid);
        fireDapPartner.remove(uuid);
        inFireDapHit.remove(uuid);
        fireCircleSpawned.remove(uuid);  
        fireDapComboRequestTime.remove(uuid);
        fireDapComboFreezeEnd.remove(uuid);
        pendingFireArmImpacts.remove(uuid);
        pendingFireTornadoSpawns.remove(uuid);
        fireComboActive.remove(uuid);  

        // Heaven Dap cleanup
        heavenPlayers.remove(uuid);
    }

   
    public static boolean isInBlockingAnimation(UUID uuid) {
        Long endTime = blockingAnimEndTime.get(uuid);
        if (endTime == null) return false;
        if (System.currentTimeMillis() >= endTime) {
            blockingAnimEndTime.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Set blocking animation state for player
     */
    public static void setBlockingAnimation(UUID uuid, long durationMs) {
        blockingAnimEndTime.put(uuid, System.currentTimeMillis() + durationMs);
    }

   
    public static boolean isPerfectDapFrozen(UUID playerId) {
        return perfectDapFreezeEnd.containsKey(playerId);
    }

 
    public static boolean isFireDapFrozen(UUID playerId) {
        return fireDapComboFreezeEnd.containsKey(playerId);
    }

   
    public static boolean isInFireDapBlockingState(UUID playerId) {
        return inFireDapHit.getOrDefault(playerId, false) || fireDapComboFreezeEnd.containsKey(playerId);
    }

   
    public static boolean isInComboCooldown(UUID playerId) {
        Long cooldownEnd = comboCooldown.get(playerId);
        if (cooldownEnd == null) return false;

        long now = System.currentTimeMillis();
        if (now < cooldownEnd) {
            return true;
        } else {
            comboCooldown.remove(playerId);
            return false;
        }
    }

   
    public static void startFireDap(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d midpoint) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();


        fireComboActive.put(id1, true);
        fireComboActive.put(id2, true);

        fireDapStartTime.put(id1, now);
        fireDapStartTime.put(id2, now);
        fireCircleSpawned.put(id1, false);
        fireCircleSpawned.put(id2, false);
        fireDapPartner.put(id1, id2);
        fireDapPartner.put(id2, id1);
        inFireDapHit.put(id1, true);
        inFireDapHit.put(id2, true);

        DapSession session = DapSessionManager.createSession(
                id1, id2,
                1.2,  // Fire dap distance
                DapSession.DapType.FIRE_DAP
        );

        if (session == null) {
            System.out.println("[Fire Dap]  Could not create session - aborting");
            // Clean up 
            fireComboActive.remove(id1);
            fireComboActive.remove(id2);
            fireDapStartTime.remove(id1);
            fireDapStartTime.remove(id2);
            return;
        }

        session.onComplete(() -> {
            startFireDapAnimation(p1, p2, midpoint);
        });

        System.out.println("[Fire Dap] DapSession created!");
    }

  
    private static void startFireDapAnimation(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d midpoint) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        ServerWorld world = p1.getEntityWorld();

        System.out.println("[Fire Dap] 🎬 Positioning complete - starting animation!");

        p1.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        p2.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        System.out.println("[Fire Dap]  Left click swing - body rotation synced!");

        Vec3d p1Hand = p1.getEntityPos().add(0, 1.4, 0);
        Vec3d p2Hand = p2.getEntityPos().add(0, 1.4, 0);
        Vec3d handMid = p1Hand.add(p2Hand).multiply(0.5);

        net.minecraft.entity.decoration.ArmorStandEntity stand =
                new net.minecraft.entity.decoration.ArmorStandEntity(net.minecraft.entity.EntityType.ARMOR_STAND, world);
        stand.setPosition(handMid.x, handMid.y, handMid.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setFireTicks(0);  
        world.spawnEntity(stand);
        fireDapArmorStands.put(id1, stand);
        System.out.println("[Fire Dap] Armor stand spawned for PARTICLES at " + handMid);

        PoseNetworking.broadcastAnimState(p1,
                com.cooptest.client.CoopAnimationHandler.AnimState.FIRE_DAP_HIT.ordinal());
        PoseNetworking.broadcastAnimState(p2,
                com.cooptest.client.CoopAnimationHandler.AnimState.FIRE_DAP_HIT.ordinal());


        for (ServerPlayerEntity player : p1.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new FireDapFirstPersonPayload(id1, true));
            ServerPlayNetworking.send(player, new FireDapFirstPersonPayload(id2, true));
        }

        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 255, false, false));
        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 255, false, false));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 255, false, false));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 255, false, false));

        ServerPlayNetworking.send(p1, new FireDapWindowPayload());
        ServerPlayNetworking.send(p2, new FireDapWindowPayload());

        System.out.println("[Fire Dap] Window sent - DapSession still active!");
    }

   

    private static void onFireDapJPress(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();


        if (!inFireDapHit.getOrDefault(playerId, false)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long startTime = fireDapStartTime.get(playerId);
        if (startTime == null) {
            return;
        }

        long elapsed = now - startTime;


        if (elapsed < FIRE_J_WINDOW_START || elapsed > FIRE_J_WINDOW_END) {
            player.sendMessage(net.minecraft.text.Text.literal("§cToo early/late for combo!"), true);
            return;
        }


        // Get partner
        UUID partnerId = fireDapPartner.get(playerId);
        if (partnerId == null) {
            return;
        }

        if (partnerId.equals(playerId)) {
            fireDapComboRequestTime.put(playerId, now);
            ServerPlayerEntity partner = player.getEntityWorld().getServer().getPlayerManager().getPlayer(partnerId);
            if (partner != null) {
                executeFireDapCombo(player, partner);
            }
            return;
        }

        ServerPlayerEntity partner = player.getEntityWorld().getServer().getPlayerManager().getPlayer(partnerId);
        if (partner == null) {
            return;
        }

        // Record request
        fireDapComboRequestTime.put(playerId, now);


        // Check if partner also pressed (within 1 second window)
        Long partnerRequestTime = fireDapComboRequestTime.get(partnerId);
        if (partnerRequestTime != null) {
            long diff = Math.abs(now - partnerRequestTime);
            if (diff < 1000) {  // 1 second window
                executeFireDapCombo(player, partner);
            }
        }
       
    }

   
    private static void executeFireDapCombo(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();


        inFireDapHit.remove(id1);
        inFireDapHit.remove(id2);
        fireDapComboRequestTime.remove(id1);
        fireDapComboRequestTime.remove(id2);

        DapSessionManager.removeSessionForPlayer(id1);

        p1.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        p2.swingHand(net.minecraft.util.Hand.MAIN_HAND);

        fireDapComboFreezeEnd.put(id1, now + FIRE_COMBO_FREEZE_MS);
        fireDapComboFreezeEnd.put(id2, now + FIRE_COMBO_FREEZE_MS);

        for (ServerPlayerEntity player : p1.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new FireDapFreezePayload(id1, true));
            ServerPlayNetworking.send(player, new FireDapFreezePayload(id2, true));
        }

        // Play combo animations IMMEDIATELY AFTER swing!
        PoseNetworking.broadcastAnimState(p1,
                com.cooptest.client.CoopAnimationHandler.AnimState.FIRE_DAP_COMBO_P1.ordinal());
        PoseNetworking.broadcastAnimState(p2,
                com.cooptest.client.CoopAnimationHandler.AnimState.FIRE_DAP_COMBO_P2.ordinal());

        // DRAGON ROAR SOUND! 🐉
        Vec3d midpoint = p1.getEntityPos().add(p2.getEntityPos()).multiply(0.5);
        p1.getEntityWorld().playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 2.0f, 0.8f);

        // ENABLE FIRST PERSON DISPLAY
        for (ServerPlayerEntity player : p1.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new FireDapFirstPersonPayload(id1, true));
            ServerPlayNetworking.send(player, new FireDapFirstPersonPayload(id2, true));
        }

        // Messages
        p1.sendMessage(net.minecraft.text.Text.literal("§c§l DIVINE FLAME COMBO! "), true);
        p2.sendMessage(net.minecraft.text.Text.literal("§c§l DIVINE FLAME COMBO! "), true);

        // GIVE IMMUNITY
        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 100, 255, false, false));
        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 255, false, false));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 100, 255, false, false));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 255, false, false));

        // SPAWN FIRE WALLS
        spawnVerticalFireWalls(p1, p2);

        // START AURA BEAMS
        auraBeamsActive = true;
        auraBeamStartTime = now;
        auraBeamPlayer1 = id1;
        auraBeamPlayer2 = id2;

        // Schedule arm impact at 1.33s
        pendingFireArmImpacts.put(id1, new FireDapScheduledEvent(p1, p2, now + FIRE_COMBO_ARM_IMPACT));

        // Schedule tornado at 1.46s
        pendingFireTornadoSpawns.put(id1, new FireDapScheduledEvent(p1, p2, now + FIRE_COMBO_TORNADO));

    }


    private static void spawnAnimatedAuraBeam(ServerPlayerEntity player, long elapsed) {
        ServerWorld world = player.getEntityWorld();
        Vec3d playerPos = player.getEntityPos();

        // Animation phase based on elapsed time
        double phase = (elapsed % 1000) / 1000.0;  // 0-1 cycle every second

        // Spawn 70-block tall beam with multiple animation layers
        for (int y = 0; y < 70; y++) {
            double currentY = playerPos.y + y;

            double coreAngle = (y * 20 + elapsed * 0.5) % 360;
            double coreRad = Math.toRadians(coreAngle);
            double coreRadius = 0.3;  // Tight core

            double coreX = playerPos.x + Math.cos(coreRad) * coreRadius;
            double coreZ = playerPos.z + Math.sin(coreRad) * coreRadius;

            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, coreX, currentY, coreZ, 1, 0.05, 0.05, 0.05, 0.01);
            if (y % 3 == 0) {
                world.spawnParticles(ParticleTypes.ENCHANT, coreX, currentY, coreZ, 2, 0.1, 0.1, 0.1, 0.5);
            }

            double midAngle = (y * 15 - elapsed * 0.3) % 360;  // Opposite direction!
            double midRad = Math.toRadians(midAngle);
            double midRadius = 0.6;

            double midX = playerPos.x + Math.cos(midRad) * midRadius;
            double midZ = playerPos.z + Math.sin(midRad) * midRadius;

            world.spawnParticles(ParticleTypes.FLAME, midX, currentY, midZ, 2, 0.1, 0.1, 0.1, 0.02);

            if (y % 2 == 0) {
                double outerAngle = (y * 10 + elapsed * 0.2) % 360;
                for (int i = 0; i < 4; i++) {
                    double angle = outerAngle + (i * 90);
                    double rad = Math.toRadians(angle);
                    double outerRadius = 0.9 + Math.sin(phase * Math.PI * 2) * 0.2;  // Pulsing!

                    double x = playerPos.x + Math.cos(rad) * outerRadius;
                    double z = playerPos.z + Math.sin(rad) * outerRadius;

                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, currentY, z, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }

            if (y % 5 == 0) {
                double waveOffset = Math.sin((y / 70.0 + phase) * Math.PI * 2) * 0.5;
                world.spawnParticles(ParticleTypes.END_ROD,
                        playerPos.x + waveOffset, currentY, playerPos.z,
                        1, 0.1, 0.1, 0.1, 0.02);
            }
        }

        for (double angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle + elapsed * 0.5);  // Rotating circle
            double radius = 3.0;

            double x = playerPos.x + Math.cos(rad) * radius;
            double z = playerPos.z + Math.sin(rad) * radius;

            world.spawnParticles(ParticleTypes.FLAME, x, playerPos.y + 0.1, z, 3, 0.1, 0.1, 0.1, 0.05);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, playerPos.y + 0.1, z, 2, 0.1, 0.1, 0.1, 0.03);

            // Pulsing energy waves
            if (angle % 30 == 0) {
                world.spawnParticles(ParticleTypes.END_ROD, x, playerPos.y + 0.1, z, 1, 0, 0, 0, 0);
            }
        }
    }

    
    private static void spawnVerticalFireWalls(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getEntityWorld();
        Vec3d midpoint = p1.getEntityPos().add(p2.getEntityPos()).multiply(0.5);


        

        double wallDistance = 5.0;  // 5 blocks from center
        int wallHeight = 50;  // 50 blocks tall
        int wallLength = 10;  // Each wall is 10 blocks long

        // North wall (positive Z)
        for (int x = -wallLength/2; x <= wallLength/2; x++) {
            for (int y = 0; y < wallHeight; y++) {
                double wx = midpoint.x + x;
                double wy = midpoint.y + y;
                double wz = midpoint.z + wallDistance;

                // Skip center 3 blocks ( zone for players)
                if (Math.abs(x) <= 1.5) continue;

                world.spawnParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                if (y % 3 == 0) {
                    world.spawnParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                }
            }
        }

        // South wall (negative Z)
        for (int x = -wallLength/2; x <= wallLength/2; x++) {
            for (int y = 0; y < wallHeight; y++) {
                double wx = midpoint.x + x;
                double wy = midpoint.y + y;
                double wz = midpoint.z - wallDistance;

                if (Math.abs(x) <= 1.5) continue;

                world.spawnParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                if (y % 3 == 0) {
                    world.spawnParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                }
            }
        }

        // East wall (positive X)
        for (int z = -wallLength/2; z <= wallLength/2; z++) {
            for (int y = 0; y < wallHeight; y++) {
                double wx = midpoint.x + wallDistance;
                double wy = midpoint.y + y;
                double wz = midpoint.z + z;

                if (Math.abs(z) <= 1.5) continue;

                world.spawnParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                if (y % 3 == 0) {
                    world.spawnParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                }
            }
        }

        // West wall (negative X)
        for (int z = -wallLength/2; z <= wallLength/2; z++) {
            for (int y = 0; y < wallHeight; y++) {
                double wx = midpoint.x - wallDistance;
                double wy = midpoint.y + y;
                double wz = midpoint.z + z;

                if (Math.abs(z) <= 1.5) continue;

                world.spawnParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                if (y % 3 == 0) {
                    world.spawnParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                }
            }
        }

    }

    /**
     * Spawn fire circle at 0.21s
     */
    private static void spawnFireCircle(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getEntityWorld();
        Vec3d midpoint = p1.getEntityPos().add(p2.getEntityPos()).multiply(0.5);

        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, midpoint.x, midpoint.y + 1, midpoint.z, 3, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.EXPLOSION, midpoint.x, midpoint.y + 1, midpoint.z, 20, 2.0, 2.0, 2.0, 0);

        int radius = 20;
        for (double angle = 0; angle < 360; angle += 2) {  // Every 2 degrees for smooth circle
            double rad = Math.toRadians(angle);
            double x = midpoint.x + radius * Math.cos(rad);
            double z = midpoint.z + radius * Math.sin(rad);

            world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y + 0.1, z, 20, 0.4, 1.2, 0.4, 0.05);
            world.spawnParticles(ParticleTypes.LAVA, x, midpoint.y + 0.1, z, 10, 0.3, 0.6, 0.3, 0.02);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, midpoint.y + 0.1, z, 15, 0.5, 1.8, 0.5, 0.1);

            if (angle % 20 == 0) {
                for (int h = 0; h < 10; h++) {  // 10 blocks tall pillars!
                    world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y + h, z, 25, 0.6, 0.6, 0.6, 0.12);
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, midpoint.y + h, z, 12, 0.4, 0.4, 0.4, 0.06);
                }
            }
        }

        for (int height = 0; height < 15; height++) {  // 15 blocks tall
            for (double angle = 0; angle < 360; angle += 10) {
                double rad = Math.toRadians(angle + height * 25);  // Spiral
                double distance = 3 + height * 0.4;  // Expands upward
                double x = midpoint.x + distance * Math.cos(rad);
                double z = midpoint.z + distance * Math.sin(rad);

                world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y + height, z, 5, 0.3, 0.3, 0.3, 0.04);
                world.spawnParticles(ParticleTypes.DRAGON_BREATH, x, midpoint.y + height, z, 3, 0.2, 0.2, 0.2, 0.02);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, midpoint.y + height, z, 2, 0.15, 0.15, 0.15, 0.01);
            }
        }

        for (int h = 0; h < 20; h++) {
            world.spawnParticles(ParticleTypes.FLAME, midpoint.x, midpoint.y + h, midpoint.z, 40, 2.0, 0.5, 2.0, 0.15);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, midpoint.x, midpoint.y + h, midpoint.z, 20, 1.5, 0.3, 1.5, 0.1);
            world.spawnParticles(ParticleTypes.LAVA, midpoint.x, midpoint.y + h, midpoint.z, 15, 1.0, 0.2, 1.0, 0.05);
        }

        Random random = new Random();
        int fireCount = 0;
        for (int i = 0; i < 300; i++) {  // Try 300 times to place fire
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 4 + random.nextDouble() * 16;  // 4 to 20 blocks (avoids center!)

            double x = midpoint.x + Math.cos(angle) * distance;
            double z = midpoint.z + Math.sin(angle) * distance;

            BlockPos pos = new BlockPos((int)x, (int)midpoint.y, (int)z);
            BlockPos above = pos.up();

            if (world.getBlockState(pos).isSolidBlock(world, pos) &&
                    world.getBlockState(above).isAir()) {
                world.setBlockState(above, net.minecraft.block.Blocks.FIRE.getDefaultState());
                fireCount++;

                world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y + 0.5, z, 20, 0.5, 1.0, 0.5, 0.08);
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, midpoint.y + 0.5, z, 10, 0.4, 0.8, 0.4, 0.06);
                world.spawnParticles(ParticleTypes.LAVA, x, midpoint.y + 0.1, z, 5, 0.3, 0.2, 0.3, 0.02);
            }
        }

        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 2.5f, 0.5f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 3.5f, 0.6f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 2.0f, 0.7f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 2.0f, 1.2f);

    }

   
    private static void executeFireArmImpact(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getEntityWorld();

        Vec3d midpoint;
        net.minecraft.entity.decoration.ArmorStandEntity stand = fireDapArmorStands.get(p1.getUuid());
        if (stand != null && !stand.isRemoved()) {
            midpoint = stand.getEntityPos();  // Exact hand-meet point tracked by armor stand!
            System.out.println("[Fire Dap] Using armor stand position: " + midpoint);
        } else {
            midpoint = p1.getEntityPos().add(p2.getEntityPos()).multiply(0.5).add(0, 1.2, 0);
            System.out.println("[Fire Dap] Warning: armor stand missing, using fallback position");
        }

      
        world.spawnParticles(ParticleTypes.FLAME, midpoint.x, midpoint.y, midpoint.z,
                3, 0.3, 0.3, 0.3, 0.1);  // Was 20 - DRASTICALLY REDUCED!
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, midpoint.x, midpoint.y, midpoint.z,
                2, 0.2, 0.2, 0.2, 0.08);  // Was 12 - DRASTICALLY REDUCED!

        Vec3d underArms = new Vec3d(midpoint.x, midpoint.y - 0.5, midpoint.z);
        world.spawnParticles(ParticleTypes.FLAME, underArms.x, underArms.y, underArms.z,
                8, 0.5, 0.2, 0.5, 0.12);
        world.spawnParticles(ParticleTypes.LAVA, underArms.x, underArms.y, underArms.z,
                4, 0.4, 0.15, 0.4, 0.05);

        // SHOCKWAVE - expanding ring of fire (reduced 50%)
        for (int ring = 1; ring <= 15; ring++) {
            double radius = ring * 1.5;
            for (double angle = 0; angle < 360; angle += 8) {
                double rad = Math.toRadians(angle);
                double x = midpoint.x + radius * Math.cos(rad);
                double z = midpoint.z + radius * Math.sin(rad);

                world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y, z, 3, 0.2, 0.5, 0.2, 0.05);  // Was 5
                world.spawnParticles(ParticleTypes.DRAGON_BREATH, x, midpoint.y, z, 2, 0.15, 0.3, 0.15, 0.03);  // Was 3
            }
        }

        // MASSIVE VERTICAL FIRE PILLAR - STARTS ABOVE PLAYERS' HEADS!
        double pillarStartY = midpoint.y + 2.0;  // Start 2 blocks ABOVE hands - players can see!
        for (int h = 0; h < 30; h++) {  // 30 blocks tall!
            // Dense center pillar ABOVE them
            world.spawnParticles(ParticleTypes.FLAME, midpoint.x, pillarStartY + h, midpoint.z,
                    25, 1.5, 0.5, 1.5, 0.15);  // Was 50
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, midpoint.x, pillarStartY + h, midpoint.z,
                    15, 1.2, 0.4, 1.2, 0.1);  // Was 30
            world.spawnParticles(ParticleTypes.LAVA, midpoint.x, pillarStartY + h, midpoint.z,
                    10, 1.0, 0.3, 1.0, 0.08);  // Was 20

            // Wide base smoke ABOVE them
            if (h < 5) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, midpoint.x, pillarStartY + h, midpoint.z,
                        20, 2.0, 0.5, 2.0, 0.12);  // Was 40
            }
        }

        for (double angle = 0; angle < 360; angle += 3) {  // Every 3 degrees for smooth circle
            double rad = Math.toRadians(angle);
            double radius = 3.0;

            double x = midpoint.x + Math.cos(rad) * radius;
            double z = midpoint.z + Math.sin(rad) * radius;

            // Dense particle ring at impact height
            world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y, z, 5, 0.1, 0.1, 0.1, 0.08);  // Was 10
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, midpoint.y, z, 3, 0.1, 0.1, 0.1, 0.05);  // Was 6
            world.spawnParticles(ParticleTypes.END_ROD, x, midpoint.y, z, 1, 0, 0, 0, 0);  // Was 2

            // Vertical beam from circle (reduced 50%)
            if (angle % 15 == 0) {
                for (int h = 0; h < 10; h++) {
                    world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y + h, z, 2, 0.05, 0.05, 0.05, 0.02);  // Was 3
                }
            }
        }

        // LAUNCH ALL ENTITIES AWAY - 30 block radius (except the two players!)
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        Box searchBox = new Box(
                midpoint.x - 30, midpoint.y - 30, midpoint.z - 30,
                midpoint.x + 30, midpoint.y + 30, midpoint.z + 30
        );

        for (Entity entity : world.getOtherEntities(null, searchBox)) {
            // Skip the two players who dapped
            if (entity.getUuid().equals(id1) || entity.getUuid().equals(id2)) {
                continue;
            }

            Vec3d entityPos = entity.getEntityPos();
            double distance = entityPos.distanceTo(midpoint);

            if (distance < 30 && distance > 0.1) {
                // Calculate knockback direction (away from impact)
                Vec3d direction = entityPos.subtract(midpoint).normalize();

                // Stronger knockback the closer they are
                double strength = (30 - distance) / 30.0 * 3.0;  // Up to 3.0 strength

                entity.setVelocity(
                        direction.x * strength,
                        0.8 + (strength * 0.5),  // Upward component
                        direction.z * strength
                );
                entity.velocityDirty = true;

                // Damage based on distance
                if (entity instanceof net.minecraft.entity.LivingEntity living) {
                    float damage = (float)((30 - distance) / 30.0 * 20.0);  // Up to 20 damage
                    living.damage(living.getDamageSources().explosion(null, null), damage);
                }
            }
        }

        // GALACTIC_DAP sound
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                ModSounds.GALACTIC_DAP, SoundCategory.PLAYERS, 3.0f, 1.0f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.5f, 0.8f);

    }

    /**
     * Tornado at 1.46s
     */
    private static void spawnFireTornado(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getEntityWorld();
        Vec3d midpoint = p1.getEntityPos().add(p2.getEntityPos()).multiply(0.5);


        // Start the continuous tornado system!
        tornadoStartTime = System.currentTimeMillis();
        tornadoActive = true;
        tornadoCenter = midpoint;
        tornadoWorld = world;
        activeTornadoSwirls.clear();

        // Play epic tornado sounds
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 4.0f, 0.4f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 3.0f, 0.6f);
    }

    /**
     * TP facing each other at 1.0 blocks
     */
    private static void teleportFireDapFacingEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2, double targetDistance) {
        Vec3d p1Pos = p1.getEntityPos();
        Vec3d p2Pos = p2.getEntityPos();
        double distance = p1Pos.distanceTo(p2Pos);

        // Always keep them exactly 1.2 blocks apart and facing each other
        Vec3d direction = p2Pos.subtract(p1Pos).normalize();
        Vec3d midpoint = p1Pos.add(p2Pos).multiply(0.5);

        // Calculate yaw so they face each other
        double dx = p2Pos.x - p1Pos.x;
        double dz = p2Pos.z - p1Pos.z;
        float yawP1 = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        float yawP2 = yawP1 + 180;

        // Only TP if distance is off by more than 0.05 blocks (smoother, less jittery)
        if (Math.abs(distance - targetDistance) > 0.05) {
            Vec3d offset = direction.multiply(targetDistance / 2.0);
            Vec3d targetP1 = midpoint.subtract(offset);
            Vec3d targetP2 = midpoint.add(offset);

            p1.teleport(p1.getEntityWorld(), targetP1.x, targetP1.y, targetP1.z, yawP1, 0.0f);
            p2.teleport(p2.getEntityWorld(), targetP2.x, targetP2.y, targetP2.z, yawP2, 0.0f);

            // Force body rotation to match head (so hands meet properly!)
            p1.setYaw(yawP1);
            p1.setBodyYaw(yawP1);
            p1.setHeadYaw(yawP1);
            p2.setYaw(yawP2);
            p2.setBodyYaw(yawP2);
            p2.setHeadYaw(yawP2);
        } else {
            // Distance is correct, just update rotation to keep facing each other
            p1.teleport(p1.getEntityWorld(), p1Pos.x, p1Pos.y, p1Pos.z, yawP1, 0.0f);
            p2.teleport(p2.getEntityWorld(), p2Pos.x, p2Pos.y, p2Pos.z, yawP2, 0.0f);

            // Force body rotation
            p1.setYaw(yawP1);
            p1.setBodyYaw(yawP1);
            p1.setHeadYaw(yawP1);
            p2.setYaw(yawP2);
            p2.setBodyYaw(yawP2);
            p2.setHeadYaw(yawP2);
        }
    }

    /**
     * Smooth TP for perfect dap - keeps players facing each other at target distance
     */
    private static void teleportPerfectDapFacingEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2, double targetDistance) {
        Vec3d p1Pos = p1.getEntityPos();
        Vec3d p2Pos = p2.getEntityPos();
        double distance = p1Pos.distanceTo(p2Pos);

        // Keep them exactly at target distance apart and facing each other
        Vec3d direction = p2Pos.subtract(p1Pos).normalize();
        Vec3d midpoint = p1Pos.add(p2Pos).multiply(0.5);

        // Calculate yaw so they face each other
        double dx = p2Pos.x - p1Pos.x;
        double dz = p2Pos.z - p1Pos.z;
        float yawP1 = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        float yawP2 = yawP1 + 180;

        // Only TP if distance is off by more than 0.05 blocks (smoother)
        if (Math.abs(distance - targetDistance) > 0.05) {
            Vec3d offset = direction.multiply(targetDistance / 2.0);
            Vec3d targetP1 = midpoint.subtract(offset);
            Vec3d targetP2 = midpoint.add(offset);

            p1.teleport(p1.getEntityWorld(), targetP1.x, targetP1.y, targetP1.z, yawP1, 0.0f);
            p2.teleport(p2.getEntityWorld(), targetP2.x, targetP2.y, targetP2.z, yawP2, 0.0f);

            // Force body rotation
            p1.setYaw(yawP1);
            p1.setBodyYaw(yawP1);
            p1.setHeadYaw(yawP1);
            p2.setYaw(yawP2);
            p2.setBodyYaw(yawP2);
            p2.setHeadYaw(yawP2);
        } else {
            // Distance correct, just update rotation
            p1.teleport(p1.getEntityWorld(), p1Pos.x, p1Pos.y, p1Pos.z, yawP1, 0.0f);
            p2.teleport(p2.getEntityWorld(), p2Pos.x, p2Pos.y, p2Pos.z, yawP2, 0.0f);

            // Force body rotation
            p1.setYaw(yawP1);
            p1.setBodyYaw(yawP1);
            p1.setHeadYaw(yawP1);
            p2.setYaw(yawP2);
            p2.setBodyYaw(yawP2);
            p2.setHeadYaw(yawP2);
        }
    }

   
    private static void smoothDapDescent(ServerPlayerEntity player, net.minecraft.entity.decoration.ArmorStandEntity stand) {
        if (stand == null || stand.isRemoved()) return;

        // SKIP if player in fire combo - fire dap handles positioning!
        if (fireComboActive.getOrDefault(player.getUuid(), false)) {
            return;  // Fire combo is active - don't interfere!
        }

        ServerWorld world = player.getEntityWorld();
        Vec3d playerPos = player.getEntityPos();
        Vec3d standPos = stand.getEntityPos();

        // DISTANCE CHECK: If armor stand too close to player, move it back
        double distanceToPlayer = standPos.distanceTo(playerPos);
        if (distanceToPlayer < 0.8) {  // Too close! (less than 0.8 blocks)
            // Calculate direction from player to stand
            Vec3d direction = standPos.subtract(playerPos).normalize();

            // Move stand to  distance (1.2 blocks from player)
            Vec3d newStandPos = playerPos.add(direction.multiply(1.2));
            stand.setPosition(newStandPos.x, newStandPos.y, newStandPos.z);

            System.out.println("[Smooth Dap] Armor stand too close (" + String.format("%.2f", distanceToPlayer) +
                    " blocks), moved to 1.2 blocks");
            return;  // Don't do descent this tick, just reposition
        }

      
        BlockPos feetPos = BlockPos.ofFloored(playerPos.x, playerPos.y - 0.1, playerPos.z);  // Just below feet
        boolean solidBlockBelow = !world.getBlockState(feetPos).isAir() &&
                world.getBlockState(feetPos).isSolidBlock(world, feetPos);

        if (solidBlockBelow) {
            // Player has solid block directly under feet - DON'T MOVE THEM!
            return;
        }

        // SMOOTH DESCENT: Player is in AIR - find ground below
        BlockPos checkPos = BlockPos.ofFloored(playerPos.x, playerPos.y - 1.0, playerPos.z);
        int blocksChecked = 0;

        // Look down up to 10 blocks for solid ground
        while (blocksChecked < 10) {
            if (!world.getBlockState(checkPos).isAir() &&
                    world.getBlockState(checkPos).isSolidBlock(world, checkPos)) {
                break;  // Found solid ground!
            }
            checkPos = checkPos.down();
            blocksChecked++;
        }

        // Calculate ground level (top of solid block)
        double groundY = checkPos.getY() + 1.0;

        // TY: Don't descend if already at or below ground
        if (playerPos.y <= groundY + 0.1) {  // Within 0.1 blocks of ground
            return;  // Too close to ground, don't move
        }

        double newY = playerPos.y - 0.12;  // Slower descent (was 0.15)

        // NEVER go below ground!
        newY = Math.max(newY, groundY);

        // Only set position if actually changing
        if (newY < playerPos.y && newY >= groundY) {
            player.setPosition(playerPos.x, newY, playerPos.z);

            // Update armor stand (maintain 1.4 block offset)
            stand.setPosition(standPos.x, newY + 1.4, standPos.z);

            System.out.println("[Smooth Dap] Descending airborne player: " +
                    String.format("%.2f", playerPos.y) + " → " + String.format("%.2f", newY) +
                    " (ground at " + String.format("%.2f", groundY) + ")");
        }
    }
}
