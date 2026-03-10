package com.cooptest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;


public class HeavenDapSoloCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("heavendapsolo")
                .executes(HeavenDapSoloCommand::execute));
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }

        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos().add(0, 1.4, 0); // Just at your location


        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_GLASS_BREAK, 
                SoundCategory.PLAYERS, 1.0f, 0.8f);
        
        player.sendMessage(Text.literal("§d§l HEAVEN READY"), true);
        

        // ========== STEP 2: 100-BLOCK TNT EXPLOSION ==========
        world.createExplosion(null, pos.x, pos.y, pos.z, 100.0f, true,
                World.ExplosionSourceType.TNT);
        

        spawnExpandingLegendarySonicBoom(world, pos);
        
        startContinuousParticles(world, pos, 30000);
        

        // ========== STEP 5: TP TO HEAVEN ==========
        teleportToHeaven(player, world, pos);
        
        // Success messages

        return 1;
    }

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


            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }


    private static void spawnExpandingRing(ServerWorld world, Vec3d center, double startRadius, double endRadius, int durationMs) {
        int steps = 20;
        double radiusStep = (endRadius - startRadius) / steps;
        long stepDuration = durationMs / steps;

        new Thread(() -> {
            try {
                for (int step = 0; step < steps; step++) {
                    double radius = startRadius + (radiusStep * step);
                    final double currentRadius = radius;

                    world.getServer().execute(() -> {
                        int particleCount = (int) (currentRadius * 20);
                        for (int i = 0; i < particleCount; i++) {
                            double angle = Math.toRadians((360.0 / particleCount) * i);

                            double x = center.x + Math.cos(angle) * currentRadius;
                            double z = center.z + Math.sin(angle) * currentRadius;
                            double y = center.y + 0.5;

                            double vx = Math.cos(angle) * 0.4;
                            double vz = Math.sin(angle) * 0.4;

                            world.spawnParticles(ParticleTypes.CLOUD,
                                    x, y, z, 0, vx, 0.0, vz, 0.6);
                            world.spawnParticles(ParticleTypes.WHITE_ASH,
                                    x, y, z, 0, vx, 0.0, vz, 0.5);

                            if (i % (particleCount / 24) == 0) {
                                world.spawnParticles(ParticleTypes.END_ROD,
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


    private static void startContinuousParticles(ServerWorld world, Vec3d pos, long durationMs) {
        long endTime = System.currentTimeMillis() + durationMs;
        
        new Thread(() -> {
            try {
                while (System.currentTimeMillis() < endTime) {
                    world.getServer().execute(() -> {
                        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 2, 2, 2, 2, 0);
                        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 10, 3, 3, 3, 0.3);
                        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 5, 2, 2, 2, 0.2);
                        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
                    });
                    
                    Thread.sleep(50); // Spawn every tick
                }
                

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }


    private static void teleportToHeaven(ServerPlayerEntity player, ServerWorld world, Vec3d groundPos) {
        final Vec3d originalPos = player.getPos();
        
        Vec3d heavenPos = new Vec3d(groundPos.x, groundPos.y + 1000, groundPos.z);
        player.teleport(world, heavenPos.x, heavenPos.y, heavenPos.z, player.getYaw(), player.getPitch());

        
        // Return after 3 seconds
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                
                world.getServer().execute(() -> {
                    player.teleport(world, originalPos.x, originalPos.y, originalPos.z, player.getYaw(), player.getPitch());

                });
                
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
