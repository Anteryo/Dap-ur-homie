package com.cooptest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Mario Jump System
 * Press SPACE while standing on another player's head to get launched 2.3 blocks up
 * - Jumper plays "jumpmario" animation (ordinal 30)
 * - Target plays "pop" animation (ordinal 31)
 * - Animations auto-reset to NONE after finishing
 */
public class MarioJumpHandler {

    private static final Map<UUID, Long> jumpCooldown = new HashMap<>();
    private static final long COOLDOWN_MS = 500; // 0.5 second cooldown

    private static final Map<UUID, Long> marioAnimEnd = new HashMap<>();
    private static final Map<UUID, Long> popAnimEnd = new HashMap<>();
    private static final long MARIO_ANIM_DURATION_MS = 500;  // 0.5 seconds
    private static final long POP_ANIM_DURATION_MS = 417;    // 0.4167 seconds

    private static final double LAUNCH_VELOCITY = 0.68;

    // ====== PAYLOADS ==================================================

    /**
     * Client -> Server: Request mario jump (player pressed SPACE)
     */
    public record MarioJumpRequestPayload() implements CustomPayload {
        public static final Id<MarioJumpRequestPayload> ID =
                new Id<>(Identifier.of("testcoop", "mario_jump_request"));
        public static final PacketCodec<PacketByteBuf, MarioJumpRequestPayload> CODEC =
                PacketCodec.unit(new MarioJumpRequestPayload());

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }



    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(MarioJumpRequestPayload.ID, MarioJumpRequestPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(MarioJumpRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> onMarioJumpRequest(player));
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();

            // Reset MARIO_JUMP animations
            Iterator<Map.Entry<UUID, Long>> marioIt = marioAnimEnd.entrySet().iterator();
            while (marioIt.hasNext()) {
                Map.Entry<UUID, Long> entry = marioIt.next();
                if (now >= entry.getValue()) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                    if (player != null) {
                        PoseNetworking.broadcastAnimState(player, 0); // NONE
                    }
                    marioIt.remove();
                }
            }

            Iterator<Map.Entry<UUID, Long>> popIt = popAnimEnd.entrySet().iterator();
            while (popIt.hasNext()) {
                Map.Entry<UUID, Long> entry = popIt.next();
                if (now >= entry.getValue()) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                    if (player != null) {
                        PoseNetworking.broadcastAnimState(player, 0); // NONE
                    }
                    popIt.remove();
                }
            }
        });
    }

    // ==================== LOGIC ====================

    /**
     * Handle mario jump request from client
     */
    private static void onMarioJumpRequest(ServerPlayerEntity jumper) {
        if (jumper == null) return;

        UUID jumperId = jumper.getUuid();
        long now = System.currentTimeMillis();

        // Check cooldown
        if (jumpCooldown.containsKey(jumperId)) {
            if (now - jumpCooldown.get(jumperId) < COOLDOWN_MS) {
                return;
            }
        }


        if (HighFiveHandler.isInBlockingState(jumperId)) {
            return;
        }


        ServerPlayerEntity target = findPlayerBelow(jumper);
        if (target == null) {
            return;
        }

        // mario jumpyeepy!
        executeMarioJump(jumper, target);
        jumpCooldown.put(jumperId, now);
    }

    /**
     * Find a player whose head we're standing on
     */
    private static ServerPlayerEntity findPlayerBelow(ServerPlayerEntity jumper) {
        ServerWorld world = jumper.getServerWorld();
        Vec3d jumperPos = jumper.getPos();
        double jumperFeetY = jumperPos.y;

        Box searchBox = new Box(
                jumperPos.x - 0.8, jumperPos.y - 2.5, jumperPos.z - 0.8,
                jumperPos.x + 0.8, jumperPos.y + 0.5, jumperPos.z + 0.8
        );

        List<ServerPlayerEntity> nearby = world.getEntitiesByClass(
                ServerPlayerEntity.class, searchBox,
                p -> p != jumper && p.isAlive()
        );

        for (ServerPlayerEntity target : nearby) {
            Vec3d targetPos = target.getPos();
            double targetHeadY = targetPos.y + target.getStandingEyeHeight() + 0.15;

            double heightDiff = jumperFeetY - targetHeadY;
            if (heightDiff >= -0.35 && heightDiff <= 0.5) {
                double horizDist = Math.sqrt(
                        Math.pow(jumperPos.x - targetPos.x, 2) +
                                Math.pow(jumperPos.z - targetPos.z, 2)
                );
                if (horizDist <= 0.7) {
                    return target;
                }
            }
        }

        return null;
    }

    /**
     *  the mario jump
     */
    private static void executeMarioJump(ServerPlayerEntity jumper, ServerPlayerEntity target) {
        ServerWorld world = jumper.getServerWorld();
        Vec3d pos = jumper.getPos();
        long now = System.currentTimeMillis();

        // DEBUG: Log animation triggers I HATE THIS
        System.out.println("[MARIO JUMP] Executing mario jump!");
        System.out.println("[MARIO JUMP] Jumper: " + jumper.getName().getString() + " (UUID: " + jumper.getUuid() + ")");
        System.out.println("[MARIO JUMP] Target: " + target.getName().getString() + " (UUID: " + target.getUuid() + ")");
        System.out.println("[MARIO JUMP] Broadcasting MARIO_JUMP (ordinal 30) to jumper");
        System.out.println("[MARIO JUMP] Broadcasting POP (ordinal 31) to target");

        Vec3d velocity = jumper.getVelocity();
        jumper.setVelocity(velocity.x, LAUNCH_VELOCITY, velocity.z);
        jumper.velocityModified = true;
        PoseNetworking.broadcastAnimState(jumper, 30); // MARIO_JUMP
        PoseNetworking.broadcastAnimState(target, 31); // POP

        System.out.println("[MARIO JUMP] Animations broadcast complete IT WORKING FINALLY!");

        marioAnimEnd.put(jumper.getUuid(), now + MARIO_ANIM_DURATION_MS);
        popAnimEnd.put(target.getUuid(), now + POP_ANIM_DURATION_MS);

        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.MARIO_JUMP, SoundCategory.PLAYERS, 1.0f, 1.0f);

        // Messages
        jumper.sendMessage(net.minecraft.text.Text.literal("§a WAHOO!"), true);
        target.sendMessage(net.minecraft.text.Text.literal("§c BONK!"), true);
    }

    /**
     * Cleanup on disconnect
     */
    public static void cleanup(UUID playerId) {
        jumpCooldown.remove(playerId);
        marioAnimEnd.remove(playerId);
        popAnimEnd.remove(playerId);
    }
}