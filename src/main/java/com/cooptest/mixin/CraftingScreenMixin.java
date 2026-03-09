package com.cooptest.mixin;

import com.cooptest.MahitoCraftingHandler;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.screen.CraftingScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(CraftingScreenHandler.class)
public class CraftingScreenMixin {

    @Shadow @Final private RecipeInputInventory input;
    @Shadow @Final private CraftingResultInventory result;

    @Inject(method = "onContentChanged", at = @At("HEAD"), cancellable = true)
    private void onCraftingChanged(net.minecraft.inventory.Inventory inventory, CallbackInfo ci) {
        if (MahitoCraftingHandler.isValidMahitoRecipe(input)) {
            result.setStack(0, MahitoCraftingHandler.createResult());
            ci.cancel();
        }
    }
}