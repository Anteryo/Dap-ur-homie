package com.cooptest.client;

import com.cooptest.HighFiveHugHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 Client-side hug handler
  Press F to hug after high-five
 */
public class HugClientHandler {

    private static KeyBinding hugKey;

    private static final Map<UUID, Boolean> inHug = new HashMap<>();

    public static void register() {
        hugKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.coopmoves.hug",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F,
                "category.coopmoves"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (hugKey.isPressed()) {
                ClientPlayNetworking.send(new HighFiveHugHandler.HugHoldPayload());
            }
        });
    }


    public static boolean isLocalPlayerInHug() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;

        var animState = CoopAnimationHandler.getAnimState(client.player.getUuid());
        return animState == CoopAnimationHandler.AnimState.HUG_START
                || animState == CoopAnimationHandler.AnimState.HUGGING
                || animState == CoopAnimationHandler.AnimState.HUGGING2;
    }
}