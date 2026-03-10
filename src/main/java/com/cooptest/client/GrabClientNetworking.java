package com.cooptest.client;

import com.cooptest.GrabNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class GrabClientNetworking {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(GrabNetworking.GrabStatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.isStart()) {
                    GrabClientState.onGrabStart(payload.holderUuid(), payload.heldUuid());
                } else {
                    GrabClientState.onGrabEnd(payload.holderUuid(), payload.heldUuid());
                }
            });
        });
    }
}
