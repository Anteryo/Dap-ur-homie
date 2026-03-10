package com.cooptest;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {


    public static final Identifier EPIC_DAP_ID = Identifier.of("testcoop", "epic_dap");
    public static final SoundEvent EPIC_DAP = SoundEvent.of(EPIC_DAP_ID);

    public static final Identifier EXPLOSION_IMPACT_ID = Identifier.of("testcoop", "explosion_impact");
    public static final SoundEvent EXPLOSION_IMPACT = SoundEvent.of(EXPLOSION_IMPACT_ID);

    public static final Identifier MAHITO_ID = Identifier.of("testcoop", "mahito");
    public static final SoundEvent MAHITO = SoundEvent.of(MAHITO_ID);

    public static final Identifier GALACTIC_DAP_ID = Identifier.of("testcoop", "galactic_dap");
    public static final SoundEvent GALACTIC_DAP = SoundEvent.of(GALACTIC_DAP_ID);

    public static final Identifier TRUE_FRIENDSHIP_ID = Identifier.of("testcoop", "true_friendship");
    public static final SoundEvent TRUE_FRIENDSHIP = SoundEvent.of(TRUE_FRIENDSHIP_ID);

    public static final Identifier IMPACT_ID = Identifier.of("testcoop", "impact");
    public static final SoundEvent IMPACT = SoundEvent.of(IMPACT_ID);

    public static final Identifier MARIO_JUMP_ID = Identifier.of("testcoop", "mariojump");
    public static final SoundEvent MARIO_JUMP = SoundEvent.of(MARIO_JUMP_ID);
    public static final SoundEvent FIRE_IMPACT = SoundEvent.of(Identifier.of("testcoop", "fireimpact")
    );


    public static final Identifier DAP_MISS_ID = Identifier.of("testcoop", "dap.miss");
    public static final SoundEvent DAP_MISS = SoundEvent.of(DAP_MISS_ID);

    public static final Identifier DAP_WEAK_ID = Identifier.of("testcoop", "dap.weak");
    public static final SoundEvent DAP_WEAK = SoundEvent.of(DAP_WEAK_ID);

    public static final Identifier DAP_HIT_ID = Identifier.of("testcoop", "dap.hit");
    public static final SoundEvent DAP_HIT = SoundEvent.of(DAP_HIT_ID);



    public static void register() {
        Registry.register(Registries.SOUND_EVENT, EPIC_DAP_ID, EPIC_DAP);
        Registry.register(Registries.SOUND_EVENT, EXPLOSION_IMPACT_ID, EXPLOSION_IMPACT);
        Registry.register(Registries.SOUND_EVENT, MAHITO_ID, MAHITO);
        Registry.register(Registries.SOUND_EVENT, GALACTIC_DAP_ID, GALACTIC_DAP);
        Registry.register(Registries.SOUND_EVENT, TRUE_FRIENDSHIP_ID, TRUE_FRIENDSHIP);
        Registry.register(Registries.SOUND_EVENT, IMPACT_ID, IMPACT);
        Registry.register(Registries.SOUND_EVENT, MARIO_JUMP_ID, MARIO_JUMP);
        Registry.register(Registries.SOUND_EVENT,
                Identifier.of("testcoop", "fireimpact"),
                FIRE_IMPACT);

        Registry.register(Registries.SOUND_EVENT, DAP_MISS_ID, DAP_MISS);
        Registry.register(Registries.SOUND_EVENT, DAP_WEAK_ID, DAP_WEAK);
        Registry.register(Registries.SOUND_EVENT, DAP_HIT_ID, DAP_HIT);

        System.out.println("[ModSounds] Registered all sounds");
    }
}