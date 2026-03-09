package com.cooptest;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;


public class MahitoCraftingHandler {

    public static void register() {

    }


    public static boolean isValidMahitoRecipe(RecipeInputInventory inventory) {
        int ghastTearCount = 0;
        int rottenFleshCount = 0;
        int waterBottleCount = 0;
        int otherItems = 0;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;

            if (stack.isOf(Items.GHAST_TEAR)) {
                ghastTearCount += stack.getCount();
            } else if (stack.isOf(Items.ROTTEN_FLESH)) {
                rottenFleshCount += stack.getCount();
            } else if (stack.isOf(Items.POTION)) {
                PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
                if (contents != null && contents.potion().isPresent()) {
                    String potionId = contents.potion().get().getIdAsString();
                    if (potionId.contains("water")) {
                        waterBottleCount += stack.getCount();
                    } else {
                        otherItems++;
                    }
                } else {
                    waterBottleCount += stack.getCount();
                }
            } else {
                otherItems++;
            }
        }

        return ghastTearCount >= 1 && rottenFleshCount >= 64 && waterBottleCount >= 1 && otherItems == 0;
    }


    public static ItemStack createResult() {
        return MahitoItems.createMahitoPotion();
    }


    public static void consumeIngredients(RecipeInputInventory inventory) {
        int fleshToConsume = 64;
        boolean ghastTearConsumed = false;
        boolean waterBottleConsumed = false;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;

            if (stack.isOf(Items.GHAST_TEAR) && !ghastTearConsumed) {
                stack.decrement(1);
                ghastTearConsumed = true;
            } else if (stack.isOf(Items.ROTTEN_FLESH) && fleshToConsume > 0) {
                int toRemove = Math.min(stack.getCount(), fleshToConsume);
                stack.decrement(toRemove);
                fleshToConsume -= toRemove;
            } else if (stack.isOf(Items.POTION) && !waterBottleConsumed) {
                stack.decrement(1);
                waterBottleConsumed = true;
            }
        }
    }
}