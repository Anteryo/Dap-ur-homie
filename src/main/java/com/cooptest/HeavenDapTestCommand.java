package com.cooptest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;
import java.util.UUID;


public class HeavenDapTestCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("heavendaptest")
                .executes(HeavenDapTestCommand::execute));
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("NEED player!"));
            return 0;
        }

        // Find nearest other player
        ServerPlayerEntity partner = findNearestPlayer(player);
        if (partner == null) {
            context.getSource().sendError(Text.literal("§c2 PLATEr."));
            return 0;
        }

        UUID playerId = player.getUuid();
        UUID partnerId = partner.getUuid();
        long now = System.currentTimeMillis();
        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos().add(partner.getPos()).multiply(0.5).add(0, 1.4, 0);

        context.getSource().sendFeedback(() -> Text.literal("§d§lHEAVEN DAP TEST ACTIVATED!"), true);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sound.SoundEvents.BLOCK_GLASS_BREAK,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 0.8f);

        world.playSound(null, partner.getX(), partner.getY(), partner.getZ(),
                net.minecraft.sound.SoundEvents.BLOCK_GLASS_BREAK,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 0.8f);

        player.sendMessage(Text.literal("§d§l HEAVEN READY!  §7(Test command)"), true);
        partner.sendMessage(Text.literal("§d§l HEAVEN READY!  §7(Test command)"), true);

        System.out.println("[HeavenDapTest] Glass breaking sound played!");

        try {
            Method method = ChargedDapHandler.class.getDeclaredMethod(
                    "startHeavenDap",
                    ServerPlayerEntity.class,
                    ServerPlayerEntity.class,
                    Vec3d.class,
                    ServerWorld.class
            );
            method.setAccessible(true);
            method.invoke(null, player, partner, pos, world);

            context.getSource().sendFeedback(() -> Text.literal("§a✅ Heaven Dap triggered successfully!"), false);
            context.getSource().sendFeedback(() -> Text.literal(""), false);
            context.getSource().sendFeedback(() -> Text.literal("§6🔥 Effects:"), false);
            context.getSource().sendFeedback(() -> Text.literal("§7  ✓ Glass breaking sound"), false);
            context.getSource().sendFeedback(() -> Text.literal("§7  ✓ 100-block TNT explosion"), false);
            context.getSource().sendFeedback(() -> Text.literal("§7  ✓ Expanding sonic boom rings"), false);
            context.getSource().sendFeedback(() -> Text.literal("§7  ✓ 30 seconds of continuous particles"), false);
            context.getSource().sendFeedback(() -> Text.literal("§7  ✓ TP to heaven (returning to ground...)"), false);

            System.out.println("[HeavenDapTest] Heaven dap triggered for " + player.getName().getString() + " and " + partner.getName().getString());

        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§cNOT WORKING: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }

        return 1;
    }


    private static ServerPlayerEntity findNearestPlayer(ServerPlayerEntity player) {
        ServerPlayerEntity nearest = null;
        double minDistance = 10.0;

        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other == player) continue;

            double distance = player.getPos().distanceTo(other.getPos());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = other;
            }
        }

        return nearest;
    }
}