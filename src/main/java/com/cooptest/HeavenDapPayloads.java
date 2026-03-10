package com.cooptest;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;


public class HeavenDapPayloads {


    public record HeavenDapStartPayload() implements CustomPayload {
        public static final Id<HeavenDapStartPayload> ID =
                new Id<>(Identifier.of("testcoop", "heaven_dap_start"));

        public static final PacketCodec<PacketByteBuf, HeavenDapStartPayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> {}, 
                        buf -> new HeavenDapStartPayload()  
                );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }


    public record HeavenDapEndPayload() implements CustomPayload {
        public static final Id<HeavenDapEndPayload> ID =
                new Id<>(Identifier.of("testcoop", "heaven_dap_end"));

        public static final PacketCodec<PacketByteBuf, HeavenDapEndPayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> {},  
                        buf -> new HeavenDapEndPayload() 
                );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }


    public record RestoreVolumePayload() implements CustomPayload {
        public static final Id<RestoreVolumePayload> ID =
                new Id<>(Identifier.of("testcoop", "restore_volume"));

        public static final PacketCodec<PacketByteBuf, RestoreVolumePayload> CODEC =
                PacketCodec.of(
                        (payload, buf) -> {},  
                        buf -> new RestoreVolumePayload()  
                );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }


    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(HeavenDapStartPayload.ID, HeavenDapStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HeavenDapEndPayload.ID, HeavenDapEndPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RestoreVolumePayload.ID, RestoreVolumePayload.CODEC);
    }
}
