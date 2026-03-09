package com.cooptest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;


public class DebugQTECommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("debugqte")
                        .executes(ctx -> executeSolo(ctx, 1))
                        .then(CommandManager.argument("stages", IntegerArgumentType.integer(1, 3))
                                .executes(ctx -> executeSolo(ctx, IntegerArgumentType.getInteger(ctx, "stages")))
                        )
        );
    }

    private static int executeSolo(CommandContext<ServerCommandSource> context, int stages) {
        ServerCommandSource source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Only players can use this command!"));
            return 0;
        }

        if (QTEManager.isInQTE(player.getUuid())) {
            player.sendMessage(Text.literal("§c§lAlready in a QTE!"), false);
            return 0;
        }

        player.sendMessage(Text.literal("§a§l[DEBUG] Starting " + stages + "-stage QTE in SOLO MODE!"), false);
        player.sendMessage(Text.literal("§e§lPress the button that appears!"), false);

        QTEManager.triggerQTESolo(
                player,
                stages,

                (p1, p2) -> {
                    p1.sendMessage(Text.literal("§d§l★ ALL STAGES COMPLETE! ★"), true);
                    p1.sendMessage(Text.literal("§a§lExtender animation would play here!"), false);

                },

                (p1, p2) -> {
                    p1.sendMessage(Text.literal("§c§l✖ QTE FAILED! ✖"), true);
                    p1.sendMessage(Text.literal("§7Better luck next time!"), false);

                },

                (p1, p2, completedStage) -> {
                    p1.sendMessage(Text.literal("§a§l✓ Stage " + completedStage + " clear!"), false);

                }
        );

        return 1;
    }
}
