package com.cooptest;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Usage: /testdivine
 */
public class DivineFlamTestCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("testdivine")
                .executes(context -> {
                    ServerCommandSource source = context.getSource();
                    ServerPlayerEntity player = source.getPlayer();

                    if (player == null) {
                        source.sendError(net.minecraft.text.Text.literal("Must be a player!"));
                        return 0;
                    }

                    ServerWorld world = player.getServerWorld();
                    Vec3d pos = player.getPos().add(0, 1, 0); // Spawn at player eye level
                    Vec3d direction = player.getRotationVector(); // Move in direction player is facing

                    // Create test pillar
                    TestPillar pillar = new TestPillar(world, pos, direction);

                    // Store in a list that gets ticked
                    TestPillarTicker.addPillar(pillar);

                    source.sendFeedback(() -> net.minecraft.text.Text.literal("§a✓ Divine Flame pillar spawned! It will move forward for 5 seconds."), false);

                    return 1;
                })
        );
    }

    /**
     * Test pillar (copy of the real one)
     */
    public static class TestPillar {
        private final ServerWorld world;
        private Vec3d position;
        private final Vec3d direction;
        private int ticksAlive = 0;
        private static final int MAX_LIFETIME = 100; // 5 seconds

        public TestPillar(ServerWorld world, Vec3d startPos, Vec3d direction) {
            this.world = world;
            this.position = startPos;
            this.direction = direction.normalize();
            System.out.println("[Test Pillar] Spawned at " + startPos + " moving in direction " + direction);
        }

        public void tick() {
            ticksAlive++;

            if (ticksAlive % 20 == 0) {
                System.out.println("[FIRE VORTEX] Tick " + ticksAlive);
            }

            // FIRE VORTEX - OPTIMIZED FOR PERFORMANCE
            double MAX_HEIGHT = 60.0;
            double BASE_RADIUS = 20.0;
            double TOP_RADIUS = 3.0;
            double SAFE_RADIUS = 1.5;

            double baseRotation = (ticksAlive * 0.3) % (Math.PI * 2);

            // GROUND SPINNING - Dense at ground
            for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                for (double radius = 3.0; radius <= BASE_RADIUS; radius += 2.0) {
                    double spinAngle = angle + baseRotation;
                    double x = position.x + Math.cos(spinAngle) * radius;
                    double z = position.z + Math.sin(spinAngle) * radius;

                    world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                            x, position.y + 0.5, z, 2, 0.3, 0.2, 0.3, 0.03);

                    if (radius > BASE_RADIUS * 0.8) {
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.LAVA,
                                x, position.y, z, 1, 0.2, 0.05, 0.2, 0);
                    }
                }
            }

            // CENTER - Light fire
            if (ticksAlive % 3 == 0) {
                for (int i = 0; i < 5; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double radius = Math.random() * SAFE_RADIUS * 0.8;
                    double x = position.x + Math.cos(angle) * radius;
                    double z = position.z + Math.sin(angle) * radius;

                    world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                            x, position.y + 0.3, z, 1, 0.1, 0.1, 0.1, 0.01);
                }
            }

            // VERTICAL - Every 2 blocks
            for (double y = 0; y <= MAX_HEIGHT; y += 2.0) {
                double worldY = position.y + y;
                double heightProgress = y / MAX_HEIGHT;
                double currentRadius = BASE_RADIUS - (heightProgress * (BASE_RADIUS - TOP_RADIUS));
                double rotationOffset = heightProgress * Math.PI * 4;

                int particlesPerRing = (int) (8 - (heightProgress * 4));
                double angleStep = (Math.PI * 2) / particlesPerRing;

                for (int i = 0; i < particlesPerRing; i++) {
                    double angle = baseRotation + rotationOffset + (i * angleStep);
                    double x = position.x + Math.cos(angle) * currentRadius;
                    double z = position.z + Math.sin(angle) * currentRadius;

                    if (y < 15) {
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                                x, worldY, z, 3, 0.3, 0.3, 0.3, 0.04);
                    } else if (y < 40) {
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                                x, worldY, z, 2, 0.25, 0.25, 0.25, 0.03);
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                                x, worldY, z, 1, 0.2, 0.2, 0.2, 0.02);
                    } else {
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                                x, worldY, z, 1, 0.2, 0.2, 0.2, 0.02);
                        if (y > 50) {
                            world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                                    x, worldY, z, 1, 0.2, 0.2, 0.2, 0.01);
                        }
                    }
                }
            }

            // SPIRALS
            for (int stream = 0; stream < 3; stream++) {
                double streamAngle = baseRotation + (stream * Math.PI * 2 / 3);
                for (double y = 0; y <= MAX_HEIGHT; y += 3.0) {
                    double heightProgress = y / MAX_HEIGHT;
                    double currentRadius = BASE_RADIUS - (heightProgress * (BASE_RADIUS - TOP_RADIUS));
                    double spiralAngle = streamAngle + (heightProgress * Math.PI * 6);
                    double x = position.x + Math.cos(spiralAngle) * currentRadius * 0.9;
                    double z = position.z + Math.sin(spiralAngle) * currentRadius * 0.9;
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                            x, position.y + y, z, 2, 0.15, 0.15, 0.15, 0.03);
                }
            }

            // Sounds
            if (ticksAlive % 10 == 0) {
                world.playSound(null, position.x, position.y, position.z,
                        net.minecraft.sound.SoundEvents.BLOCK_FIRE_AMBIENT,
                        net.minecraft.sound.SoundCategory.PLAYERS, 6.0f, 0.7f);
            }
            if (ticksAlive == 1) {
                world.playSound(null, position.x, position.y, position.z,
                        net.minecraft.sound.SoundEvents.ENTITY_BLAZE_SHOOT,
                        net.minecraft.sound.SoundCategory.PLAYERS, 10.0f, 0.4f);
            }

            // Damage
            for (net.minecraft.entity.Entity entity : world.getOtherEntities(null,
                    net.minecraft.util.math.Box.of(position, BASE_RADIUS * 2, MAX_HEIGHT * 2, BASE_RADIUS * 2))) {
                if (entity instanceof net.minecraft.entity.LivingEntity living) {
                    double distance2D = Math.sqrt(
                            Math.pow(entity.getX() - position.x, 2) +
                                    Math.pow(entity.getZ() - position.z, 2)
                    );
                    double heightDiff = Math.abs(entity.getY() - position.y);

                    if (heightDiff <= MAX_HEIGHT) {
                        double heightProgress = heightDiff / MAX_HEIGHT;
                        double radiusAtHeight = BASE_RADIUS - (heightProgress * (BASE_RADIUS - TOP_RADIUS));

                        if (distance2D > SAFE_RADIUS && distance2D < radiusAtHeight) {
                            living.damage(world.getDamageSources().onFire(), 30.0f);
                            living.setOnFireFor(10);
                        }
                    }
                }
            }
        }

        public boolean shouldRemove() {
            return ticksAlive >= MAX_LIFETIME;
        }
    }

    public static class TestPillarTicker {
        private static final java.util.List<TestPillar> pillars = new java.util.ArrayList<>();

        public static void addPillar(TestPillar pillar) {
            pillars.add(pillar);
        }

        public static void tick() {
            java.util.Iterator<TestPillar> it = pillars.iterator();
            while (it.hasNext()) {
                TestPillar pillar = it.next();
                pillar.tick();

                if (pillar.shouldRemove()) {
                    System.out.println("[Test Pillar] Removed after " + pillar.ticksAlive + " ticks");
                    it.remove();
                }
            }
        }
    }
}