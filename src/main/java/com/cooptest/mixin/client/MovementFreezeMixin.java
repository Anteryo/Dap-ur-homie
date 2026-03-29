package com.cooptest.mixin.client;

import com.cooptest.client.ChargedDapClientHandler;
import com.cooptest.client.DapHoldClientHandler;
import com.cooptest.client.DivineFlamComboClient;
import com.cooptest.client.HighFiveClientHandler;
import com.cooptest.client.HugClientHandler;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class MovementFreezeMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        boolean shouldFreeze =
                HighFiveClientHandler.isLocalPlayerFrozen()
                        || DivineFlamComboClient.isLocalPlayerInCombo()
                        || ChargedDapClientHandler.isLocalPlayerFireDapFrozen()
                        || ChargedDapClientHandler.isLocalPlayerPerfectDapFrozen()
                        || DapHoldClientHandler.isLocalPlayerFrozen()
                        || HugClientHandler.isLocalPlayerInHug();

        if (shouldFreeze) {
            Input input = (Input) (Object) this;
            input.playerInput = PlayerInput.DEFAULT;
            ((InputAccessor) input).setMovementVector(Vec2f.ZERO);
        }
    }
}