package com.cooptest;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Payload to clear QTE UI
 * Sent from server to client when QTE ends
 */
public record QTEClearPayload(UUID playerId) implements CustomPayload {

    public static final Identifier QTE_CLEAR_ID = Identifier.of("cooptest", "qte_clear");
    public static final Id<QTEClearPayload> ID = new Id<>(QTE_CLEAR_ID);

    public static final PacketCodec<PacketByteBuf, QTEClearPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeUuid(payload.playerId),
            buf -> new QTEClearPayload(buf.readUuid())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}