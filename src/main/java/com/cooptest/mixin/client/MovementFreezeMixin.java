package com.cooptest.mixin.client;

import com.cooptest.client.DapHoldClientHandler;
import com.cooptest.client.HighFiveClientHandler;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to freeze player movement during combo without using slowness effect.
 * This prevents the annoying FOV zoom that slowness causes.
 */
@Mixin(KeyboardInput.class)
public class MovementFreezeMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(boolean slowDown, float f, CallbackInfo ci) {
        boolean shouldFreeze =
                HighFiveClientHandler.isLocalPlayerFrozen()
                        || com.cooptest.client.DivineFlamComboClient.isLocalPlayerInCombo()
                        || com.cooptest.client.ChargedDapClientHandler.isLocalPlayerFireDapFrozen()
                        || com.cooptest.client.ChargedDapClientHandler.isLocalPlayerPerfectDapFrozen()  // Perfect dap freeze!
                        || DapHoldClientHandler.isLocalPlayerFrozen()
                        || com.cooptest.client.HugClientHandler.isLocalPlayerInHug();  // Hug freeze!

        if (shouldFreeze) {
            Input input = (Input) (Object) this;
            // Freeze ONLY movement inputs - camera remains FREE!
            input.movementForward  = 0;
            input.movementSideways = 0;
            input.jumping  = false;
            input.sneaking = false;
            // Camera/mouse look continues to work normally!
        }
    }
}