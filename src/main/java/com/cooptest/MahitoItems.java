package com.cooptest;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;


public class MahitoItems {

    public static RegistryEntry<Potion> MAHITO_POTION;

    public static void register() {
        // Register the potion type - 60 seconds duration
        Potion mahitoPotion = new Potion(
                new StatusEffectInstance(ModEffects.MAHITO, 1200, 0) // 60 seconds
        );

        MAHITO_POTION = Registry.registerReference(
                Registries.POTION,
                Identifier.of("testcoop", "mahito_stuff"),
                mahitoPotion
        );

        // Add to creative tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(content -> {
            ItemStack potionStack = new ItemStack(Items.POTION);
            potionStack.set(DataComponentTypes.POTION_CONTENTS,
                    new PotionContentsComponent(MAHITO_POTION));
            content.add(potionStack);
        });
    }

    /**
     * Create a Mahito Stuff Potion item stack
     */
    public static ItemStack createMahitoPotion() {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(DataComponentTypes.POTION_CONTENTS,
                new PotionContentsComponent(MAHITO_POTION));
        return stack;
    }
}