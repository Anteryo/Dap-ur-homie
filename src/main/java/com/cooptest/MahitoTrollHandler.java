package com.cooptest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;


public class MahitoTrollHandler {
    
    private static final Map<UUID, TrollData> trolledPlayers = new HashMap<>();
    
    private static final long TROLL_START_DELAY_MS = 2000;  // 2 sec before troll starts
    private static final long TROLL_DEATH_DELAY_MS = 4000;  // 4 more sec until death (6 total)
    
    private static class TrollData {
        long dapTime;         
        boolean trollStarted;  
        UUID trollerId;        
        
        TrollData(long time, UUID troller) {
            this.dapTime = time;
            this.trollStarted = false;
            this.trollerId = troller;
        }
    }
    
    public record MahitoAnimPayload(UUID playerId) implements CustomPayload {
        public static final Id<MahitoAnimPayload> ID = new Id<>(Identifier.of("cooptest", "mahito_anim"));
        
        public static final PacketCodec<PacketByteBuf, MahitoAnimPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeUuid(payload.playerId),
            buf -> new MahitoAnimPayload(buf.readUuid())
        );
        
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    
    public static void register() {
        PayloadTypeRegistry.playS2C().register(MahitoAnimPayload.ID, MahitoAnimPayload.CODEC);
        
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            
            Iterator<Map.Entry<UUID, TrollData>> iter = trolledPlayers.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<UUID, TrollData> entry = iter.next();
                UUID victimId = entry.getKey();
                TrollData data = entry.getValue();
                
                ServerPlayerEntity victim = server.getPlayerManager().getPlayer(victimId);
                if (victim == null || !victim.isAlive()) {
                    iter.remove();
                    continue;
                }
                
                long elapsed = now - data.dapTime;
                
                if (!data.trollStarted && elapsed >= TROLL_START_DELAY_MS) {
                    data.trollStarted = true;
                    startTroll(victim, server.getPlayerManager().getPlayer(data.trollerId));
                }
                
                if (data.trollStarted && elapsed >= TROLL_START_DELAY_MS + TROLL_DEATH_DELAY_MS) {
                    executeDeath(victim);
                    iter.remove();
                }
                
                if (data.trollStarted) {
                    victim.setVelocity(0, victim.getVelocity().y, 0);
                    victim.velocityModified = true;
                }
            }
        });
    }
    
    /**
     * Called when a dap happens - check if troller has mahito effect
     */
    public static void checkForMahitoTroll(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        // Check if either player has mahito effect
        boolean p1HasMahito = p1.hasStatusEffect(ModEffects.MAHITO);
        boolean p2HasMahito = p2.hasStatusEffect(ModEffects.MAHITO);
        
        if (p1HasMahito && !p2HasMahito) {
            // P1 trolls P2
            startMahitoTroll(p2, p1);
            p1.removeStatusEffect(ModEffects.MAHITO);
        } else if (p2HasMahito && !p1HasMahito) {
            // P2 trolls P1
            startMahitoTroll(p1, p2);
            p2.removeStatusEffect(ModEffects.MAHITO);
        }
        // If both have it, nothing happens (they cancel out)
    }
    
    private static void startMahitoTroll(ServerPlayerEntity victim, ServerPlayerEntity troller) {
        trolledPlayers.put(victim.getUuid(), new TrollData(System.currentTimeMillis(), troller.getUuid()));
        
        // Notify troller
        if (troller != null) {
            troller.sendMessage(Text.literal("§c§l☠ You cursed " + victim.getName().getString() + "! ☠"), true);
        }
    }
    
    private static void startTroll(ServerPlayerEntity victim, ServerPlayerEntity troller) {
        ServerWorld world = victim.getServerWorld();
        
        // Freeze movement + levitation
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 255, false, false));
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 100, 1, false, false));
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 100, 128, false, false)); // Prevent jumping
        
        // Play mahito sound
        world.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
            ModSounds.MAHITO, SoundCategory.PLAYERS, 2.0f, 1.0f);
        
        // Play animation - broadcast to all clients
        for (ServerPlayerEntity player : world.getPlayers()) {
            ServerPlayNetworking.send(player, new MahitoAnimPayload(victim.getUuid()));
        }
        
        // Ominous particles around victim
        world.spawnParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + 1, victim.getZ(), 
            20, 0.5, 1.0, 0.5, 0.02);
        world.spawnParticles(ParticleTypes.SMOKE, victim.getX(), victim.getY() + 1, victim.getZ(), 
            15, 0.4, 0.8, 0.4, 0.01);
        
        // Message
        victim.sendMessage(Text.literal("§4§l☠ MAHITO'S CURSE! ☠"), true);
        
        // Sound effects
        world.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
            SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.5f, 1.5f);
    }
    
    private static void executeDeath(ServerPlayerEntity victim) {
        ServerWorld world = victim.getServerWorld();
        double x = victim.getX();
        double y = victim.getY();
        double z = victim.getZ();
        
        // Firework explosion on head!
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 2, z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.FIREWORK, x, y + 2, z, 50, 0.5, 0.5, 0.5, 0.3);
        world.spawnParticles(ParticleTypes.FLASH, x, y + 2, z, 3, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 2, z, 30, 0.4, 0.4, 0.4, 0.15);
        world.spawnParticles(ParticleTypes.DRAGON_BREATH, x, y + 2, z, 20, 0.3, 0.3, 0.3, 0.1);
        
        // Explosion sounds
        world.playSound(null, x, y, z,
            SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 2.0f, 1.0f);
        world.playSound(null, x, y, z,
            SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.5f, 1.2f);
        world.playSound(null, x, y, z,
            SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 0.5f);
        
        // Kill with custom death message
        victim.damage(world.getDamageSources().magic(), Float.MAX_VALUE);
        
        // Announce
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player != victim) {
                player.sendMessage(Text.literal("§4" + victim.getName().getString() + " §7was trolled by §cMahito's Curse!"), false);
            }
        }
    }
    
    /**
     * Check if a player is currently being trolled
     */
    public static boolean isBeingTrolled(UUID playerId) {
        return trolledPlayers.containsKey(playerId);
    }
    
    /**
     * Cleanup when player disconnects
     */
    public static void cleanup(UUID playerId) {
        trolledPlayers.remove(playerId);
    }
}
