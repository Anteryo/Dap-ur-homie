package com.cooptest.client;

import com.cooptest.DivineFlamCombo;
import com.cooptest.ModKeyCategories;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;


public class DivineFlamComboClient {

    private static KeyBinding divineFlameKey;
    private static long divineFlameEndTime = 0;

    public static void register() {
        divineFlameKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cooptest.divine_flame",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                ModKeyCategories.COOPMOVES
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (divineFlameKey.wasPressed()) {
                ClientPlayNetworking.send(new DivineFlamCombo.DivineJPressPayload());
                divineFlameEndTime = System.currentTimeMillis() + 3000;
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(
                DivineFlamCombo.DivineStartPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        divineFlameEndTime = System.currentTimeMillis() + 1460;
                    });
                }
        );

    }


    public static boolean isLocalPlayerInCombo() {
        return System.currentTimeMillis() < divineFlameEndTime;
    }

    public static boolean onHighFivePress() {
        return false;
    }
}