package com.cooptest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class TestCoop implements ModInitializer {
    @Override
    public void onInitialize() {
        try {
            System.out.println("[TestCoop] Starting initialization...");

            System.out.println("[TestCoop] Loading config...");
            CoopMovesConfig.load();

            System.out.println("[TestCoop] Registering sounds...");
            ModSounds.register();

            System.out.println("[TestCoop] Registering effects...");
            ModEffects.register();
            MahitoItems.register();

            System.out.println("[TestCoop] Registering payloads...");
            PoseNetworking.registerPayloads();
            PoseNetworking.registerServerReceiver();

            GrabNetworking.registerPayloads();
            GrabNetworking.registerServerReceivers();
            GrabMechanic.ShieldModePayload.register();

            HighFiveHandler.registerPayloads();
            ChargedDapHandler.registerPayloads();

            System.out.println("[TestCoop] Registering QTE system...");

            PushInteractionHandler.registerPayloads();
            FallCatchHandler.registerPayloads();
            MahitoTrollHandler.register();
            FallDapHandler.register();

            System.out.println("[TestCoop] Registering Mario Jump...");
            MarioJumpHandler.registerPayloads();
            MarioJumpHandler.register();

            System.out.println("[TestCoop] Registering Hug System payloads...");
            HighFiveHugHandler.registerPayloads();

            System.out.println("[TestCoop] Registering Heaven Dap...");
            HeavenDapPayloads.registerPayloads();

            System.out.println("[TestCoop] Registering Hug System...");
            HighFiveHugHandler.register();

            System.out.println("[TestCoop] Registering handlers...");
            GrabInteractionHandler.register();
            GrabMechanic.registerShieldDamageEvent();
            PushInteractionHandler.register();
            HighFiveHandler.register();
            ChargedDapHandler.register();

            System.out.println("[TestCoop] Registering DapSession system...");
            DapSessionManager.register();  //
            FallCatchHandler.register();

            DapHoldHandler.register();
            System.out.println("[TestCoop] Registering animation handlers...");
            AnimationTickHandler.register();
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
                FireDapTestCommand.register(dispatcher);
                DapHoldTestCommand.register(dispatcher);
                HeavenDapTestCommand.register(dispatcher);
                HeavenDapSoloCommand.register(dispatcher);
                DebugQTECommand.register(dispatcher);




            });
            LaunchedPlayerTracker.register();

            CarryingSlowdown.register();

            PlayerCleanupHandler.register();

            System.out.println("[TestCoop] Registering server tick...");
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                GrabMechanic.tick(server);
                ChargedDapHandler.checkTickSpeedRestore(server);
                QTEManager.tick(server);
                DapComboChain.tick(server);
                if (server.getTicks() % 20 == 0) {
                    PushInteractionHandler.cleanupExpiredImmunity();
                }
            });

            System.out.println("[TestCoop] Initialization complete!");

        } catch (Exception e) {
            System.err.println("[TestCoop] bruh CRAASH during initialization");
            e.printStackTrace();
            throw new RuntimeException("TestCoop initialization failed", e);
        }
    }
}