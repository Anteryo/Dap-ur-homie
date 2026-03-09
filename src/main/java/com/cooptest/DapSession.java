package com.cooptest;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;


public class DapSession {

    private final UUID playerAId;
    private final UUID playerBId;
    private Vec3d targetA;
    private Vec3d targetB;

    private final long startTime;
    private int tickCount;
    private boolean positioningComplete;

    private final double targetDistance;  
    private final double lerpSpeed;       
    private final DapType type;           

    private Runnable onPositioningComplete;

    public enum DapType {
        NORMAL_DAP,     
        PERFECT_DAP,     
        FIRE_DAP,
        FIRE_COMBO,
        DAP_HOLD
    }

  
    public DapSession(UUID playerA, UUID playerB, double targetDistance, DapType type) {
        this.playerAId = playerA;
        this.playerBId = playerB;
        this.targetDistance = targetDistance;
        this.type = type;
        this.startTime = System.currentTimeMillis();
        this.tickCount = 0;
        this.positioningComplete = false;

        if (type == DapType.PERFECT_DAP) {
            this.lerpSpeed = 0.95;  
        } else {
            this.lerpSpeed = 0.75;  // Very fast speed (ends at ~0.75 of animation)
        }

        System.out.println("[DapSession] Created session: " + type + " | Distance: " + targetDistance + " | Speed: " + lerpSpeed);
    }

   
    public void onComplete(Runnable callback) {
        this.onPositioningComplete = callback;
    }

 
    public void tick(MinecraftServer server) {
        ServerPlayerEntity playerA = server.getPlayerManager().getPlayer(playerAId);
        ServerPlayerEntity playerB = server.getPlayerManager().getPlayer(playerBId);

        // Safety checks
        if (playerA == null || playerB == null) {
            System.out.println("[DapSession] Player null - removing session");
            return;
        }

        if (playerA.isRemoved() || playerB.isRemoved()) {
            System.out.println("[DapSession] Player removed - removing session");
            return;
        }

        // Timeout safety (5 seconds)
        if (tickCount > 100) {
            System.out.println("[DapSession] Timeout - forcing completion");
            forceComplete(playerA, playerB);
            return;
        }

        freezePlayers(playerA, playerB);
        computeTargets(playerA, playerB);
        smoothMoveToTargets(playerA, playerB);
        makeFaceEachOther(playerA, playerB);
        playerA.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        playerB.swingHand(net.minecraft.util.Hand.MAIN_HAND);

        if (!positioningComplete) {
            checkPositioningComplete(playerA, playerB);
        }

        tickCount++;
    }

   
    private void freezePlayers(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        // Stop velocity completely
        playerA.setVelocity(Vec3d.ZERO);
        playerB.setVelocity(Vec3d.ZERO);
        playerA.velocityModified = true;
        playerB.velocityModified = true;

        playerA.fallDistance = 0;
        playerB.fallDistance = 0;
    }

   
    private void computeTargets(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        Vec3d posA = playerA.getPos();
        Vec3d posB = playerB.getPos();

        Vec3d midpoint = posA.add(posB).multiply(0.5);

        Vec3d direction = posB.subtract(posA);
        if (direction.length() < 0.001) {
            direction = new Vec3d(1, 0, 0);
        }
        direction = direction.normalize();

        double halfDistance = targetDistance / 2.0;
        targetA = midpoint.subtract(direction.multiply(halfDistance));
        targetB = midpoint.add(direction.multiply(halfDistance));

        double targetY = Math.max(posA.y, posB.y);

        ServerWorld world = playerA.getServerWorld();
        BlockPos groundPos = new BlockPos((int)midpoint.x, (int)targetY - 1, (int)midpoint.z);
        if (world.getBlockState(groundPos).isAir()) {
            targetY = Math.min(posA.y, posB.y);
        }

        targetA = new Vec3d(targetA.x, targetY, targetA.z);
        targetB = new Vec3d(targetB.x, targetY, targetB.z);
    }

    
    private void smoothMoveToTargets(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        Vec3d currentA = playerA.getPos();
        Vec3d currentB = playerB.getPos();

        Vec3d newPosA = new Vec3d(
                lerp(currentA.x, targetA.x, lerpSpeed),
                lerp(currentA.y, targetA.y, lerpSpeed),
                lerp(currentA.z, targetA.z, lerpSpeed)
        );

        Vec3d newPosB = new Vec3d(
                lerp(currentB.x, targetB.x, lerpSpeed),
                lerp(currentB.y, targetB.y, lerpSpeed),
                lerp(currentB.z, targetB.z, lerpSpeed)
        );

        
        playerA.teleport(playerA.getServerWorld(), newPosA.x, newPosA.y, newPosA.z,
                playerA.getYaw(), playerA.getPitch());
        playerB.teleport(playerB.getServerWorld(), newPosB.x, newPosB.y, newPosB.z,
                playerB.getYaw(), playerB.getPitch());
    }

   
    private void makeFaceEachOther(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        Vec3d posA = playerA.getPos();
        Vec3d posB = playerB.getPos();

        // Calculate yaw to face each other
        double dx = posB.x - posA.x;
        double dz = posB.z - posA.z;
        float yawA = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        float yawB = yawA + 180;  // Opposite direction

        playerA.setYaw(yawA);
        playerA.setBodyYaw(yawA);
        playerA.setHeadYaw(yawA);
        playerA.prevYaw = yawA;  // Prevent interpolation
        playerA.prevBodyYaw = yawA;  // Prevent body lag
        playerA.prevHeadYaw = yawA;

        playerB.setYaw(yawB);
        playerB.setBodyYaw(yawB);
        playerB.setHeadYaw(yawB);
        playerB.prevYaw = yawB;  // Prevent interpolation
        playerB.prevBodyYaw = yawB;  // Prevent body lag
        playerB.prevHeadYaw = yawB;

        playerA.teleport(playerA.getServerWorld(), posA.x, posA.y, posA.z, yawA, playerA.getPitch());
        playerB.teleport(playerB.getServerWorld(), posB.x, posB.y, posB.z, yawB, playerB.getPitch());
    }

    
    private void checkPositioningComplete(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        double distA = playerA.getPos().distanceTo(targetA);
        double distB = playerB.getPos().distanceTo(targetB);

        double threshold = (type == DapType.PERFECT_DAP) ? 0.35 : 0.25;
        if (distA < threshold && distB < threshold) {
            positioningComplete = true;
            System.out.println("[DapSession] Positioning complete! (" + tickCount + " ticks)");

            if (onPositioningComplete != null) {
                onPositioningComplete.run();
            }

            this.tickCount = 99;  
        }
    }

    
    private void forceComplete(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        if (!positioningComplete) {
            positioningComplete = true;
            System.out.println("[DapSession] Force completed");

            if (onPositioningComplete != null) {
                onPositioningComplete.run();
            }
        }
    }

   
    private double lerp(double current, double target, double factor) {
        return current + (target - current) * factor;
    }

    public UUID getPlayerAId() { return playerAId; }
    public UUID getPlayerBId() { return playerBId; }
    public boolean isPositioningComplete() { return positioningComplete; }
    public int getTickCount() { return tickCount; }
    public DapType getType() { return type; }
    public long getStartTime() { return startTime; }
    public Vec3d getTargetA() { return targetA; }
    public Vec3d getTargetB() { return targetB; }
}
