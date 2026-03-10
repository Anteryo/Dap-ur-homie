package com.cooptest;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

public class GrabNetworking {

    // ===== PAYLOADS SKBIDI =====

    public record ThrowRequestPayload(float power) implements CustomPayload {
        public static final Id<ThrowRequestPayload> ID = new Id<>(Identifier.of("cooptest", "throw_request"));
        public static final PacketCodec<PacketByteBuf, ThrowRequestPayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeFloat(payload.power),
                buf -> new ThrowRequestPayload(buf.readFloat())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record DropRequestPayload() implements CustomPayload {
        public static final Id<DropRequestPayload> ID = new Id<>(Identifier.of("cooptest", "drop_request"));
        public static final PacketCodec<PacketByteBuf, DropRequestPayload> CODEC = PacketCodec.unit(new DropRequestPayload());
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record EscapeRequestPayload() implements CustomPayload {
        public static final Id<EscapeRequestPayload> ID = new Id<>(Identifier.of("cooptest", "escape_request"));
        public static final PacketCodec<PacketByteBuf, EscapeRequestPayload> CODEC = PacketCodec.unit(new EscapeRequestPayload());
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ElytraBoostRequestPayload() implements CustomPayload {
        public static final Id<ElytraBoostRequestPayload> ID = new Id<>(Identifier.of("cooptest", "elytra_boost"));
        public static final PacketCodec<PacketByteBuf, ElytraBoostRequestPayload> CODEC = PacketCodec.unit(new ElytraBoostRequestPayload());
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record AirMovementPayload(float forward, float strafe) implements CustomPayload {
        public static final Id<AirMovementPayload> ID = new Id<>(Identifier.of("cooptest", "air_movement"));
        public static final PacketCodec<PacketByteBuf, AirMovementPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeFloat(payload.forward);
                    buf.writeFloat(payload.strafe);
                },
                buf -> new AirMovementPayload(buf.readFloat(), buf.readFloat())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record GrabStatePayload(UUID holderUuid, UUID heldUuid, boolean isStart) implements CustomPayload {
        public static final Id<GrabStatePayload> ID = new Id<>(Identifier.of("cooptest", "grab_state"));
        public static final PacketCodec<PacketByteBuf, GrabStatePayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.holderUuid);
                    buf.writeUuid(payload.heldUuid);
                    buf.writeBoolean(payload.isStart);
                },
                buf -> new GrabStatePayload(buf.readUuid(), buf.readUuid(), buf.readBoolean())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ShieldTogglePayload() implements CustomPayload {
        public static final Id<ShieldTogglePayload> ID = new Id<>(Identifier.of("cooptest", "shield_toggle"));
        public static final PacketCodec<PacketByteBuf, ShieldTogglePayload> CODEC = PacketCodec.unit(new ShieldTogglePayload());
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ===== REGISTRATION =====

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(ThrowRequestPayload.ID, ThrowRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DropRequestPayload.ID, DropRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EscapeRequestPayload.ID, EscapeRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ElytraBoostRequestPayload.ID, ElytraBoostRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AirMovementPayload.ID, AirMovementPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ShieldTogglePayload.ID, ShieldTogglePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GrabStatePayload.ID, GrabStatePayload.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ThrowRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            float power = payload.power();
            context.server().execute(() -> {
                if (GrabMechanic.isHolding(player)) {
                    GrabMechanic.tryThrow(player, power);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DropRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (GrabMechanic.isHolding(player)) {
                    GrabMechanic.tryDrop(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(EscapeRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (GrabMechanic.isBeingHeld(player)) {
                    GrabMechanic.tryEscape(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ElytraBoostRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                GrabMechanic.requestElytraBoost(player.getUuid());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AirMovementPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                GrabMechanic.setAirMovementInput(player.getUuid(), payload.forward(), payload.strafe());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ShieldTogglePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                GrabMechanic.toggleShieldMode(player);
            });
        });
    }

    // ===== BROADCAST =====

    public static void broadcastGrabState(MinecraftServer server, UUID holderUuid, UUID heldUuid, boolean isStart) {
        GrabStatePayload payload = new GrabStatePayload(holderUuid, heldUuid, isStart);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}