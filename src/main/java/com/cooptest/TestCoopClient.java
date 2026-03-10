package com.cooptest;

import com.cooptest.client.*;
import net.fabricmc.api.ClientModInitializer;

public class TestCoopClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {


        PoseNetworking.registerClientReceiver();

        GrabClientNetworking.register();
        GrabInputHandler.register();
        ClientCrouchPoseHandler.register();
        GrabClientEffects.register();
        ThrowPowerHUD.register();
        TrajectoryRenderer.register();
        HighFiveClientHandler.register();
        ChargedDapClientHandler.register();
        PushClientHandler.register();
        CatchClientHandler.register();
        MahitoClientHandler.register();
        FallDapClientHandler.register();
        DapHoldClientHandler.register();
        MarioJumpClientHandler.register();
        HugClientHandler.register();
        HeavenDapClientHandler.register();
        QTEClientHandler.registerReceivers();

        com.cooptest.client.CoopAnimationHandler.register();
    }
}