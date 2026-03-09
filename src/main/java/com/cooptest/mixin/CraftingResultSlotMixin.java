package com.cooptest.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.component.DataComponentTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(CraftingResultSlot.class)
public class CraftingResultSlotMixin {

    @Shadow @Final private RecipeInputInventory input;

    @Unique
    private boolean isMahitoPotion(ItemStack stack) {
        if (!stack.isOf(Items.POTION)) {
            return false;
        }
        var contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents != null && contents.potion().isPresent()) {
            String potionId = contents.potion().get().getIdAsString();
            return potionId.contains("mahito");
        }
        return false;
    }

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void onTakeMahitoPotion(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (isMahitoPotion(stack)) {
            // Clear ALL slots in the crafting grid to prevent duplication
            for (int i = 0; i < input.size(); i++) {
                input.setStack(i, ItemStack.EMPTY);
            }
            input.markDirty();
        }
    }
}