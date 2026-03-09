package com.cooptest;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class PushInteractionHandler {

    private static final HashMap<UUID, Long> cooldowns = new HashMap<>();
    public static final HashMap<UUID, Long> pushImmunity = new HashMap<>();
    private static final long PUSH_IMMUNITY_MS = 500;

    // JUMP PUSH PERMSSION
    public static final HashMap<UUID, Long> lastJumpTime = new HashMap<>();
    private static final long JUMP_WINDOW_MS = 1000;

    private static final HashMap<UUID, PushRequest> pendingJumpPush = new HashMap<>();
    private static final long REQUEST_TIMEOUT_MS = 5000;

    private static class PushRequest {
        UUID pusher;
        double velocity;
        long timestamp;

        PushRequest(UUID pusher, double velocity) {
            this.pusher = pusher;
            this.velocity = velocity;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static final Identifier PUSH_ANIM_ID = Identifier.of("cooptest", "push_anim");

    public record PushAnimPayload(UUID playerId) implements CustomPayload {
        public static final Id<PushAnimPayload> ID = new Id<>(PUSH_ANIM_ID);
        public static final PacketCodec<PacketByteBuf, PushAnimPayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> buf.writeUuid(payload.playerId),
                        buf -> new PushAnimPayload(buf.readUuid())
                );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(PushAnimPayload.ID, PushAnimPayload.CODEC);
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(entity instanceof PlayerEntity target)) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (!(target instanceof ServerPlayerEntity serverTarget)) return ActionResult.PASS;

            long now = System.currentTimeMillis();

            PushRequest request = pendingJumpPush.get(player.getUuid());
            if (request != null && request.pusher.equals(target.getUuid())) {

                if (now - request.timestamp > REQUEST_TIMEOUT_MS) {
                    pendingJumpPush.remove(player.getUuid());
                    serverPlayer.sendMessage(net.minecraft.text.Text.literal("§cRequest expired!"), true);
                    return ActionResult.FAIL;
                }

                if (player.distanceTo(target) > 2.5f) {
                    pendingJumpPush.remove(player.getUuid());
                    serverPlayer.sendMessage(net.minecraft.text.Text.literal("§cToo far away!"), true);
                    return ActionResult.FAIL;
                }

                pendingJumpPush.remove(player.getUuid());
                serverPlayer.sendMessage(net.minecraft.text.Text.literal("§a✓ JUMP PUSH accepted!"), true);
                serverTarget.sendMessage(net.minecraft.text.Text.literal("§a✓ " + serverPlayer.getName().getString() + " accepted!"), true);

                executePush(serverTarget, serverPlayer, request.velocity, "§6§lJUMP PUSH!", now);
                return ActionResult.SUCCESS;
            }

            PoseState pose = PoseNetworking.poseStates.getOrDefault(player.getUuid(), PoseState.NONE);

            if (pose == PoseState.GRAB_READY || pose == PoseState.GRAB_HOLDING) return ActionResult.PASS;
            if (HighFiveHandler.isInBlockingState(player.getUuid())) return ActionResult.PASS;
            if (pose != PoseState.PUSH_IDLE) return ActionResult.PASS;
            if (player.distanceTo(target) > 2.5f) return ActionResult.PASS;

            if (cooldowns.containsKey(player.getUuid()) && now - cooldowns.get(player.getUuid()) < 3000) return ActionResult.FAIL;
            if (cooldowns.containsKey(target.getUuid()) && now - cooldowns.get(target.getUuid()) < 3000) return ActionResult.FAIL;

            // DETERMINE PUSH POWER
            double baseLaunchVelocity;
            String pushType;

            Long jumpTime = lastJumpTime.get(player.getUuid());
            boolean jumped = jumpTime != null && (now - jumpTime) < JUMP_WINDOW_MS;

            if (jumped) {
                baseLaunchVelocity = 10.0;  // 40+ blocks!
                pushType = "§6§lJUMP PUSH!";
            } else if (player.isSneaking()) {
                baseLaunchVelocity = 2.0;  // 4 blocks
                pushType = "§7Gentle Push";
            } else if (player.isSprinting()) {
                baseLaunchVelocity = 8.0;  // 30 blocks
                pushType = "§c§lMEGA PUSH!";
            } else {
                baseLaunchVelocity = 6.0;  // 20 blocks
                pushType = "§ePush";
            }

            // ALL PUSHES REQUIRE PERMISSION!

            // PREVENT SPAM: Check if request already exists
            if (pendingJumpPush.containsKey(target.getUuid())) {
                // Already have a pending request for this target
                return ActionResult.FAIL;
            }

            double velocity = calculateVelocity(serverTarget, baseLaunchVelocity);
            pendingJumpPush.put(target.getUuid(), new PushRequest(player.getUuid(), velocity));

            serverPlayer.sendMessage(net.minecraft.text.Text.literal("§e⚡ Request sent to " + serverTarget.getName().getString() + "!"), false);
            serverTarget.sendMessage(net.minecraft.text.Text.literal("§e⚡ " + serverPlayer.getName().getString() + " wants to push you! §aRight-click them to accept!"), true);

            return ActionResult.SUCCESS;
        });
    }

    private static double calculateVelocity(ServerPlayerEntity target, double base) {
        int ceiling = getCeilingHeight(target);
        if (ceiling < 10 && ceiling > 0) {
            double maxH = Math.max(2, ceiling - 1);
            double v = Math.sqrt(2 * 0.08 * 20 * maxH);
            return Math.min(v, base);
        }
        return base;
    }

    private static void executePush(ServerPlayerEntity pusher, ServerPlayerEntity target, double velocity, String type, long now) {
        PoseEffects.playActionEffects(pusher, target);
        LaunchedPlayerTracker.markPlayerAsLaunched(target.getUuid());

        PushAnimPayload payload = new PushAnimPayload(pusher.getUuid());
        for (ServerPlayerEntity p : PlayerLookup.tracking(pusher)) {
            ServerPlayNetworking.send(p, payload);
        }
        ServerPlayNetworking.send(pusher, payload);

        UUID carried = GrabMechanic.holding.get(target.getUuid());
        if (carried != null) {
            ServerPlayerEntity c = target.getServer().getPlayerManager().getPlayer(carried);
            if (c != null) {
                LaunchedPlayerTracker.markPlayerAsLaunched(c.getUuid());
                c.addVelocity(0, velocity + 0.2, 0);
                c.velocityModified = true;
                pushImmunity.put(c.getUuid(), now);
            }
        }

        target.addVelocity(0, velocity, 0);
        target.velocityModified = true;
        pushImmunity.put(target.getUuid(), now);

        cooldowns.put(pusher.getUuid(), now);
        cooldowns.put(target.getUuid(), now);

        PoseNetworking.broadcastPoseChange(
                Objects.requireNonNull(pusher.getServer()),
                pusher.getUuid(),
                PoseState.PUSH_ACTION
        );

        pusher.sendMessage(net.minecraft.text.Text.literal(type), true);
    }

    public static boolean hasPushImmunity(UUID uuid) {
        Long t = pushImmunity.get(uuid);
        if (t == null) return false;
        if (System.currentTimeMillis() - t < PUSH_IMMUNITY_MS) return true;
        pushImmunity.remove(uuid);
        return false;
    }

    private static int getCeilingHeight(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        for (int y = 1; y <= 15; y++) {
            BlockPos check = pos.up(y);
            BlockState state = player.getWorld().getBlockState(check);
            if (!state.isAir() && state.isSolidBlock(player.getWorld(), check)) {
                return y;
            }
        }
        return -1;
    }

    public static void cleanupExpiredImmunity() {
        long now = System.currentTimeMillis();
        pushImmunity.entrySet().removeIf(e -> now - e.getValue() > PUSH_IMMUNITY_MS);
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        long now = System.currentTimeMillis();

        // Track jumps
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!p.isOnGround() && p.getVelocity().y > 0.1) {
                lastJumpTime.put(p.getUuid(), now);
            }
        }

        // Cleanup
        pendingJumpPush.entrySet().removeIf(e -> {
            if (now - e.getValue().timestamp > REQUEST_TIMEOUT_MS) {
                ServerPlayerEntity t = server.getPlayerManager().getPlayer(e.getKey());
                if (t != null) t.sendMessage(net.minecraft.text.Text.literal("§cRequest expired!"), true);
                return true;
            }
            return false;
        });

        lastJumpTime.entrySet().removeIf(e -> now - e.getValue() > 2000);
    }
}