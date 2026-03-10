package com.cooptest.client;

import com.cooptest.MarioJumpHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;


public class MarioJumpClientHandler {

    private static boolean wasJumpPressed = false;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            boolean isJumpPressed = client.options.jumpKey.isPressed();

            if (isJumpPressed && !wasJumpPressed) {
                if (isOnPlayerHead(client)) {
                    ClientPlayNetworking.send(new MarioJumpHandler.MarioJumpRequestPayload());
                }
            }

            wasJumpPressed = isJumpPressed;
        });
    }


    private static boolean isOnPlayerHead(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return false;

        Vec3d playerPos = player.getPos();
        double playerFeetY = playerPos.y;

        Box searchBox = new Box(
            playerPos.x - 0.8, playerPos.y - 2.5, playerPos.z - 0.8,
            playerPos.x + 0.8, playerPos.y + 0.5, playerPos.z + 0.8
        );

        List<PlayerEntity> nearby = client.world.getEntitiesByClass(
            PlayerEntity.class, searchBox,
            p -> p != player && p.isAlive()
        );

        for (PlayerEntity target : nearby) {
            Vec3d targetPos = target.getPos();
            double targetHeadY = targetPos.y + target.getStandingEyeHeight() + 0.15;

            double heightDiff = playerFeetY - targetHeadY;
            if (heightDiff >= -0.35 && heightDiff <= 0.5) {
                double horizDist = Math.sqrt(
                    Math.pow(playerPos.x - targetPos.x, 2) +
                    Math.pow(playerPos.z - targetPos.z, 2)
                );
                if (horizDist <= 0.7) {
                    return true;
                }
            }
        }

        return false;
    }
}
