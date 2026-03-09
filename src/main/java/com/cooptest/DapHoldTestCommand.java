package com.cooptest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;


public class DapHoldTestCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("testdaphold")
                .executes(DapHoldTestCommand::execute)
        );
    }
    
    private static int execute(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            
            System.out.println("=".repeat(60));
            System.out.println("[DapHold Test] STARTING SINGLE PLAYER TEST");
            System.out.println("[DapHold Test] Player: " + player.getName().getString());
            System.out.println("=".repeat(60));
            
            
            DapHoldHandler.startDapHold(player, player);
            
            player.sendMessage(net.minecraft.text.Text.literal("§a[DapHold Test] Started! Check console for logs."), false);
            
            return 1;
        } catch (Exception e) {
            System.err.println("[DapHold Test] ERROR: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
}
