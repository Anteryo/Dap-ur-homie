package com.cooptest;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;


public class ModEffects {

    public static RegistryEntry<StatusEffect> MAHITO;

    public static void register() {
        MAHITO = Registry.registerReference(
                Registries.STATUS_EFFECT,
                Identifier.of("testcoop", "mahito"),
                new MahitoEffect()
        );
    }
}