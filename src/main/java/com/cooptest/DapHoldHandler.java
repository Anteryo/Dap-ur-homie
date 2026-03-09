package com.cooptest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class DapHoldHandler {

    private static final long ANIM_LENGTH_MS    = 1042;
    private static final long J_WINDOW_START_MS = 330;
    private static final long IMPACT_MS         = 420;   
    private static final long J_WINDOW_END_MS   = 1330;  
    private static final double STOP_DISTANCE   = 1.5;   
    private static final double TP_SPEED        = 0.08;

    public record DapHoldStartPayload(UUID playerId, UUID partnerId, int role) implements CustomPayload {
        public static final Id<DapHoldStartPayload> ID = new Id<>(Identifier.of("cooptest", "daphold_start"));
        public static final PacketCodec<PacketByteBuf, DapHoldStartPayload> CODEC = PacketCodec.of(
                (p, buf) -> { buf.writeUuid(p.playerId()); buf.writeUuid(p.partnerId()); buf.writeInt(p.role()); },
                buf -> new DapHoldStartPayload(buf.readUuid(), buf.readUuid(), buf.readInt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record DapHoldWindowPayload(boolean open) implements CustomPayload {
        public static final Id<DapHoldWindowPayload> ID = new Id<>(Identifier.of("cooptest", "daphold_window"));
        public static final PacketCodec<PacketByteBuf, DapHoldWindowPayload> CODEC = PacketCodec.of(
                (p, buf) -> buf.writeBoolean(p.open()), buf -> new DapHoldWindowPayload(buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record DapHoldLoopPayload(boolean looping) implements CustomPayload {
        public static final Id<DapHoldLoopPayload> ID = new Id<>(Identifier.of("cooptest", "daphold_loop"));
        public static final PacketCodec<PacketByteBuf, DapHoldLoopPayload> CODEC = PacketCodec.of(
                (p, buf) -> buf.writeBoolean(p.looping()), buf -> new DapHoldLoopPayload(buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record DapHoldEndPayload(boolean wasLooping) implements CustomPayload {
        public static final Id<DapHoldEndPayload> ID = new Id<>(Identifier.of("cooptest", "daphold_end"));
        public static final PacketCodec<PacketByteBuf, DapHoldEndPayload> CODEC = PacketCodec.of(
                (p, buf) -> buf.writeBoolean(p.wasLooping()), buf -> new DapHoldEndPayload(buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record DapHoldFreezePayload(UUID playerId, boolean frozen) implements CustomPayload {
        public static final Id<DapHoldFreezePayload> ID = new Id<>(Identifier.of("cooptest", "daphold_freeze"));
        public static final PacketCodec<PacketByteBuf, DapHoldFreezePayload> CODEC = PacketCodec.of(
                (p, buf) -> { buf.writeUuid(p.playerId()); buf.writeBoolean(p.frozen()); },
                buf -> new DapHoldFreezePayload(buf.readUuid(), buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    // C→S payloads
    public record DapHoldJHoldPayload() implements CustomPayload {
        public static final Id<DapHoldJHoldPayload> ID = new Id<>(Identifier.of("cooptest", "daphold_jhold"));
        public static final PacketCodec<PacketByteBuf, DapHoldJHoldPayload> CODEC = PacketCodec.unit(new DapHoldJHoldPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record DapHoldJReleasePayload() implements CustomPayload {
        public static final Id<DapHoldJReleasePayload> ID = new Id<>(Identifier.of("cooptest", "daphold_jrelease"));
        public static final PacketCodec<PacketByteBuf, DapHoldJReleasePayload> CODEC = PacketCodec.unit(new DapHoldJReleasePayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private static final Map<UUID, UUID> activePairs   = new HashMap<>();
    private static final Map<UUID, Long> pairStartTime = new HashMap<>();
    private static final Set<UUID> windowOpen          = new HashSet<>();
    private static final Set<UUID> impactFired         = new HashSet<>();
    private static final Set<UUID> looping             = new HashSet<>();
    private static final Set<UUID> endingAnimation     = new HashSet<>();
    private static final Map<UUID, Long> jHoldLastTick = new HashMap<>();
    private static final Map<UUID, Long> loopStartTime = new HashMap<>();  
    private static final Map<UUID, ArmorStandEntity> handStands = new HashMap<>();
    private static final Set<UUID> tpComplete          = new HashSet<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(DapHoldStartPayload.ID,    DapHoldStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldWindowPayload.ID,   DapHoldWindowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldLoopPayload.ID,     DapHoldLoopPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldEndPayload.ID,      DapHoldEndPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldFreezePayload.ID,   DapHoldFreezePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DapHoldJHoldPayload.ID,    DapHoldJHoldPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DapHoldJReleasePayload.ID, DapHoldJReleasePayload.CODEC);
    }

    public static void register() {
        registerPayloads();
        ServerPlayNetworking.registerGlobalReceiver(DapHoldJHoldPayload.ID,
                (payload, ctx) -> ctx.server().execute(() -> onJHold(ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(DapHoldJReleasePayload.ID,
                (payload, ctx) -> ctx.server().execute(() -> onJRelease(ctx.player())));
        ServerTickEvents.END_SERVER_TICK.register(DapHoldHandler::onServerTick);
        System.out.println("[DapHold] Registered!");
    }


    private static void makeFaceEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        Vec3d p1Pos = p1.getPos();
        Vec3d p2Pos = p2.getPos();

        p1.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        p2.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        System.out.println("[DapHold]  Left click swing - body rotation synced!");

        double dx = p2Pos.x - p1Pos.x;
        double dz = p2Pos.z - p1Pos.z;
        float yawP1 = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        float yawP2 = yawP1 + 180;

        p1.setYaw(yawP1);
        p1.setBodyYaw(yawP1);
        p1.setHeadYaw(yawP1);
        p1.teleport(p1.getServerWorld(), p1Pos.x, p1Pos.y, p1Pos.z, yawP1, 0.0f);

        p2.setYaw(yawP2);
        p2.setBodyYaw(yawP2);
        p2.setHeadYaw(yawP2);
        p2.teleport(p2.getServerWorld(), p2Pos.x, p2Pos.y, p2Pos.z, yawP2, 0.0f);
    }



    private static boolean arePlayersFacingEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        net.minecraft.util.math.Vec3d p1Pos = p1.getPos();
        net.minecraft.util.math.Vec3d p2Pos = p2.getPos();

        net.minecraft.util.math.Vec3d directionTo = p2Pos.subtract(p1Pos).normalize();
        net.minecraft.util.math.Vec3d p1Looking = p1.getRotationVector();
        double dot1 = p1Looking.dotProduct(directionTo);
        if (dot1 < 0.85) return false;  

        net.minecraft.util.math.Vec3d directionBack = p1Pos.subtract(p2Pos).normalize();
        net.minecraft.util.math.Vec3d p2Looking = p2.getRotationVector();
        double dot2 = p2Looking.dotProduct(directionBack);
        return dot2 >= 0.85;  
    }

    public static void startDapHold(ServerPlayerEntity hfPlayer, ServerPlayerEntity dapPlayer) {
        UUID hfId = hfPlayer.getUuid();
        UUID dapId = dapPlayer.getUuid();
        if (isInDapHold(hfId) || isInDapHold(dapId)) return;

        if (!arePlayersFacingEachOther(hfPlayer, dapPlayer)) {
            hfPlayer.sendMessage(net.minecraft.text.Text.literal("§cNot facing each other!"), true);
            dapPlayer.sendMessage(net.minecraft.text.Text.literal("§cNot facing each other!"), true);
            System.out.println("[DapHold]  FAILED - Players not facing each other!");
            return;  
        }

        System.out.println("[DapHold]  Facing check passed! START! HF=" + hfPlayer.getName().getString() + " DAP=" + dapPlayer.getName().getString());

        HighFiveHandler.handRaisedTime.remove(hfId);
        HighFiveHandler.startAnimTime.remove(hfId);
        HighFiveHandler.syncHandRaised(hfPlayer, false);
        System.out.println("[DapHold] Removed HF player from HighFiveHandler control");

        com.cooptest.DapSession session = com.cooptest.DapSessionManager.createSession(
                hfId, dapId,
                1.5,  
                com.cooptest.DapSession.DapType.PERFECT_DAP  
        );


        activePairs.put(hfId, dapId);
        pairStartTime.put(hfId, System.currentTimeMillis());

        sendFreeze(hfPlayer.getServer(), hfId,  true);
        sendFreeze(hfPlayer.getServer(), dapId, true);
        System.out.println("[DapHold] Sent freeze to both players");

        spawnHandStand(hfPlayer, dapPlayer);

        System.out.println("[DapHold] Sending DapHoldStartPayload:");
        System.out.println("  - HF player (" + hfPlayer.getName().getString() + "): role=0 (highfive_dap)");
        System.out.println("  - DAP player (" + dapPlayer.getName().getString() + "): role=1 (dap_high)");

        sendToAll(hfPlayer.getServer(), new DapHoldStartPayload(hfId,  dapId, 0));
        sendToAll(hfPlayer.getServer(), new DapHoldStartPayload(dapId, hfId,  1));

        PoseNetworking.broadcastAnimState(hfPlayer, 38);  // DAPHOLD_HIGHFIVE (highfive_dap)
        PoseNetworking.broadcastAnimState(dapPlayer, 39); // DAPHOLD_DAP (dap_high)

    }

    private static void onServerTick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Set<UUID> toCleanup = new HashSet<>();

        for (Map.Entry<UUID, UUID> entry : activePairs.entrySet()) {
            UUID hfId  = entry.getKey();
            UUID dapId = entry.getValue();

            ServerPlayerEntity hfPlayer  = server.getPlayerManager().getPlayer(hfId);
            ServerPlayerEntity dapPlayer = server.getPlayerManager().getPlayer(dapId);

            if (hfPlayer == null || dapPlayer == null) { toCleanup.add(hfId); continue; }

            Long startMs = pairStartTime.get(hfId);
            if (startMs == null) { toCleanup.add(hfId); continue; }
            long elapsed = now - startMs;

            if (!tpComplete.contains(hfId)) {

                tpComplete.add(hfId);
            }
            updateHandStand(hfPlayer, dapPlayer, hfId);


            if (elapsed % 500 < 50) {  // Every 500ms
                hfPlayer.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                dapPlayer.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            }

            if (!windowOpen.contains(hfId) && elapsed >= J_WINDOW_START_MS) {
                windowOpen.add(hfId);
                sendToAll(server, new DapHoldWindowPayload(true));

                hfPlayer.sendMessage(net.minecraft.text.Text.literal("§e HOLD J "), true);
                dapPlayer.sendMessage(net.minecraft.text.Text.literal("§e HOLD J "), true);
            }

            if (!impactFired.contains(hfId) && elapsed >= IMPACT_MS) {
                impactFired.add(hfId);
                spawnImpactParticles(hfPlayer, dapPlayer, hfId);
            }

            if (windowOpen.contains(hfId) && !looping.contains(hfId)
                    && !endingAnimation.contains(hfId) && elapsed >= J_WINDOW_END_MS) {

                if (isHoldingJ(hfId, now) && isHoldingJ(dapId, now)) {
                    looping.add(hfId);
                    loopStartTime.put(hfId, now);

                    com.cooptest.DapSessionManager.removeSession(hfId);

                    sendToAll(server, new DapHoldLoopPayload(true));


                    if (hfPlayer != null && dapPlayer != null) {
                        PoseNetworking.broadcastAnimState(hfPlayer, 40);  // DAPHOLD_DAPPING
                        PoseNetworking.broadcastAnimState(dapPlayer, 40); // DAPHOLD_DAPPING
                    }

                    System.out.println("[DapHold] BOTH HELD J → DAPPING LOOP!");
                } else {
                    endingAnimation.add(hfId);
                    sendToAll(server, new DapHoldWindowPayload(false));

                    doUnfreeze(server, hfId, dapId);
                }
            }

            if (endingAnimation.contains(hfId) && elapsed >= ANIM_LENGTH_MS) {
                sendToAll(server, new DapHoldEndPayload(false));
                toCleanup.add(hfId);
            }
        }

        for (UUID hfId : looping) {
            ServerPlayerEntity hfPlayer = server.getPlayerManager().getPlayer(hfId);
            UUID dapId = activePairs.get(hfId);
            ServerPlayerEntity dapPlayer = server.getPlayerManager().getPlayer(dapId);

            if (hfPlayer == null || dapPlayer == null) continue;

            ServerWorld world = hfPlayer.getServerWorld();
            ArmorStandEntity stand = handStands.get(hfId);
            Long loopStart = loopStartTime.get(hfId);

            if (stand != null && !stand.isRemoved()) {
                Vec3d impactPos = stand.getPos();

                world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,
                        impactPos.x, impactPos.y, impactPos.z,
                        2, 0.1, 0.1, 0.1, 0.02);
            }
        }

        toCleanup.forEach(hfId -> cleanupPair(hfId, server));
    }

    private static void onJHold(ServerPlayerEntity player) {
        UUID hfId = getPairHfId(player.getUuid());
        if (hfId == null || !windowOpen.contains(hfId)) return;
        jHoldLastTick.put(player.getUuid(), System.currentTimeMillis());
    }

    private static void onJRelease(ServerPlayerEntity player) {
        jHoldLastTick.remove(player.getUuid());
        UUID hfId = getPairHfId(player.getUuid());
        if (hfId == null || !looping.contains(hfId)) return;

        UUID dapId = activePairs.get(hfId);
        MinecraftServer server = player.getServer();
        if (server == null) return;

        System.out.println("[DapHold] J RELEASED during loop - playing dapping_end!");

        looping.remove(hfId);
        loopStartTime.remove(hfId);

        doUnfreeze(server, hfId, dapId);
        System.out.println("[DapHold]  UNFROZEN immediately!");

        sendToAll(server, new DapHoldEndPayload(true));

        ServerPlayerEntity hfPlayer = server.getPlayerManager().getPlayer(hfId);
        ServerPlayerEntity dapPlayer = server.getPlayerManager().getPlayer(dapId);
        if (hfPlayer != null && dapPlayer != null) {
            PoseNetworking.broadcastAnimState(hfPlayer, 41);  // DAPHOLD_DAPPING_END
            PoseNetworking.broadcastAnimState(dapPlayer, 41); // DAPHOLD_DAPPING_END
        }

        long dappingEndLength = 1042;
        pairStartTime.put(hfId, System.currentTimeMillis() + 100 - dappingEndLength);
    }

    private static UUID getPairHfId(UUID id) {
        if (activePairs.containsKey(id)) return id;
        for (Map.Entry<UUID, UUID> e : activePairs.entrySet())
            if (e.getValue().equals(id)) return e.getKey();
        return null;
    }

    private static boolean isHoldingJ(UUID id, long now) {
        Long last = jHoldLastTick.get(id);
        return last != null && (now - last) < 200;
    }

    private static void smoothTP(ServerPlayerEntity hf, ServerPlayerEntity dap, UUID hfId) {
        double dist = hf.getPos().distanceTo(dap.getPos());
        if (dist <= STOP_DISTANCE) {
            tpComplete.add(hfId);
            faceEachOther(hf, dap);
            return;
        }
        double move = Math.min(TP_SPEED, (dist - STOP_DISTANCE) / 2.0);
        Vec3d dir   = dap.getPos().subtract(hf.getPos()).normalize();
        Vec3d newHf  = hf.getPos().add(dir.multiply(move));
        Vec3d newDap = dap.getPos().add(dir.negate().multiply(move));

        hf.teleport(hf.getServerWorld(),   newHf.x,  newHf.y,  newHf.z,  hf.getYaw(),  hf.getPitch());
        dap.teleport(dap.getServerWorld(), newDap.x, newDap.y, newDap.z, dap.getYaw(), dap.getPitch());
    }

    private static void faceEachOther(ServerPlayerEntity a, ServerPlayerEntity b) {
        Vec3d diff = b.getPos().subtract(a.getPos());
        float yawA = (float)(Math.toDegrees(Math.atan2(diff.z, diff.x))) - 90f;
        a.teleport(a.getServerWorld(), a.getX(), a.getY(), a.getZ(), yawA,        a.getPitch());
        b.teleport(b.getServerWorld(), b.getX(), b.getY(), b.getZ(), yawA + 180f, b.getPitch());
    }

    private static void spawnHandStand(ServerPlayerEntity hf, ServerPlayerEntity dap) {
        ServerWorld world = hf.getServerWorld();
        Vec3d mid = hf.getPos().add(0, 1.4, 0).add(dap.getPos().add(0, 1.4, 0)).multiply(0.5);

        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        stand.setPosition(mid.x, mid.y, mid.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);

        world.spawnEntity(stand);
        handStands.put(hf.getUuid(), stand);
    }

    private static void updateHandStand(ServerPlayerEntity hf, ServerPlayerEntity dap, UUID hfId) {
        ArmorStandEntity stand = handStands.get(hfId);
        if (stand == null || stand.isRemoved()) return;
        Vec3d mid = hf.getPos().add(0, 1.4, 0).add(dap.getPos().add(0, 1.4, 0)).multiply(0.5);
        stand.setPosition(mid.x, mid.y, mid.z);
    }

    private static void spawnImpactParticles(ServerPlayerEntity hf, ServerPlayerEntity dap, UUID hfId) {
        ServerWorld world = hf.getServerWorld();
        ArmorStandEntity stand = handStands.get(hfId);
        double x, y, z;
        if (stand != null && !stand.isRemoved()) {
            x = stand.getX(); y = stand.getY(); z = stand.getZ();
        } else {
            Vec3d mid = hf.getPos().add(dap.getPos()).multiply(0.5).add(0, 1.4, 0);
            x = mid.x; y = mid.y; z = mid.z;
        }

        world.spawnParticles(ParticleTypes.FLASH,     x, y, z, 3,  0,   0,   0,   0);
        world.spawnParticles(ParticleTypes.END_ROD,   x, y, z, 40, 0.4, 0.4, 0.4, 0.15);
        world.spawnParticles(ParticleTypes.WHITE_ASH, x, y, z, 80, 0.6, 0.6, 0.6, 0.08);
        world.spawnParticles(ParticleTypes.CLOUD,     x, y, z, 20, 0.3, 0.3, 0.3, 0.05);
        world.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 5,  0.3, 0.3, 0.3, 0);

        double groundY = hf.getY() + 0.1;
        for (double angle = 0; angle < 360; angle += 8) {
            double rad = Math.toRadians(angle);
            for (double r = 0.5; r <= 3.0; r += 0.5) {
                world.spawnParticles(ParticleTypes.END_ROD,
                        x + Math.cos(rad) * r, groundY, z + Math.sin(rad) * r,
                        2, 0.05, 0.05, 0.05, 0.02);
            }
        }
        world.playSound(null, x, y, z, ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private static void sendFreeze(MinecraftServer server, UUID targetId, boolean freeze) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
            ServerPlayNetworking.send(p, new DapHoldFreezePayload(targetId, freeze));
    }

    private static void doUnfreeze(MinecraftServer server, UUID hfId, UUID dapId) {
        sendFreeze(server, hfId, false);
        sendFreeze(server, dapId, false);

        com.cooptest.DapSessionManager.removeSession(hfId);  // Only needs hfId!

        ArmorStandEntity stand = handStands.remove(hfId);
        if (stand != null && !stand.isRemoved()) stand.discard();
    }

    private static void sendToAll(MinecraftServer server, CustomPayload payload) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
            ServerPlayNetworking.send(p, payload);
    }

    private static void cleanupPair(UUID hfId, MinecraftServer server) {
        UUID dapId = activePairs.remove(hfId);
        pairStartTime.remove(hfId); windowOpen.remove(hfId); impactFired.remove(hfId);
        looping.remove(hfId); endingAnimation.remove(hfId); tpComplete.remove(hfId);
        loopStartTime.remove(hfId);  // Clean up loop start time
        jHoldLastTick.remove(hfId);
        if (dapId != null) jHoldLastTick.remove(dapId);
        ArmorStandEntity stand = handStands.remove(hfId);
        if (stand != null && !stand.isRemoved()) stand.discard();

        com.cooptest.DapSessionManager.removeSession(hfId);  // Only needs hfId!

        sendFreeze(server, hfId, false);
        if (dapId != null) sendFreeze(server, dapId, false);

        long now = System.currentTimeMillis();

        ChargedDapHandler.cooldowns.put(hfId, now + 1000);  // 1 second cooldown
        if (dapId != null) {
            ChargedDapHandler.cooldowns.put(dapId, now + 1000);
        }

        HighFiveHandler.highFiveCooldown.put(hfId, now);
        if (dapId != null) {
            HighFiveHandler.highFiveCooldown.put(dapId, now);
        }

        System.out.println("[DapHold] Cleaned up: " + hfId + " (1s cooldown applied)");
    }

    public static boolean tryDetect(ServerPlayerEntity player, ServerPlayerEntity partner) {
        boolean playerHF  = HighFiveHandler.hasHandRaised(player.getUuid());
        boolean partnerHF = HighFiveHandler.hasHandRaised(partner.getUuid());
        if (playerHF && !partnerHF)  { startDapHold(player,  partner); return true; }
        if (partnerHF && !playerHF)  { startDapHold(partner, player);  return true; }
        return false;
    }

    public static boolean isInDapHold(UUID playerId) {
        return activePairs.containsKey(playerId) || activePairs.containsValue(playerId);
    }
}
