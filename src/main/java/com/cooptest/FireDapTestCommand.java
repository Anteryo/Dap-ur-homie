package com.cooptest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;


public class FireDapTestCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("testfiredap").requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))).executes(FireDapTestCommand::execute));
    } // update it does exist, w zaptrem4 for the code here
    
    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            
            Vec3d pos = player.getEntityPos();
            Vec3d fakePartnerPos = pos.add(1.0, 0, 0); // 1 block to the right
            
            player.sendMessage(Text.literal("§a[TEST] Starting Fire Dap test..."), false);
            player.sendMessage(Text.literal("§a[TEST] You are P1, fake partner is P2"), false);
            player.sendMessage(Text.literal("§a[TEST] Press J when window opens!"), false);
            

            ChargedDapHandler.startFireDap(player, player, pos);
            
            player.sendMessage(Text.literal("§a[TEST] Fire dap started! Check console logs!"), false);
            
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("§cError: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
}
