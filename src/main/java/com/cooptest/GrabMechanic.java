package com.cooptest;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.UUID;

public class GrabMechanic {

    public static final HashMap<UUID, UUID> holding = new HashMap<>();
    public static final HashMap<UUID, UUID> heldBy = new HashMap<>();
    public static final HashMap<UUID, Boolean> shieldMode = new HashMap<>();  
    public static final HashMap<UUID, Long> shieldSwapCooldown = new HashMap<>();
    public static final HashMap<UUID, net.minecraft.entity.decoration.ArmorStandEntity> shieldArmorStands = new HashMap<>();
    private static final long SHIELD_SWAP_COOLDOWN_MS = 1000;  
    private static final HashMap<UUID, PendingThrow> pendingThrows = new HashMap<>();
    private static final HashMap<UUID, ThrownPlayerData> thrownPlayers = new HashMap<>();
    private static class PendingThrow {
        ServerPlayerEntity holder;
        ServerPlayerEntity held;
        Vec3d velocity;
        int ticksRemaining;

        PendingThrow(ServerPlayerEntity holder, ServerPlayerEntity held, Vec3d velocity, int delay) {
            this.holder = holder;
            this.held = held;
            this.velocity = velocity;
            this.ticksRemaining = delay;
        }
    }

    private static class ThrownPlayerData {
        double startY;
        int ticksFlying;
        boolean wasOnFire;
        Vec3d lastPos;
        Vec3d velocity;
        long throwTimeMs;
        boolean elytraBoostUsed;

        ThrownPlayerData(double startY, boolean wasOnFire, Vec3d velocity) {
            this.startY = startY;
            this.ticksFlying = 0;
            this.wasOnFire = wasOnFire;
            this.lastPos = null;
            this.velocity = velocity;
            this.throwTimeMs = System.currentTimeMillis();
            this.elytraBoostUsed = false;
        }
    }

    public static final HashMap<UUID, Boolean> elytraBoostRequests = new HashMap<>();

    public static final HashMap<UUID, float[]> airMovementInput = new HashMap<>();

    private static final double AIR_CONTROL_STRENGTH = 0.025;

    public static boolean tryGrab(ServerPlayerEntity holder, ServerPlayerEntity held) {
        if (holder == held) return false;
        if (holder.distanceTo(held) > 3.0f) return false;
        if (holding.containsKey(holder.getUuid())) return false;
        if (heldBy.containsKey(held.getUuid())) return false;

        if (PushInteractionHandler.hasPushImmunity(held.getUuid())) return false;

        PoseState holderPose = PoseNetworking.poseStates.getOrDefault(holder.getUuid(), PoseState.NONE);
        if (holderPose != PoseState.GRAB_READY) return false;

        boolean success = held.startRiding(holder);
        if (!success) return false;

        holding.put(holder.getUuid(), held.getUuid());
        heldBy.put(held.getUuid(), holder.getUuid());

        PoseNetworking.poseStates.put(holder.getUuid(), PoseState.GRAB_HOLDING);
        PoseNetworking.poseStates.put(held.getUuid(), PoseState.GRABBED);

        if (holder.getEntityWorld() != null) {
            PoseNetworking.broadcastPoseChange(holder.getEntityWorld().getServer(), holder.getUuid(), PoseState.GRAB_HOLDING);
            PoseNetworking.broadcastPoseChange(holder.getEntityWorld().getServer(), held.getUuid(), PoseState.GRABBED);
            GrabNetworking.broadcastGrabState(holder.getEntityWorld().getServer(), holder.getUuid(), held.getUuid(), true);

            EntityPassengersSetS2CPacket packet = new EntityPassengersSetS2CPacket(holder);
            for (ServerPlayerEntity p : holder.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
                p.networkHandler.sendPacket(packet);
            }
        }

        holder.getEntityWorld().playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 1.0f, 1.0f);

