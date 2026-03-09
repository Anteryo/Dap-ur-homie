package com.cooptest;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Payload for QTE window display
 */
public record QTEWindowPayload(UUID playerId, String button, int stage, long windowStart, long windowEnd) implements CustomPayload {

    public static final Identifier QTE_WINDOW_ID = Identifier.of("cooptest", "qte_window");
    public static final Id<QTEWindowPayload> ID = new Id<>(QTE_WINDOW_ID);

    public static final PacketCodec<PacketByteBuf, QTEWindowPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeUuid(payload.playerId);
                buf.writeString(payload.button);
                buf.writeInt(payload.stage);
                buf.writeLong(payload.windowStart);
                buf.writeLong(payload.windowEnd);
            },
            buf -> new QTEWindowPayload(buf.readUuid(), buf.readString(), buf.readInt(), buf.readLong(), buf.readLong())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}