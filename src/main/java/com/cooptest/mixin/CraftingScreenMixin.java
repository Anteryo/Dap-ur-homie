package com.cooptest.mixin;

import com.cooptest.MahitoCraftingHandler;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.CraftingScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingScreenHandler.class)
public class CraftingScreenMixin {

    // fields live on AbstractCraftingScreenHandler now
    protected RecipeInputInventory craftingInventory;
    protected CraftingResultInventory craftingResultInventory;

    @Inject(method = "onContentChanged", at = @At("HEAD"), cancellable = true)
    private void onCraftingChanged(Inventory inventory, CallbackInfo ci) {
        if (MahitoCraftingHandler.isValidMahitoRecipe(craftingInventory)) {
            craftingResultInventory.setStack(0, MahitoCraftingHandler.createResult());
            ci.cancel();
        }
    }
}