        return true;
    }

    public static boolean tryThrow(ServerPlayerEntity holder, float power) {
        UUID heldId = holding.get(holder.getUuid());
        if (heldId == null) return false;

        if (isInShieldMode(holder.getUuid())) {
            holder.sendMessage(net.minecraft.text.Text.literal("§cSwitch to throw mode first! (Press V)"), true);
            return false;
        }

        ServerPlayerEntity held = holder.getEntityWorld().getServer().getPlayerManager().getPlayer(heldId);
        if (held == null) {
            cleanupGrab(holder.getUuid());
            return false;
        }

        if (!holder.isCreative()) {
            if (holder.getHungerManager().getFoodLevel() < 6) {
                holder.sendMessage(net.minecraft.text.Text.literal("§cToo hungry to throw!"), true);
                return false;
            }
            holder.getHungerManager().addExhaustion(18.0f); // Causes ~6 hunger point loss
        }

        Vec3d lookDir = holder.getRotationVec(1.0f);
        float scaledPower = 0.5f + (1.5f - 0.5f) * power;
        double horizX = lookDir.x * scaledPower * 1.3; // 30% more horizontal
        double horizZ = lookDir.z * scaledPower * 1.3;

        double verticalBase = 0.4 + (lookDir.y * 0.6); // Base upward
        double maxVertical = 1.5; // Cap for ~4.5 blocks height
        double verticalVel = Math.min(verticalBase + (power * 0.5), maxVertical);
        if (lookDir.y < 0) verticalVel = Math.max(0.3, verticalVel); // Still some lift when throwing down

        Vec3d throwVelocity = new Vec3d(horizX, verticalVel, horizZ);

        Vec3d releasePos = holder.getEntityPos()
                .add(lookDir.multiply(1.5).multiply(1, 0, 1))
                .add(0, 0.5, 0);

        held.stopRiding();

        holding.remove(holder.getUuid());
        heldBy.remove(held.getUuid());

        PoseNetworking.poseStates.put(holder.getUuid(), PoseState.NONE);

        if (holder.getEntityWorld().getServer() != null) {
            PoseNetworking.broadcastPoseChange(holder.getEntityWorld().getServer(), holder.getUuid(), PoseState.NONE);
            PoseNetworking.broadcastAnimState(holder, 0); // NONE animation
            GrabNetworking.broadcastGrabState(holder.getEntityWorld().getServer(), holder.getUuid(), held.getUuid(), false);

            EntityPassengersSetS2CPacket packet = new EntityPassengersSetS2CPacket(holder);
            for (ServerPlayerEntity p : holder.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
                p.networkHandler.sendPacket(packet);
            }
        }

        float throwYaw = holder.getYaw();
        float throwPitch = holder.getPitch();
        held.refreshPositionAndAngles(releasePos.x, releasePos.y, releasePos.z, throwYaw, throwPitch);
        held.setHeadYaw(throwYaw);
        held.networkHandler.requestTeleport(releasePos.x, releasePos.y, releasePos.z, throwYaw, throwPitch);

        pendingThrows.put(held.getUuid(), new PendingThrow(holder, held, throwVelocity, 3));

        holder.getEntityWorld().playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.8f + (power * 0.4f));

        spawnThrowParticles(holder, held);

        return true;
    }

    private static void spawnThrowParticles(ServerPlayerEntity holder, ServerPlayerEntity held) {
        ServerWorld world = holder.getEntityWorld();
        Vec3d pos = holder.getEntityPos();

        for (int i = 0; i < 10; i++) {
            double offsetX = (world.random.nextDouble() - 0.5) * 0.5;
            double offsetY = world.random.nextDouble() * 0.5 + 0.5;
            double offsetZ = (world.random.nextDouble() - 0.5) * 0.5;

            world.spawnParticles(ParticleTypes.CLOUD,
                    pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                    1, 0, 0, 0, 0.05);
        }
    }

  
    public static void tick(net.minecraft.server.MinecraftServer server) {
        tickShieldMode(server);

      
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.hasVehicle() && player.getVehicle() instanceof ServerPlayerEntity carrier) {
                PoseState pose = PoseNetworking.poseStates.get(player.getUuid());
                if (pose == PoseState.GRABBED) {
                    float carrierYaw = carrier.getYaw();
                    player.setYaw(carrierYaw);
                    player.setBodyYaw(carrierYaw);
                    player.setHeadYaw(carrierYaw);
                }
            }
        }

        var throwIterator = pendingThrows.entrySet().iterator();
        while (throwIterator.hasNext()) {
            var entry = throwIterator.next();
            PendingThrow pending = entry.getValue();

            pending.ticksRemaining--;

            if (pending.ticksRemaining <= 0) {
                ServerPlayerEntity held = pending.held;
                if (held != null && held.isAlive()) {
                    held.setVelocity(pending.velocity);
                    held.velocityDirty = true;
                    held.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(held));

                    boolean wasOnFire = held.isOnFire();
                    thrownPlayers.put(held.getUuid(), new ThrownPlayerData(held.getY(), wasOnFire, pending.velocity));
                }
                throwIterator.remove();
            }
        }

        var landIterator = thrownPlayers.entrySet().iterator();
        while (landIterator.hasNext()) {
            var entry = landIterator.next();
            UUID playerId = entry.getKey();
            ThrownPlayerData data = entry.getValue();

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null) {
                landIterator.remove();
                continue;
            }

            data.ticksFlying++;

            Vec3d vel = player.getVelocity();
            if (vel.horizontalLengthSquared() > 0.01) {
                float velocityYaw = (float) Math.toDegrees(Math.atan2(-vel.x, vel.z));
                player.setYaw(velocityYaw);
                player.setBodyYaw(velocityYaw);
                player.setHeadYaw(velocityYaw);
            }

            float[] moveInput = airMovementInput.get(playerId);
            if (moveInput != null && (Math.abs(moveInput[0]) > 0.01f || Math.abs(moveInput[1]) > 0.01f)) {
                float yawRad = (float) Math.toRadians(player.getYaw());
                float forward = moveInput[0];
                float strafe = moveInput[1];

                double driftX = (-strafe * Math.cos(yawRad) - forward * Math.sin(yawRad)) * AIR_CONTROL_STRENGTH;
                double driftZ = (-strafe * Math.sin(yawRad) + forward * Math.cos(yawRad)) * AIR_CONTROL_STRENGTH;

                Vec3d currentVel = player.getVelocity();
                player.setVelocity(currentVel.add(driftX, 0, driftZ));
                player.velocityDirty = true;
            }

            long timeSinceThrow = System.currentTimeMillis() - data.throwTimeMs;
            if (!data.elytraBoostUsed && timeSinceThrow < 2000) {
                if (elytraBoostRequests.remove(playerId) != null) {
                    if (!data.elytraBoostUsed && timeSinceThrow < 2000L && elytraBoostRequests.remove(playerId) != null && player.getEquippedStack(EquipmentSlot.CHEST).getItem() .equals(net.minecraft.item.Items.ELYTRA.getDefaultStack().getItem())) {
                        Vec3d look = player.getRotationVec(1.0f);
                        double boostStrength = 1.5; // Similar to small rocket
                        player.setVelocity(player.getVelocity().add(
                                look.x * boostStrength,
                                look.y * boostStrength + 0.5,
                                look.z * boostStrength
                        ));
                        player.velocityDirty = true;

                        player.startGliding(); /* i'm not sure but i THINK fallflying is gliding now?
                        idk, the official maven repo is acting like it is, ill check it out - update, it is. */
                        player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0f, 1.2f);
                        player.getEntityWorld().spawnParticles(ParticleTypes.FIREWORK,
                                player.getX(), player.getY(), player.getZ(), 10, 0.2, 0.2, 0.2, 0.1);

                        data.elytraBoostUsed = true;

                        PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                        PoseNetworking.broadcastPoseChange(server, playerId, PoseState.NONE);
                        airMovementInput.remove(playerId);
                        landIterator.remove();
                        continue;
                    }
                }
            }

            if (data.lastPos != null) {
                checkWallCollision(player, data);
            }
            data.lastPos = player.getEntityPos();

            if (data.ticksFlying % 2 == 0) {
                spawnTrailParticles(player);

                if (data.wasOnFire && player.isOnFire()) {
                    spawnFireTrail(player, data);
                }
            }

            checkForNearbyCreepers(player);

            if (data.ticksFlying >= 10) {
                boolean onGround = player.isOnGround();
                boolean inWater = player.isTouchingWater();
                boolean closeToGround = isCloseToGround(player);
                boolean isFalling = player.getVelocity().y < -0.1;

                if (onGround || inWater || (closeToGround && isFalling)) {
                    PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                    PoseNetworking.broadcastPoseChange(server, playerId, PoseState.NONE);
                    ServerPlayerEntity landedPlayer = server.getPlayerManager().getPlayer(playerId);
                    if (landedPlayer != null) {
                        PoseNetworking.broadcastAnimState(landedPlayer, 0); // NONE animation
                    }

                    airMovementInput.remove(playerId);

                    // Fire explosion on impact!
                    if (data.wasOnFire) {
                        createFireExplosion(player);
                    }

                    // Landing particles
                    spawnLandingParticles(player);

                    landIterator.remove();
                }
            }

            if (data.ticksFlying > 200) {
                PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                PoseNetworking.broadcastPoseChange(server, playerId, PoseState.NONE);
                ServerPlayerEntity timedOutPlayer = server.getPlayerManager().getPlayer(playerId);
                if (timedOutPlayer != null) {
                    PoseNetworking.broadcastAnimState(timedOutPlayer, 0); // NONE animation
                }
                airMovementInput.remove(playerId);
                landIterator.remove();
            }
        }
    }

    
    private static void checkWallCollision(ServerPlayerEntity player, ThrownPlayerData data) {
        ServerWorld world = player.getEntityWorld();
        Vec3d currentPos = player.getEntityPos();
        Vec3d velocity = player.getVelocity();

        double speed = velocity.horizontalLength();
        if (speed < 0.3) return;

        Vec3d direction = velocity.normalize();

        for (double dist = 0.3; dist <= 1.5; dist += 0.3) {
            Vec3d checkPos = currentPos.add(direction.multiply(dist));

            for (double yOff = 0; yOff <= 1.8; yOff += 0.9) {
                BlockPos blockPos = new BlockPos(
                        (int) Math.floor(checkPos.x),
                        (int) Math.floor(checkPos.y + yOff),
                        (int) Math.floor(checkPos.z)
                );

                BlockState blockState = world.getBlockState(blockPos);

                if (!blockState.isAir() && canBreakBlock(blockState, world, blockPos)) {
                    // Calculate damage based on hardness
                    float hardness = blockState.getHardness(world, blockPos);
                    float damage = calculateWallDamage(hardness, speed);

                    // Break the block
                    world.breakBlock(blockPos, true, player);

                    // Play break sound
                    world.playSound(null, blockPos, blockState.getSoundGroup().getBreakSound(),
                            SoundCategory.BLOCKS, 1.0f, 1.0f);

                    // Damage the player
                    if (damage > 0) {
                        player.clientDamage(world.getDamageSources().flyIntoWall());
                    }

                    // Slow down slightly after breaking
                    player.setVelocity(velocity.multiply(0.7));
                    player.velocityDirty = true;
                    player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

                    // Particles
                    world.spawnParticles(ParticleTypes.CRIT,
                            blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5,
                            10, 0.3, 0.3, 0.3, 0.1);
                }
            }
        }
    }

    
    private static boolean canBreakBlock(BlockState state, ServerWorld world, BlockPos pos) {
        if (state.isOf(Blocks.BEDROCK)) return false;

        if (state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.CRYING_OBSIDIAN)) return false;

        if (state.isOf(Blocks.REINFORCED_DEEPSLATE)) return false;

        if (state.isOf(Blocks.END_PORTAL_FRAME)) return false;

        if (state.isOf(Blocks.BARRIER)) return false;

        if (state.isOf(Blocks.COMMAND_BLOCK) || state.isOf(Blocks.CHAIN_COMMAND_BLOCK) ||
                state.isOf(Blocks.REPEATING_COMMAND_BLOCK)) return false;

        if (state.isOf(Blocks.STRUCTURE_BLOCK) || state.isOf(Blocks.JIGSAW)) return false;

        if (state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST) ||
                state.isOf(Blocks.ENDER_CHEST) || state.isOf(Blocks.BARREL) ||
                state.isOf(Blocks.SHULKER_BOX) || state.isOf(Blocks.FURNACE) ||
                state.isOf(Blocks.BLAST_FURNACE) || state.isOf(Blocks.SMOKER) ||
                state.isOf(Blocks.BREWING_STAND) || state.isOf(Blocks.ENCHANTING_TABLE) ||
                state.isOf(Blocks.ANVIL) || state.isOf(Blocks.CHIPPED_ANVIL) ||
                state.isOf(Blocks.DAMAGED_ANVIL) || state.isOf(Blocks.CRAFTING_TABLE) ||
                state.isOf(Blocks.CARTOGRAPHY_TABLE) || state.isOf(Blocks.FLETCHING_TABLE) ||
                state.isOf(Blocks.GRINDSTONE) || state.isOf(Blocks.LOOM) ||
                state.isOf(Blocks.SMITHING_TABLE) || state.isOf(Blocks.STONECUTTER) ||
                state.isOf(Blocks.LECTERN) || state.isOf(Blocks.BEACON) ||
                state.isOf(Blocks.RESPAWN_ANCHOR) || state.isOf(Blocks.LODESTONE)) {
            return false;
        }

        if (state.isIn(BlockTags.DOORS) || state.isIn(BlockTags.TRAPDOORS) ||
                state.isIn(BlockTags.FENCE_GATES)) {
            return false;
        }

        if (state.isIn(BlockTags.SIGNS) || state.isIn(BlockTags.ALL_HANGING_SIGNS)) {
            return false;
        }

        if (state.isIn(BlockTags.BEDS)) return false;

        if (state.isIn(BlockTags.BUTTONS) || state.isOf(Blocks.LEVER)) return false;

        float hardness = state.getHardness(world, pos);
        if (hardness < 0) return false;

        if (hardness > 25) return false;

        return true;
    }

    
    private static float calculateWallDamage(float hardness, double speed) {
        

        float baseDamage = hardness * 0.8f;

        float speedMultiplier = (float) Math.min(2.0, speed);

        return Math.max(1.0f, Math.min(10.0f, baseDamage * speedMultiplier));
    }

    private static void spawnTrailParticles(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        Vec3d pos = player.getEntityPos();

        world.spawnParticles(ParticleTypes.CLOUD,
                pos.x, pos.y + 0.5, pos.z,
                1, 0.1, 0.1, 0.1, 0.02);
    }

    private static void spawnLandingParticles(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        Vec3d pos = player.getEntityPos();

        // Get the block below for block-break particles
        BlockPos groundPos = player.getBlockPos().down();
        BlockState groundBlock = world.getBlockState(groundPos);

      
        world.spawnParticles(ParticleTypes.EXPLOSION,
                pos.x, pos.y + 0.5, pos.z,
                1, 0, 0, 0, 0);

        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2) * i / 24;
            double offsetX = Math.cos(angle) * 0.8;
            double offsetZ = Math.sin(angle) * 0.8;
            double velX = Math.cos(angle) * 0.3;
            double velZ = Math.sin(angle) * 0.3;

            world.spawnParticles(ParticleTypes.CLOUD,
                    pos.x + offsetX, pos.y + 0.2, pos.z + offsetZ,
                    1, velX, 0.1, velZ, 0.05);

            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    pos.x + offsetX * 0.5, pos.y + 0.1, pos.z + offsetZ * 0.5,
                    1, velX * 0.5, 0.2, velZ * 0.5, 0.02);
        }

        if (!groundBlock.isAir()) {
            BlockStateParticleEffect blockParticle = new BlockStateParticleEffect(
                    ParticleTypes.BLOCK, groundBlock);

            for (int i = 0; i < 30; i++) {
                double offsetX = (world.random.nextDouble() - 0.5) * 1.5;
                double offsetZ = (world.random.nextDouble() - 0.5) * 1.5;
                double velY = world.random.nextDouble() * 0.5 + 0.2;

                world.spawnParticles(blockParticle,
                        pos.x + offsetX, pos.y + 0.1, pos.z + offsetZ,
                        1, 0, velY, 0, 0.15);
            }
        }

        world.spawnParticles(ParticleTypes.POOF,
                pos.x, pos.y + 0.3, pos.z,
                15, 0.5, 0.3, 0.5, 0.05);

        world.spawnParticles(ParticleTypes.CRIT,
                pos.x, pos.y + 0.5, pos.z,
                10, 0.5, 0.5, 0.5, 0.3);

        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                SoundCategory.PLAYERS, 0.5f, 1.2f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BLOCK_ANVIL_LAND,
                SoundCategory.PLAYERS, 0.3f, 0.8f);
    }

    private static boolean isCloseToGround(ServerPlayerEntity player) {
        if (player.isOnGround()) return true;
        if (player.getVelocity().y >= 0) return false;

        double maxDistance = 1.0;
        double startY = player.getY();
        for (double checkY = startY; checkY > startY - maxDistance; checkY -= 0.5) {
            var blockPos = player.getBlockPos().withY((int) checkY - 1);
            var blockState = player.getEntityWorld().getBlockState(blockPos);

            if (!blockState.isAir() && blockState.isSolidBlock(player.getEntityWorld(), blockPos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean tryDrop(ServerPlayerEntity holder) {
        UUID heldId = holding.get(holder.getUuid());
        if (heldId == null) return false;

        ServerPlayerEntity held = holder.getEntityWorld().getServer().getPlayerManager().getPlayer(heldId);
        if (held == null) {
            cleanupGrab(holder.getUuid());
            return false;
        }

        // Remove slowness if was in shield mode
        if (isInShieldMode(holder.getUuid())) {
            holder.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
        }

        held.stopRiding();
        cleanupGrab(holder.getUuid());

        PoseNetworking.poseStates.put(holder.getUuid(), PoseState.NONE);
        PoseNetworking.poseStates.put(held.getUuid(), PoseState.NONE);

        if (holder.getEntityWorld().getServer() != null) {
            PoseNetworking.broadcastPoseChange(holder.getEntityWorld().getServer(), holder.getUuid(), PoseState.NONE);
            PoseNetworking.broadcastPoseChange(holder.getEntityWorld().getServer(), held.getUuid(), PoseState.NONE);
            PoseNetworking.broadcastAnimState(holder, 0); // NONE animation
            PoseNetworking.broadcastAnimState(held, 0); // NONE animation
            GrabNetworking.broadcastGrabState(holder.getEntityWorld().getServer(), holder.getUuid(), held.getUuid(), false);

            EntityPassengersSetS2CPacket packet = new EntityPassengersSetS2CPacket(holder);
            for (ServerPlayerEntity p : holder.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
                p.networkHandler.sendPacket(packet);
            }
        }

        holder.getEntityWorld().playSound(null, held.getX(), held.getY(), held.getZ(),
                SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, 0.8f);

        return true;
    }

    public static boolean tryEscape(ServerPlayerEntity held) {
        UUID holderId = heldBy.get(held.getUuid());
        if (holderId == null) return false;

        ServerPlayerEntity holder = held.getEntityWorld().getServer().getPlayerManager().getPlayer(holderId);

        // Remove slowness if holder was in shield mode
        if (holder != null && isInShieldMode(holderId)) {
            holder.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
        }

        held.stopRiding();

        cleanupGrab(holderId);

        PoseNetworking.poseStates.put(held.getUuid(), PoseState.NONE);
        if (holder != null) {
            PoseNetworking.poseStates.put(holder.getUuid(), PoseState.NONE);
            PoseNetworking.broadcastPoseChange(held.getEntityWorld().getServer(), holder.getUuid(), PoseState.NONE);
            // Reset holder animation to NONE
            PoseNetworking.broadcastAnimState(holder, 0); // NONE animation
        }

        if (held.getEntityWorld().getServer() != null) {
            PoseNetworking.broadcastPoseChange(held.getEntityWorld().getServer(), held.getUuid(), PoseState.NONE);
            PoseNetworking.broadcastAnimState(held, 0); // NONE animation
            GrabNetworking.broadcastGrabState(held.getEntityWorld().getServer(), holderId, held.getUuid(), false);

            if (holder != null) {
                EntityPassengersSetS2CPacket packet = new EntityPassengersSetS2CPacket(holder);
                for (ServerPlayerEntity p : held.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
                    p.networkHandler.sendPacket(packet);
                }
            }

            Vec3d escapePos = held.getEntityPos().add(0, 0.1, 0);
            held.requestTeleport(escapePos.x, escapePos.y, escapePos.z);
        }

        held.getEntityWorld().playSound(null, held.getX(), held.getY(), held.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.8f, 1.2f);

        return true;
    }

    public static void cleanupGrab(UUID holderUuid) {
        UUID heldUuid = holding.remove(holderUuid);
        if (heldUuid != null) {
            heldBy.remove(heldUuid);
        }
        // Clean shield mode and armor stand
        shieldMode.remove(holderUuid);
        shieldSwapCooldown.remove(holderUuid);
        net.minecraft.entity.decoration.ArmorStandEntity armorStand = shieldArmorStands.remove(holderUuid);
        if (armorStand != null && !armorStand.isRemoved()) {
            armorStand.discard();
        }
    }

    public static boolean isHolding(ServerPlayerEntity player) {
        return holding.containsKey(player.getUuid());
    }

    public static boolean isBeingHeld(ServerPlayerEntity player) {
        return heldBy.containsKey(player.getUuid());
    }

    public static void forceRelease(UUID playerUuid) {
        UUID heldUuid = holding.remove(playerUuid);
        if (heldUuid != null) heldBy.remove(heldUuid);

        UUID holderUuid = heldBy.remove(playerUuid);
        if (holderUuid != null) holding.remove(holderUuid);

        PoseNetworking.poseStates.remove(playerUuid);
        pendingThrows.remove(playerUuid);
        thrownPlayers.remove(playerUuid);
    }

    /**
     * Spawn fire trail behind a flying player who is on fire
     */
    private static void spawnFireTrail(ServerPlayerEntity player, ThrownPlayerData data) {
        ServerWorld world = player.getEntityWorld();
        Vec3d pos = player.getEntityPos();

        // Spawn fire particles
        world.spawnParticles(ParticleTypes.FLAME,
                pos.x, pos.y + 0.5, pos.z,
                3, 0.2, 0.2, 0.2, 0.02);

        world.spawnParticles(ParticleTypes.SMOKE,
                pos.x, pos.y + 0.3, pos.z,
                2, 0.1, 0.1, 0.1, 0.01);

        if (data.ticksFlying % 4 == 0) {
            BlockPos groundPos = player.getBlockPos().down();
            for (int i = 0; i < 5; i++) {
                BlockState belowState = world.getBlockState(groundPos);
                if (!belowState.isAir()) {
                    BlockPos firePos = groundPos.up();
                    BlockState fireSpot = world.getBlockState(firePos);
                    if (fireSpot.isAir()) {
                        world.setBlockState(firePos, Blocks.FIRE.getDefaultState());
                    }
                    break;
                }
                groundPos = groundPos.down();
            }
        }
    }

   
    private static void checkForNearbyCreepers(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        double checkRadius = 4.0;

        var nearbyCreepers = world.getEntitiesByClass(
                net.minecraft.entity.mob.CreeperEntity.class,
                player.getBoundingBox().expand(checkRadius),
                creeper -> creeper.isAlive() && player.distanceTo(creeper) <= checkRadius
        );

        for (var creeper : nearbyCreepers) {
            world.createExplosion(
                    creeper,
                    creeper.getX(), creeper.getY(), creeper.getZ(),
                    3.0f,
                    net.minecraft.world.World.ExplosionSourceType.MOB
            );

            creeper.discard();

            world.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(),
                    SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1.0f, 1.0f);
        }

        var nearbyGhasts = world.getEntitiesByClass(
                net.minecraft.entity.mob.GhastEntity.class,
                player.getBoundingBox().expand(checkRadius),
                ghast -> ghast.isAlive() && player.distanceTo(ghast) <= checkRadius
        );

        for (var ghast : nearbyGhasts) {
            Vec3d ghastPos = ghast.getEntityPos();

            ghast.clientDamage(world.getDamageSources().playerAttack((PlayerEntity)player));

            world.playSound(null, ghastPos.x, ghastPos.y, ghastPos.z,
                    ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 2.0f, 1.0f);

            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                    ghastPos.x, ghastPos.y, ghastPos.z, 3, 0.5, 0.5, 0.5, 0);
            world.spawnParticles(ParticleTypes.CLOUD,
                    ghastPos.x, ghastPos.y, ghastPos.z, 30, 1.5, 1.5, 1.5, 0.1);
            world.spawnParticles(ParticleTypes.FLAME,
                    ghastPos.x, ghastPos.y, ghastPos.z, 20, 1.0, 1.0, 1.0, 0.2);

            player.sendMessage(net.minecraft.text.Text.literal("§6§l💥 GHAST OBLITERATED! 💥"), true);
        }
    }

    
    private static void createFireExplosion(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        Vec3d pos = player.getEntityPos();

        world.createExplosion(
                player,
                pos.x, pos.y, pos.z,
                2.0f,
                true,
                net.minecraft.world.World.ExplosionSourceType.MOB
        );

        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 1.5f, 1.0f);

        player.clientDamage(world.getDamageSources().onFire());

        for (int i = 0; i < 20; i++) {
            double offsetX = (world.random.nextDouble() - 0.5) * 3;
            double offsetY = world.random.nextDouble() * 2;
            double offsetZ = (world.random.nextDouble() - 0.5) * 3;

            world.spawnParticles(ParticleTypes.FLAME,
                    pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                    1, 0, 0, 0, 0.1);
        }
    }

    
    public static void setAirMovementInput(UUID playerId, float forward, float strafe) {
        if (thrownPlayers.containsKey(playerId)) {
            airMovementInput.put(playerId, new float[]{forward, strafe});
        }
    }

  
    public static boolean isPlayerThrown(UUID playerId) {
        return thrownPlayers.containsKey(playerId);
    }

    
    public static void requestElytraBoost(UUID playerId) {
        if (thrownPlayers.containsKey(playerId)) {
            elytraBoostRequests.put(playerId, true);
        }
    }

    
    public static void fullCleanup(UUID playerId) {
        thrownPlayers.remove(playerId);
        pendingThrows.remove(playerId);
        elytraBoostRequests.remove(playerId);
        airMovementInput.remove(playerId);
        shieldMode.remove(playerId);
        shieldSwapCooldown.remove(playerId);

        net.minecraft.entity.decoration.ArmorStandEntity armorStand = shieldArmorStands.remove(playerId);
        if (armorStand != null && !armorStand.isRemoved()) {
            armorStand.discard();
        }

        if (holding.containsKey(playerId)) {
            UUID heldId = holding.get(playerId);
            heldBy.remove(heldId);
            holding.remove(playerId);
            net.minecraft.entity.decoration.ArmorStandEntity holderArmorStand = shieldArmorStands.remove(playerId);
            if (holderArmorStand != null && !holderArmorStand.isRemoved()) {
                holderArmorStand.discard();
            }
        }
        if (heldBy.containsKey(playerId)) {
            UUID holderId = heldBy.get(playerId);
            holding.remove(holderId);
            heldBy.remove(playerId);
            shieldMode.remove(holderId);
            net.minecraft.entity.decoration.ArmorStandEntity holderArmorStand = shieldArmorStands.remove(holderId);
            if (holderArmorStand != null && !holderArmorStand.isRemoved()) {
                holderArmorStand.discard();
            }
        }
    }

    // ==================== HUMAN SHIELD SYSTEM ====================

    public static boolean toggleShieldMode(ServerPlayerEntity holder) {
        UUID holderId = holder.getUuid();

        if (!holding.containsKey(holderId)) return false;

        Long cooldownEnd = shieldSwapCooldown.get(holderId);
        if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
            long remaining = (cooldownEnd - System.currentTimeMillis()) / 100;
            holder.sendMessage(net.minecraft.text.Text.literal("§cSwap cooldown! " + (remaining / 10.0) + "s"), true);
            return false;
        }

        UUID heldId = holding.get(holderId);
        ServerPlayerEntity held = holder.getEntityWorld().getServer().getPlayerManager().getPlayer(heldId);
        if (held == null) return false;

        boolean currentMode = shieldMode.getOrDefault(holderId, false);
        boolean newMode = !currentMode;
        shieldMode.put(holderId, newMode);

        shieldSwapCooldown.put(holderId, System.currentTimeMillis() + SHIELD_SWAP_COOLDOWN_MS);

        if (newMode) {
            holder.sendMessage(net.minecraft.text.Text.literal("§b🛡 HUMAN SHIELD MODE"), true);
            held.sendMessage(net.minecraft.text.Text.literal("§c⚠ You are now a SHIELD!"), true);

            held.stopRiding();

            ServerWorld world = holder.getEntityWorld();

            double yaw = Math.toRadians(holder.getYaw());

            double forwardX = -Math.sin(yaw) * 0.8;  // 0.8 blocks forward (was 0.6)
            double forwardZ = Math.cos(yaw) * 0.8;

            net.minecraft.entity.decoration.ArmorStandEntity armorStand = new net.minecraft.entity.decoration.ArmorStandEntity(
                    net.minecraft.entity.EntityType.ARMOR_STAND, world);

            armorStand.setPosition(
                    holder.getX() + forwardX,
                    holder.getY() - 0.5, 
                    holder.getZ() + forwardZ);
            armorStand.setYaw(holder.getYaw()); 
            armorStand.setInvisible(true);
            armorStand.setNoGravity(true);
            armorStand.setInvulnerable(true);
            armorStand.setSilent(true);

            world.spawnEntity(armorStand);
            shieldArmorStands.put(holderId, armorStand);

            held.startRiding(armorStand);

            world.playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                    SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 1.2f);

            holder.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.SLOWNESS, 999999, 1, false, false, true));

            
            PoseNetworking.poseStates.put(heldId, PoseState.NONE);
            PoseNetworking.broadcastPoseChange(holder.getEntityWorld().getServer(), heldId, PoseState.NONE);

            PoseNetworking.poseStates.put(holderId, PoseState.NONE);

            PoseNetworking.broadcastAnimState(held, 29); // SHIELD animation

            PoseNetworking.broadcastAnimState(holder, 28); // HOLD_SHIELD animation

            EntityPassengersSetS2CPacket holderPacket = new EntityPassengersSetS2CPacket(holder);
            for (ServerPlayerEntity p : holder.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
                p.networkHandler.sendPacket(holderPacket);
            }

            net.minecraft.entity.decoration.ArmorStandEntity as = shieldArmorStands.get(holderId);
            if (as != null) {
                EntityPassengersSetS2CPacket asPacket = new EntityPassengersSetS2CPacket(as);
                for (ServerPlayerEntity p : holder.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
                    p.networkHandler.sendPacket(asPacket);
                }
            }

            broadcastShieldMode(holder.getEntityWorld().getServer(), holderId, heldId, true);
        } else {
            holder.sendMessage(net.minecraft.text.Text.literal("§e THROW MODE"), true);
            held.sendMessage(net.minecraft.text.Text.literal("§eBack to throw mode"), true);

            held.stopRiding();

            net.minecraft.entity.decoration.ArmorStandEntity armorStand = shieldArmorStands.remove(holderId);
            if (armorStand != null) {
                armorStand.discard();
            }

            held.startRiding(holder);

            PoseNetworking.poseStates.put(heldId, PoseState.GRABBED);
            PoseNetworking.broadcastPoseChange(held.getEntityWorld().getServer(), heldId, PoseState.GRABBED);

            PoseNetworking.broadcastAnimState(holder, 3); // GRAB_HOLDING animation

            holder.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);

            EntityPassengersSetS2CPacket holderPacket = new EntityPassengersSetS2CPacket(holder);
            for (ServerPlayerEntity p : holder.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
                p.networkHandler.sendPacket(holderPacket);
            }

            holder.getEntityWorld().playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                    SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 1.0f, 1.0f);

            broadcastShieldMode(holder.getEntityWorld().getServer(), holderId, heldId, false);
        }

        return true;
    }

   
    public static void tickShieldMode(net.minecraft.server.MinecraftServer server) {
        var iterator = shieldArmorStands.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            UUID holderId = entry.getKey();
            net.minecraft.entity.decoration.ArmorStandEntity armorStand = entry.getValue();

            if (!shieldMode.getOrDefault(holderId, false)) {
                armorStand.discard();
                iterator.remove();
                continue;
            }

            ServerPlayerEntity holder = server.getPlayerManager().getPlayer(holderId);
            if (holder == null || armorStand.isRemoved()) {
                if (!armorStand.isRemoved()) armorStand.discard();
                iterator.remove();
                shieldMode.remove(holderId);
                continue;
            }

            UUID heldId = holding.get(holderId);
            if (heldId == null) {
                armorStand.discard();
                iterator.remove();
                shieldMode.remove(holderId);
                shieldSwapCooldown.remove(holderId);

                holder.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);

                PoseNetworking.poseStates.put(holderId, PoseState.NONE);
                PoseNetworking.broadcastPoseChange(holder.getEntityWorld().getServer(), holderId, PoseState.NONE);
                PoseNetworking.broadcastAnimState(holder, 0); // NONE animation

                GrabNetworking.broadcastGrabState(server, holderId, heldId, false);

                EntityPassengersSetS2CPacket packet = new EntityPassengersSetS2CPacket(holder);
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    p.networkHandler.sendPacket(packet);
                }

                holder.sendMessage(net.minecraft.text.Text.literal("§c Shield dropped!"), true);
                continue;
            }

            ServerPlayerEntity heldPlayer = server.getPlayerManager().getPlayer(heldId);
            if (heldPlayer == null || !heldPlayer.isAlive()) {
                armorStand.discard();
                iterator.remove();

                shieldMode.remove(holderId);
                shieldSwapCooldown.remove(holderId);
                holding.remove(holderId);
                heldBy.remove(heldId);

                holder.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);

                PoseNetworking.poseStates.put(holderId, PoseState.NONE);
                PoseNetworking.broadcastPoseChange(holder.getEntityWorld().getServer(), holderId, PoseState.NONE);
                PoseNetworking.broadcastAnimState(holder, 0); // NONE animation

                GrabNetworking.broadcastGrabState(server, holderId, heldId, false);

                EntityPassengersSetS2CPacket packet = new EntityPassengersSetS2CPacket(holder);
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    p.networkHandler.sendPacket(packet);
                }

                holder.sendMessage(net.minecraft.text.Text.literal("§c Shield died!"), true);
                continue;
            }

            double yaw = Math.toRadians(holder.getYaw());

            double forwardX = -Math.sin(yaw) * 0.8;  // 0.8 blocks forward (was 0.6)
            double forwardZ = Math.cos(yaw) * 0.8;

            double newX = holder.getX() + forwardX;
            double newY = holder.getY() - 0.5;  // 0.5 blocks LOWER (more crouched, was 0.3)
            double newZ = holder.getZ() + forwardZ;

            armorStand.setPosition(newX, newY, newZ);
            armorStand.setYaw(holder.getYaw()); // Face same direction as holder

            if (armorStand.getFirstPassenger() instanceof ServerPlayerEntity shieldPlayer) {
                float heldYaw = holder.getYaw();  // Face same direction as holder
                shieldPlayer.setYaw(heldYaw);
                shieldPlayer.setBodyYaw(heldYaw);
                shieldPlayer.setHeadYaw(heldYaw);

              
                PoseNetworking.AnimStateSyncPayload animPayload =
                        new PoseNetworking.AnimStateSyncPayload(shieldPlayer.getUuid(), 29); // SHIELD = ordinal 29
                ServerPlayNetworking.send(holder, animPayload);


                EntityPositionS2CPacket posPacket = new EntityPositionS2CPacket(shieldPlayer.getId(), net.minecraft.entity.EntityPosition.fromEntity(shieldPlayer), java.util.Set.of(), shieldPlayer.isOnGround());
                holder.networkHandler.sendPacket(posPacket);
                EntityPositionS2CPacket standPacket = new EntityPositionS2CPacket(armorStand.getId(), net.minecraft.entity.EntityPosition.fromEntity(armorStand), java.util.Set.of(), armorStand.isOnGround());
                holder.networkHandler.sendPacket(standPacket);
            }
        }
    }

  
    public static boolean isInShieldMode(UUID holderId) {
        return shieldMode.getOrDefault(holderId, false);
    }

    
    public static ServerPlayerEntity getShieldPlayer(ServerPlayerEntity holder) {
        if (!isInShieldMode(holder.getUuid())) return null;
        UUID heldId = holding.get(holder.getUuid());
        if (heldId == null) return null;
        return holder.getEntityWorld().getServer().getPlayerManager().getPlayer(heldId);
    }

  
    private static void broadcastShieldMode(net.minecraft.server.MinecraftServer server, UUID holderId, UUID heldId, boolean enabled) {
        ShieldModePayload payload = new ShieldModePayload(holderId, heldId, enabled);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

   
    public static void registerShieldDamageEvent() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            // Only handle player damage
            if (!(entity instanceof ServerPlayerEntity holder)) return true;

            if (isInShieldMode(holder.getUuid())) {
                ServerPlayerEntity shield = getShieldPlayer(holder);
                if (shield != null && shield.isAlive()) {
                    shield.clientDamage(source);
                    return false;
                }
            }

            return true;
        });
    }


    public record ShieldModePayload(UUID holderId, UUID heldId, boolean enabled) implements CustomPayload {
        public static final CustomPayload.Id<ShieldModePayload> ID =
                new CustomPayload.Id<>(Identifier.of("testcoop", "shield_mode"));

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            PayloadTypeRegistry.playS2C().register(ID, PacketCodec.of(
                    (payload, buf) -> {
                        buf.writeUuid(payload.holderId);
                        buf.writeUuid(payload.heldId);
                        buf.writeBoolean(payload.enabled);
                    },
                    buf -> new ShieldModePayload(buf.readUuid(), buf.readUuid(), buf.readBoolean())
            ));
        }
    }
}
