package com.cooptest;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;


public class FallCatchHandler {
    public static final double CATCH_RANGE_HORIZONTAL = 3.0;  
    public static final double CATCH_RANGE_VERTICAL = 4.0;   
    public static final long CATCH_WINDOW_MS = 500;          
    public static final long CATCH_COOLDOWN_MS = 1000;        

    private static final Map<UUID, Long> catchReadyTime = new HashMap<>();
    private static final Map<UUID, Long> catchCooldowns = new HashMap<>();
    private static final Map<UUID, Boolean> successfulCatch = new HashMap<>();
    public static final Identifier CATCH_ANIM_ID = Identifier.of("cooptest", "catch_anim");
    public record CatchAnimPayload(UUID catcherId, UUID caughtId) implements CustomPayload {
        public static final Id<CatchAnimPayload> ID = new Id<>(CATCH_ANIM_ID);
        public static final PacketCodec<PacketByteBuf, CatchAnimPayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> {
                            buf.writeUuid(payload.catcherId);
                            buf.writeUuid(payload.caughtId);
                        },
                        buf -> new CatchAnimPayload(buf.readUuid(), buf.readUuid())
                );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(CatchAnimPayload.ID, CatchAnimPayload.CODEC);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();

            Iterator<Map.Entry<UUID, Long>> it = catchReadyTime.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                UUID playerId = entry.getKey();
                long readyTime = entry.getValue();

                PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
                if (pose != PoseState.GRAB_READY) {
                    if (!successfulCatch.getOrDefault(playerId, false)) {
                        catchCooldowns.put(playerId, now + CATCH_COOLDOWN_MS);

                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                        if (player != null) {
                            player.sendMessage(net.minecraft.text.Text.literal("§c✗ Catch missed! 1 sec cooldown"), true);
                        }
                    }
                    it.remove();
                    successfulCatch.remove(playerId);
                }
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID playerId = player.getUuid();
                PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);

                if (pose == PoseState.GRAB_READY && !catchReadyTime.containsKey(playerId)) {
                    catchReadyTime.put(playerId, now);
                    successfulCatch.put(playerId, false);
                }
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity fallingPlayer)) return true;
            if (!source.isOf(DamageTypes.FALL)) return true;

            ServerPlayerEntity catcher = findCatcher(fallingPlayer);
            if (catcher != null) {
                playCatchEffects(fallingPlayer, catcher);

                successfulCatch.put(catcher.getUuid(), true);

                return false; 
            }

            return true; 
        });
    }

   
    public static boolean isInCatchReadyMode(UUID playerId) {
        PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
        return pose == PoseState.GRAB_READY;
    }

    /**
     * Check if player is on catch cooldown
     */
    public static boolean isOnCatchCooldown(UUID playerId) {
        Long cooldownEnd = catchCooldowns.get(playerId);
        if (cooldownEnd == null) return false;
        if (System.currentTimeMillis() >= cooldownEnd) {
            catchCooldowns.remove(playerId);
            return false;
        }
        return true;
    }

  
    public static boolean canEnterCatchReady(UUID playerId) {
        return !isOnCatchCooldown(playerId);
    }

    private static ServerPlayerEntity findCatcher(ServerPlayerEntity fallingPlayer) {
        ServerWorld world = fallingPlayer.getServerWorld();
        long now = System.currentTimeMillis();

        // Search in a generous area around the falling player
        Box searchBox = new Box(
                fallingPlayer.getX() - CATCH_RANGE_HORIZONTAL,
                fallingPlayer.getY() - 2, // Below
                fallingPlayer.getZ() - CATCH_RANGE_HORIZONTAL,
                fallingPlayer.getX() + CATCH_RANGE_HORIZONTAL,
                fallingPlayer.getY() + 1, // Slightly above
                fallingPlayer.getZ() + CATCH_RANGE_HORIZONTAL
        );

        for (PlayerEntity player : world.getPlayers()) {
            if (player == fallingPlayer) continue;
            if (!(player instanceof ServerPlayerEntity catcher)) continue;

            UUID catcherId = catcher.getUuid();

            // Check if player is in GRAB_READY pose
            PoseState pose = PoseNetworking.poseStates.getOrDefault(catcherId, PoseState.NONE);
            if (pose != PoseState.GRAB_READY) continue;

            if (isOnCatchCooldown(catcherId)) continue;

            Long readyTime = catchReadyTime.get(catcherId);
            if (readyTime == null) continue;

            if (!searchBox.contains(catcher.getPos())) continue;

            double dx = fallingPlayer.getX() - catcher.getX();
            double dz = fallingPlayer.getZ() - catcher.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            if (horizontalDist <= CATCH_RANGE_HORIZONTAL) {
                return catcher;
            }
        }

        return null;
    }

    private static void playCatchEffects(ServerPlayerEntity caught, ServerPlayerEntity catcher) {
        ServerWorld world = catcher.getServerWorld();
        double x = (caught.getX() + catcher.getX()) / 2;
        double y = catcher.getY() + 1.5;
        double z = (caught.getZ() + catcher.getZ()) / 2;

        world.playSound(null, x, y, z,
                SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.2f, 0.7f);
        world.playSound(null, x, y, z,
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
        world.playSound(null, x, y, z,
                SoundEvents.BLOCK_WOOL_FALL, SoundCategory.PLAYERS, 1.0f, 0.8f);

        world.spawnParticles(ParticleTypes.CLOUD,
                x, y, z, 12, 0.4, 0.3, 0.4, 0.02);
        world.spawnParticles(ParticleTypes.CRIT,
                x, y, z, 8, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticles(ParticleTypes.WAX_ON,
                x, y, z, 6, 0.2, 0.2, 0.2, 0.02);

        caught.setVelocity(0, 0, 0);
        caught.velocityModified = true;

        CatchAnimPayload payload = new CatchAnimPayload(catcher.getUuid(), caught.getUuid());
        for (ServerPlayerEntity nearby : PlayerLookup.tracking(catcher)) {
            ServerPlayNetworking.send(nearby, payload);
        }
        ServerPlayNetworking.send(catcher, payload);
        ServerPlayNetworking.send(caught, payload);

        PoseNetworking.poseStates.put(catcher.getUuid(), PoseState.NONE);
        PoseNetworking.broadcastPoseChange(catcher.getServer(), catcher.getUuid(), PoseState.NONE);

        caught.sendMessage(net.minecraft.text.Text.literal("§a§l✓ " + catcher.getName().getString() + " caught you!"), true);
        catcher.sendMessage(net.minecraft.text.Text.literal("§a§l✓ PERFECT CATCH! " + caught.getName().getString()), true);
    }

   
    public static void cleanup(UUID playerId) {
        catchReadyTime.remove(playerId);
        catchCooldowns.remove(playerId);
        successfulCatch.remove(playerId);
    }
}
