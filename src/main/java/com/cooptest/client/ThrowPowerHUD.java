package com.cooptest.client;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;


public class ThrowPowerHUD {

    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 8;
    private static final int BAR_Y_OFFSET = 60;
    private static final int BG_COLOR = 0xAA000000;
    private static final int BORDER_COLOR = 0xFFFFFFFF;
    private static final int LOW_COLOR = 0xFF00FF00;
    private static final int MID_COLOR = 0xFFFFFF00;
    private static final int HIGH_COLOR = 0xFFFF0000;
    private static final int READY_COLOR = 0xFFFF00FF;
    private static float displayedCharge = 0f;
    private static final float LERP_SPEED = 0.15f;

    public static void register() {
        HudRenderCallback.EVENT.register(ThrowPowerHUD::render);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        PoseState pose = PoseNetworking.poseStates.getOrDefault(
            client.player.getUuid(), PoseState.NONE
        );
        
        if (pose != PoseState.GRAB_HOLDING) {
            displayedCharge = 0f;
            return;
        }
        
        float targetCharge = GrabClientState.getChargeProgress(client.player.getUuid());
        
        if (targetCharge <= 0 && displayedCharge <= 0.01f) {
            return;
        }
        
        displayedCharge += (targetCharge - displayedCharge) * LERP_SPEED;
        
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        
        int barX = (screenWidth - BAR_WIDTH) / 2;
        int barY = (screenHeight / 2) + BAR_Y_OFFSET;
        
        context.fill(barX - 2, barY - 2, barX + BAR_WIDTH + 2, barY + BAR_HEIGHT + 2, BG_COLOR);
        
        context.drawBorder(barX - 2, barY - 2, BAR_WIDTH + 4, BAR_HEIGHT + 4, BORDER_COLOR);
        
        int fillWidth = (int)(BAR_WIDTH * displayedCharge);
        
        int fillColor = getChargeColor(displayedCharge);
        
        if (fillWidth > 0) {
            context.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, fillColor);
        }
        
        if (displayedCharge >= 0.99f) {
            String text = "RELEASE TO THROW!";
            int textWidth = client.textRenderer.getWidth(text);
            int textX = (screenWidth - textWidth) / 2;
            int textY = barY - 12;
            
            // Pulsing effect
            float pulse = (float)(Math.sin(System.currentTimeMillis() / 100.0) * 0.5 + 0.5);
            int alpha = (int)(155 + pulse * 100);
            int textColor = (alpha << 24) | 0xFFFFFF;
            
            context.drawText(client.textRenderer, text, textX, textY, textColor, true);
        }
    }
    
    private static int getChargeColor(float charge) {
        if (charge >= 0.99f) return READY_COLOR;
        if (charge >= 0.7f) return HIGH_COLOR;
        if (charge >= 0.4f) return MID_COLOR;
        return LOW_COLOR;
    }
}
