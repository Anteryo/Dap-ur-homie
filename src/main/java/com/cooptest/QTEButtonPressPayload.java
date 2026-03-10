package com.cooptest;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;


public record QTEButtonPressPayload(String button) implements CustomPayload {

    public static final Identifier QTE_BUTTON_PRESS_ID = Identifier.of("cooptest", "qte_button_press");
    public static final Id<QTEButtonPressPayload> ID = new Id<>(QTE_BUTTON_PRESS_ID);

    public static final PacketCodec<RegistryByteBuf, QTEButtonPressPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, QTEButtonPressPayload::button,
            QTEButtonPressPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}