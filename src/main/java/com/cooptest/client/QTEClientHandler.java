package com.cooptest.client;

import com.cooptest.QTEButtonPressPayload;
import com.cooptest.QTEWindowPayload;
import com.cooptest.QTEClearPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;


public class QTEClientHandler {

    private static boolean active = false;
    private static String expectedButton = null;
    private static int stage = 0;
    private static int maxStages = 1;
    private static long windowStart = 0;  // Client-local timestamp
    private static long windowEnd = 0;    // Client-local timestamp
    private static long receiveTime = 0;  // When we received the packet

    private static long flashEndTime = 0;        // Green flash on success
    private static long failFlashEndTime = 0;     // Red flash on wrong button
    private static boolean pressedThisWindow = false;



    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(QTEWindowPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {

                        long now = System.currentTimeMillis();
                        long serverWindowStart = payload.windowStart();
                        long serverWindowEnd = payload.windowEnd();


                        active = true;
                        expectedButton = payload.button();
                        stage = payload.stage();
                        windowStart = serverWindowStart;
                        windowEnd = serverWindowEnd;
                        receiveTime = now;
                        pressedThisWindow = false;

                        System.out.println("[QTE Client] Window received! Stage " + stage +
                                ", Press [" + expectedButton + "]" +
                                ", Opens in " + (windowStart - now) + "ms" +
                                ", Duration: " + (windowEnd - windowStart) + "ms");
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(QTEClearPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        active = false;
                        expectedButton = null;
                        pressedThisWindow = false;
                        System.out.println("[QTE Client] Cleared.");
                    });
                });
    }



    public static boolean handleKeyPress(String button) {
        if (!active) return false;

        if (button.equals(expectedButton)) {
            long now = System.currentTimeMillis();

            if (now >= windowStart && now <= windowEnd) {
                System.out.println("[QTE Client]  Pressed " + button + " in window!");
                flashEndTime = now + 200; // Green flash for 200ms
                pressedThisWindow = true;
            } else if (now < windowStart) {
                System.out.println("[QTE Client]  Too early!");
                failFlashEndTime = now + 150;
            } else {
                System.out.println("[QTE Client]  Too late!");
                failFlashEndTime = now + 150;
            }

            ClientPlayNetworking.send(new QTEButtonPressPayload(button));
        } else {
            System.out.println("[QTE Client]  Wrong button! Expected " + expectedButton);
            failFlashEndTime = System.currentTimeMillis() + 150;
        }

        return true; // Consumed - block normal key action
    }

    // ==================== HUD RENDERING ====================


    public static void renderHUD(DrawContext context, int screenWidth, int screenHeight) {
        if (!active) return;

        long now = System.currentTimeMillis();
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, 0, 1000); // Render on top of everything

        int barWidth = 60;
        int barHeight = 4;
        int barX = (screenWidth - barWidth) / 2;
        int barY = screenHeight - 55;

        context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF000000);
        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF222222);

        boolean inWindow = (now >= windowStart && now <= windowEnd);
        boolean beforeWindow = (now < windowStart);
        long totalWindowDuration = windowEnd - windowStart;

        int barColor;
        int filledWidth;

        boolean successFlash = (now < flashEndTime);
        boolean failFlash = (now < failFlashEndTime);

        if (successFlash) {
            barColor = 0xFFFFFFFF;
            filledWidth = barWidth;
        } else if (failFlash) {
            barColor = 0xFFFF3333;
            filledWidth = barWidth;
        } else if (beforeWindow) {
            long countdownDuration = windowStart - receiveTime;
            long elapsed = now - receiveTime;
            float progress = countdownDuration > 0
                    ? Math.min(1.0f, (float) elapsed / countdownDuration)
                    : 1.0f;
            filledWidth = (int) (barWidth * progress);
            barColor = 0xFFFF4444;
        } else if (inWindow) {
            long elapsed = now - windowStart;
            float remaining = 1.0f - ((float) elapsed / totalWindowDuration);
            remaining = Math.max(0, remaining);
            filledWidth = (int) (barWidth * remaining);

            if (pressedThisWindow) {
                barColor = 0xFF00FF00;
                filledWidth = barWidth;
            } else {
                barColor = 0xFF00FF00;
            }
        } else {
            filledWidth = 0;
            barColor = 0xFF666666;
        }

        if (filledWidth > 0) {
            context.fill(barX, barY, barX + filledWidth, barY + barHeight, barColor);
        }

        if (expectedButton != null && !pressedThisWindow) {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            String keyText = "[" + expectedButton + "]";
            int textWidth = client.textRenderer.getWidth(keyText);
            int textX = (screenWidth - textWidth) / 2;
            int textY = barY - 12;

            int alpha = 255;
            if (inWindow) {
                float pulse = (float) (Math.sin(now / 100.0) * 0.3 + 0.7);
                alpha = (int) (pulse * 255);
            }
            int textColor = (alpha << 24) | 0xFFFFFF;

            context.drawText(client.textRenderer, keyText, textX, textY, textColor, true);
        }

        if (maxStages > 1) {
            int dotY = barY + barHeight + 3;
            int totalDotsWidth = maxStages * 4 + (maxStages - 1) * 3;
            int dotStartX = (screenWidth - totalDotsWidth) / 2;

            for (int i = 1; i <= maxStages; i++) {
                int dotX = dotStartX + (i - 1) * 7;
                int dotColor;

                if (i < stage) {
                    dotColor = 0xFF00FF00;
                } else if (i == stage) {
                    dotColor = inWindow ? 0xFFFFFF00 : 0xFFFFFFFF;
                } else {
                    dotColor = 0xFF555555;
                }

                context.fill(dotX, dotY, dotX + 4, dotY + 4, dotColor);
            }
        }

        matrices.pop();
    }


    public static boolean isActive() {
        return active;
    }

    public static String getExpectedButton() {
        return expectedButton;
    }

    public static long getWindowStart() {
        return windowStart;
    }

    public static long getWindowEnd() {
        return windowEnd;
    }

    public static int getStage() {
        return stage;
    }


    public static void setMaxStages(int max) {
        maxStages = max;
    }
}