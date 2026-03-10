package com.cooptest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;


public class FallDapHandler {

    private static final Map<UUID, FallDapState> fallDapPlayers = new HashMap<>();

    private static final Map<UUID, Long> fallChargeStartTime = new HashMap<>();

    private static final Map<UUID, Long> squashedPlayers = new HashMap<>();
    private static final long SQUASHED_DURATION_MS = 25000; // 25 sec squashed animation

    private static final Map<UUID, Double> fallStartY = new HashMap<>();
    private static final double REQUIRED_FALL_BLOCKS = 20.0;

    private static final long FALL_CHARGE_DURATION_MS = 750; // 0.75 sec

    public enum FallDapState {
        NONE,
        CHARGING,    // Playing dap_charge_fall_start
        FALLING      // Playing dap_charge_falling (ready to dap/squash)
    }

    public record FallDapAnimPayload(UUID playerId, int state) implements CustomPayload {
        public static final Id<FallDapAnimPayload> ID =
                new Id<>(Identifier.of("testcoop", "fall_dap_anim"));

        public static final PacketCodec<PacketByteBuf, FallDapAnimPayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> {
                            buf.writeUuid(payload.playerId);
                            buf.writeInt(payload.state);
                        },
                        buf -> new FallDapAnimPayload(buf.readUuid(), buf.readInt())
                );

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SquashAnimPayload(UUID playerId) implements CustomPayload {
        public static final Id<SquashAnimPayload> ID =
                new Id<>(Identifier.of("testcoop", "squash_anim"));

        public static final PacketCodec<PacketByteBuf, SquashAnimPayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> buf.writeUuid(payload.playerId),
                        buf -> new SquashAnimPayload(buf.readUuid())
                );

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(FallDapAnimPayload.ID, FallDapAnimPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SquashAnimPayload.ID, SquashAnimPayload.CODEC);


        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick(server);
        });
    }

    private static void tick(net.minecraft.server.MinecraftServer server) {
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Long>> squashIt = squashedPlayers.entrySet().iterator();
        while (squashIt.hasNext()) {
            Map.Entry<UUID, Long> entry = squashIt.next();
            UUID playerId = entry.getKey();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);

            if (now >= entry.getValue()) {
                squashIt.remove();

                if (player != null) {
                    PoseNetworking.broadcastAnimState(player, 0); // NONE
                    player.removeStatusEffect(StatusEffects.SLOWNESS);
                    player.removeStatusEffect(StatusEffects.JUMP_BOOST);
                }
            } else if (player != null) {

                if (!player.hasStatusEffect(StatusEffects.SLOWNESS) ||
                        player.getStatusEffect(StatusEffects.SLOWNESS).getDuration() < 40) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2, false, false));
                }

                if (!player.hasStatusEffect(StatusEffects.JUMP_BOOST) ||
                        player.getStatusEffect(StatusEffects.JUMP_BOOST).getDuration() < 40) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, 250, false, false));
                }
            }
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerId = player.getUuid();

            if (isSquashed(playerId)) continue;

            if (player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
                cleanup(playerId);
                continue;
            }

            boolean isChargingDap = ChargedDapHandler.isCharging(playerId);
            boolean isOnGround = player.isOnGround();
            boolean isFalling = player.getVelocity().y < -0.1;

            FallDapState currentState = fallDapPlayers.getOrDefault(playerId, FallDapState.NONE);

            if (currentState == FallDapState.NONE) {
                if (isChargingDap && isFalling && !isOnGround) {
                    if (!fallStartY.containsKey(playerId)) {
                        fallStartY.put(playerId, player.getY());
                    }

                    double startY = fallStartY.get(playerId);
                    double fallen = startY - player.getY();

                    if (fallen >= REQUIRED_FALL_BLOCKS && ChargedDapHandler.isFullyCharged(playerId)) {
                        startFallDapCharge(player);
                    }
                } else if (isOnGround) {
                    fallStartY.remove(playerId);
                }
            } else if (currentState == FallDapState.CHARGING) {
                Long chargeStart = fallChargeStartTime.get(playerId);
                if (chargeStart != null && now - chargeStart >= FALL_CHARGE_DURATION_MS) {
                    fallDapPlayers.put(playerId, FallDapState.FALLING);
                    broadcastFallDapAnim(player, 2); // FALLING state
                    player.sendMessage(Text.literal("§c§l FALL DAP READY! "), true);
                }

                if (isOnGround) {
                    if (isChargingDap) {
                        resetToNormalCharge(player);
                    } else {
                        cleanup(playerId);
                        PoseNetworking.broadcastAnimState(player, 0); // NONE
                    }
                }
            } else if (currentState == FallDapState.FALLING) {

                ServerPlayerEntity victim = findSquashTarget(player, 3.0);
                if (victim != null) {
                    // SQUASH!
                    squashPlayer(player, victim);
                    cleanup(playerId);
                    continue;
                }

                // Reset if touched ground
                if (isOnGround) {
                    if (isChargingDap) {
                        resetToNormalCharge(player);
                    } else {
                        cleanup(playerId);
                        PoseNetworking.broadcastAnimState(player, 0); // NONE
                    }
                }
            }
        }
    }

    
    private static void startFallDapCharge(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        fallDapPlayers.put(playerId, FallDapState.CHARGING);

        fallChargeStartTime.put(playerId, System.currentTimeMillis());

        // Broadcast animation
        broadcastFallDapAnim(player, 1); // CHARGING state

        player.sendMessage(Text.literal("§e§l FALL DAP CHARGING! "), true);
    }

  
    private static void resetToNormalCharge(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        fallDapPlayers.remove(playerId);
        fallStartY.remove(playerId);
        fallChargeStartTime.remove(playerId);

        broadcastFallDapAnim(player, 0); 

        PoseNetworking.broadcastAnimState(player,
                com.cooptest.client.CoopAnimationHandler.AnimState.DAP_CHARGE_IDLE.ordinal());

        player.sendMessage(Text.literal("§7Fall dap reset - touched ground"), true);
    }

    
    public static boolean isInFallDapState(UUID playerId) {
        FallDapState state = fallDapPlayers.get(playerId);
        return state == FallDapState.CHARGING || state == FallDapState.FALLING;
    }

    
    public static boolean isReadyToFallDap(UUID playerId) {
        return fallDapPlayers.get(playerId) == FallDapState.FALLING;
    }

    
    public static void executeFallDapHit(ServerWorld world, Vec3d pos,
                                         ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        UUID attackerId = attacker.getUuid();

        broadcastFallDapAnim(attacker, 3); // FALL_HIT state

        cleanup(attackerId);

    }


    private static void squashPlayer(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        ServerWorld world = attacker.getServerWorld();
        Vec3d pos = victim.getPos();

        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 2.0f, 0.5f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 0.8f);

        world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y + 1, pos.z, 30, 0.5, 0.3, 0.5, 0.2);
        world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 20, 0.5, 0.2, 0.5, 0.05);

        dropHandItems(victim, world, pos);

        victim.damage(world.getDamageSources().playerAttack(attacker), 10.0f);

        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 420, 2, false, false));
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 420, 250, false, false));
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 300, 0, false, false));

        victim.setVelocity(0, 0, 0);
        victim.velocityModified = true;

        squashedPlayers.put(victim.getUuid(), System.currentTimeMillis() + SQUASHED_DURATION_MS);

        // Broadcast squash animation
        for (ServerPlayerEntity p : world.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, new SquashAnimPayload(victim.getUuid()));
        }

        // Messages
        attacker.sendMessage(Text.literal("§c§l💀 SQUASHED! 💀"), true);
        victim.sendMessage(Text.literal("§c§lYOU GOT SQUASHED FOR 25 SECONDS!"), true);
    }

  
    private static void dropHandItems(ServerPlayerEntity player, ServerWorld world, Vec3d pos) {
        // Drop main hand item
        net.minecraft.item.ItemStack mainStack = player.getMainHandStack();
        if (!mainStack.isEmpty()) {
            net.minecraft.entity.ItemEntity mainItem = new net.minecraft.entity.ItemEntity(
                    world, pos.x, pos.y + 0.5, pos.z, mainStack.copy()
            );
            mainItem.setVelocity(
                    (world.random.nextDouble() - 0.5) * 0.3,
                    world.random.nextDouble() * 0.2 + 0.1,
                    (world.random.nextDouble() - 0.5) * 0.3
            );
            world.spawnEntity(mainItem);
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
        }

        net.minecraft.item.ItemStack offStack = player.getOffHandStack();
        if (!offStack.isEmpty()) {
            net.minecraft.entity.ItemEntity offItem = new net.minecraft.entity.ItemEntity(
                    world, pos.x, pos.y + 0.5, pos.z, offStack.copy()
            );
            offItem.setVelocity(
                    (world.random.nextDouble() - 0.5) * 0.3,
                    world.random.nextDouble() * 0.2 + 0.1,
                    (world.random.nextDouble() - 0.5) * 0.3
            );
            world.spawnEntity(offItem);
            player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, net.minecraft.item.ItemStack.EMPTY);
        }
    }

    
    private static ServerPlayerEntity findSquashTarget(ServerPlayerEntity attacker, double horizontalRange) {
        double attackerY = attacker.getY();

        for (ServerPlayerEntity other : attacker.getServerWorld().getPlayers()) {
            if (other == attacker) continue;

            if (isSquashed(other.getUuid())) continue;

            double otherY = other.getY();

            double heightDiff = attackerY - otherY;
            if (heightDiff < 0.5 || heightDiff > 3.0) continue;

            double dx = attacker.getX() - other.getX();
            double dz = attacker.getZ() - other.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            if (horizontalDist <= horizontalRange) {
                return other;
            }
        }
        return null;
    }

    private static ServerPlayerEntity findNearbyPlayer(ServerPlayerEntity player, double range) {
        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other == player) continue;
            if (other.squaredDistanceTo(player) <= range * range) {
                return other;
            }
        }
        return null;
    }

  
    public static boolean isSquashed(UUID playerId) {
        Long endTime = squashedPlayers.get(playerId);
        if (endTime == null) return false;
        if (System.currentTimeMillis() >= endTime) {
            squashedPlayers.remove(playerId);
            return false;
        }
        return true;
    }

  
    private static void broadcastFallDapAnim(ServerPlayerEntity player, int state) {
        var server = player.getServer();
        if (server == null) return;

        FallDapAnimPayload payload = new FallDapAnimPayload(player.getUuid(), state);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    
    public static void cleanup(UUID playerId) {
        fallDapPlayers.remove(playerId);
        fallStartY.remove(playerId);
        fallChargeStartTime.remove(playerId);
        squashedPlayers.remove(playerId);
    }
}
