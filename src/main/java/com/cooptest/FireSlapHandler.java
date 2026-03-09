package com.cooptest;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;

/**
UNDER CONSTRUCTION
 */
public class FireSlapHandler {

    private static final float MIN_FIRE_LEVEL = 0.95f;

    private static final double SLAP_KNOCKBACK = 2.5;
    private static final double SLAP_VERTICAL = 0.5;

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;
            if (entity instanceof PlayerEntity) return ActionResult.PASS; // Don't slap players

            float fireLevel = ChargedDapHandler.fireLevel.getOrDefault(player.getUuid(), 0f);
            if (fireLevel < MIN_FIRE_LEVEL) return ActionResult.PASS;

            executeFireSlap(serverPlayer, target);

            return ActionResult.PASS;
        });
    }

    private static void executeFireSlap(ServerPlayerEntity player, LivingEntity target) {
        ServerWorld world = player.getServerWorld();
        Vec3d playerPos = player.getPos();
        Vec3d targetPos = target.getPos();

        Vec3d direction = targetPos.subtract(playerPos).normalize();

        target.setVelocity(
                direction.x * SLAP_KNOCKBACK,
                SLAP_VERTICAL,
                direction.z * SLAP_KNOCKBACK
        );
        target.velocityModified = true;

        target.setOnFireFor(2);

        // Spawn fire particles at impact
        double x = target.getX();
        double y = target.getY() + target.getHeight() / 2;
        double z = target.getZ();

        world.spawnParticles(ParticleTypes.FLAME, x, y, z, 8, 0.3, 0.3, 0.3, 0.05);
        world.spawnParticles(ParticleTypes.SMOKE, x, y, z, 5, 0.2, 0.2, 0.2, 0.02);
        world.spawnParticles(ParticleTypes.CRIT, x, y, z, 6, 0.3, 0.3, 0.3, 0.1);

        world.playSound(null, x, y, z,
                SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.playSound(null, x, y, z,
                SoundEvents.ENTITY_BLAZE_HURT, SoundCategory.PLAYERS, 0.5f, 1.2f);

        player.sendMessage(net.minecraft.text.Text.literal("§c FIRE SLAP! "), true);

        ChargedDapHandler.fireLevel.put(player.getUuid(), 0.5f);
    }
}