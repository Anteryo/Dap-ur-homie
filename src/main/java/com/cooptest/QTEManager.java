package com.cooptest;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.*;


public class QTEManager {

 
    private static final int STAGE_1_DELAY_TICKS = 8;
    private static final int STAGE_1_WINDOW_TICKS = 12;
    private static final int STAGE_2_DELAY_TICKS = 6;
    private static final int STAGE_2_WINDOW_TICKS = 9;
    private static final int STAGE_3_DELAY_TICKS = 4;
    private static final int STAGE_3_WINDOW_TICKS = 6;
    private static final int TIMEOUT_GRACE_TICKS = 4;
    private static final String[] BUTTONS = {"G", "H"};
    private static final Random RANDOM = new Random();
    private static final Map<UUID, QTESession> activeSessions = new HashMap<>();
    public static class QTESession {
        public final UUID player1Id;
        public final UUID player2Id;
        public final boolean isSolo;  // Solo debug mode (fake partner)

     
        public int currentStage;
        public final int maxStages;

     
        public int ticksInStage;        
        public int delayTicks;           
        public int windowTicks;         
        public QTEPhase phase;

      
        public String expectedButton;

        
        public boolean player1Pressed;
        public boolean player2Pressed;

        // Callback
        public QTECallback onAllStagesComplete;
        public QTECallback onFail;
        public StageCallback onStageComplete;

        // Server
        public ServerPlayerEntity player1Ref;
        public ServerPlayerEntity player2Ref;

        public enum QTEPhase {
            WAIT,       
            ACTIVE,     // Window is open - press now!
            GRACE,      // Window closed, checking for late presses
            STAGE_TRANSITION, // Brief pause between stages
            COMPLETE,   // Done (success or fail)
        }

        QTESession(UUID p1, UUID p2, int maxStages, boolean solo) {
            this.player1Id = p1;
            this.player2Id = p2;
            this.maxStages = maxStages;
            this.isSolo = solo;
            this.currentStage = 1;
            this.ticksInStage = 0;
            this.phase = QTEPhase.WAIT;
            this.player1Pressed = false;
            this.player2Pressed = false;

            applyStageTimings(1);

            this.expectedButton = BUTTONS[RANDOM.nextInt(BUTTONS.length)];
        }

        private void applyStageTimings(int stage) {
            switch (stage) {
                case 1 -> { delayTicks = STAGE_1_DELAY_TICKS; windowTicks = STAGE_1_WINDOW_TICKS; }
                case 2 -> { delayTicks = STAGE_2_DELAY_TICKS; windowTicks = STAGE_2_WINDOW_TICKS; }
                case 3 -> { delayTicks = STAGE_3_DELAY_TICKS; windowTicks = STAGE_3_WINDOW_TICKS; }
                default -> { delayTicks = STAGE_3_DELAY_TICKS; windowTicks = STAGE_3_WINDOW_TICKS; }
            }
        }

       
        public float getPhaseProgress() {
            return switch (phase) {
                case WAIT -> (float) ticksInStage / delayTicks;
                case ACTIVE -> (float) ticksInStage / windowTicks;
                case GRACE -> (float) ticksInStage / TIMEOUT_GRACE_TICKS;
                default -> 1.0f;
            };
        }
    }

    @FunctionalInterface
    public interface QTECallback {
        void execute(ServerPlayerEntity p1, ServerPlayerEntity p2);
    }

    @FunctionalInterface
    public interface StageCallback {
        void execute(ServerPlayerEntity p1, ServerPlayerEntity p2, int completedStage);
    }


    public static QTESession triggerQTE(
            ServerPlayerEntity p1,
            ServerPlayerEntity p2,
            int maxStages,
            QTECallback onSuccess,
            QTECallback onFail,
            StageCallback onStage
    ) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        if (activeSessions.containsKey(id1) || activeSessions.containsKey(id2)) {
            System.out.println("[QTE] Cannot start - player already in QTE!");
            return null;
        }

        QTESession session = new QTESession(id1, id2, Math.min(maxStages, 3), false);
        session.player1Ref = p1;
        session.player2Ref = p2;
        session.onAllStagesComplete = onSuccess;
        session.onFail = onFail;
        session.onStageComplete = onStage;

        activeSessions.put(id1, session);
        activeSessions.put(id2, session);


        sendQTEWindowToClients(session);

        System.out.println("[QTE] Started! Stage 1/" + maxStages +
                " | Button: " + session.expectedButton +
                " | Delay: " + session.delayTicks + "t, Window: " + session.windowTicks + "t");

        return session;
    }

    
    public static QTESession triggerQTESolo(
            ServerPlayerEntity player,
            int maxStages,
            QTECallback onSuccess,
            QTECallback onFail,
            StageCallback onStage
    ) {
        UUID id = player.getUuid();
        UUID fakeId = UUID.randomUUID();

        if (activeSessions.containsKey(id)) {
            System.out.println("[QTE Solo] Cannot start - player already in QTE!");
            return null;
        }

        QTESession session = new QTESession(id, fakeId, Math.min(maxStages, 3), true);
        session.player1Ref = player;
        session.player2Ref = player; // Same player for solo
        session.onAllStagesComplete = onSuccess;
        session.onFail = onFail;
        session.onStageComplete = onStage;

        activeSessions.put(id, session);
        
        sendQTEWindowToClient(player, session);

        System.out.println("[QTE Solo] Started! Stage 1/" + maxStages +
                " | Button: " + session.expectedButton);

        return session;
    }

   
    public static void onButtonPress(ServerPlayerEntity player, String button) {
        if (player == null || button == null) return;

        UUID playerId = player.getUuid();
        QTESession session = activeSessions.get(playerId);

        if (session == null) {
            System.out.println("[QTE] No active session for " + player.getName().getString());
            return;
        }

       
        if (!button.equals(session.expectedButton)) {
            System.out.println("[QTE] Wrong button! Expected " + session.expectedButton + ", got " + button);
            player.sendMessage(Text.literal("§c§lWRONG BUTTON!"), true);
            return;
        }

        if (session.phase != QTESession.QTEPhase.ACTIVE) {
            if (session.phase == QTESession.QTEPhase.WAIT) {
                System.out.println("[QTE] Too early! Window not open yet.");
                player.sendMessage(Text.literal("§c§lTOO EARLY!"), true);
            } else {
                System.out.println("[QTE] Too late! Window already closed.");
                player.sendMessage(Text.literal("§c§lTOO LATE!"), true);
            }
            return;
        }

        System.out.println("[QTE] ✓ " + player.getName().getString() + " pressed " + button + " in time!");

        if (playerId.equals(session.player1Id)) {
            session.player1Pressed = true;
        } else if (playerId.equals(session.player2Id)) {
            session.player2Pressed = true;
        }

       
        if (!session.isSolo) {
            checkBothPressed(session);
        }
    }

   
    public static boolean isInQTE(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    
    public static QTESession getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }

    public static void cancelQTE(UUID playerId) {
        QTESession session = activeSessions.get(playerId);
        if (session == null) return;

        System.out.println("[QTE] Force-cancelled for " + playerId);
        cleanupSession(session);
    }



    public static void tick(net.minecraft.server.MinecraftServer server) {
        List<QTESession> sessionsToTick = new ArrayList<>(
                activeSessions.values().stream().distinct().toList()
        );

        for (QTESession session : sessionsToTick) {
            tickSession(session, server);
        }
    }

    private static void tickSession(QTESession session, net.minecraft.server.MinecraftServer server) {
        session.ticksInStage++;

        switch (session.phase) {
            case WAIT -> {
                // Countdown before window opens
                if (session.ticksInStage >= session.delayTicks) {
                    // Window opens NOW
                    session.phase = QTESession.QTEPhase.ACTIVE;
                    session.ticksInStage = 0;

                    // Send "window open" prompt
                    sendPrompt(session);

                    System.out.println("[QTE] Window OPEN! Stage " + session.currentStage +
                            " | Button: " + session.expectedButton +
                            " | Duration: " + session.windowTicks + " ticks");
                }
            }

            case ACTIVE -> {
                // Solo mode: auto-press partner 4 ticks after real player
                if (session.isSolo && session.player1Pressed && !session.player2Pressed) {
                    if (session.ticksInStage >= 4) { // 200ms delay
                        session.player2Pressed = true;
                        System.out.println("[QTE Solo] Auto-pressed for fake partner!");
                        checkBothPressed(session);
                    }
                }

                // Window expired?
                if (session.ticksInStage >= session.windowTicks) {
                    session.phase = QTESession.QTEPhase.GRACE;
                    session.ticksInStage = 0;
                    System.out.println("[QTE] Window CLOSED. Grace period...");
                }
            }

            case GRACE -> {
                if (session.ticksInStage >= TIMEOUT_GRACE_TICKS) {
                    System.out.println("[QTE]  TIMEOUT! " + session.currentStage);

                    if (session.player1Ref != null) {
                        session.player1Ref.sendMessage(Text.literal("§c§l✖ MISSED!"), true);
                        ServerPlayNetworking.send(session.player1Ref,
                                new QTEClearPayload(session.player1Id));
                    }
                    if (session.player2Ref != null && !session.isSolo) {
                        session.player2Ref.sendMessage(Text.literal("§c§l✖ MISSED!"), true);
                        ServerPlayNetworking.send(session.player2Ref,
                                new QTEClearPayload(session.player2Id));
                    }

                    if (session.onFail != null) {
                        session.onFail.execute(session.player1Ref, session.player2Ref);
                    }

                    cleanupSession(session);
                }
            }

            case STAGE_TRANSITION -> {
                if (session.ticksInStage >= 10) {
                    session.currentStage++;
                    session.player1Pressed = false;
                    session.player2Pressed = false;
                    session.ticksInStage = 0;
                    session.phase = QTESession.QTEPhase.WAIT;
                    session.applyStageTimings(session.currentStage);

                    String oldButton = session.expectedButton;
                    do {
                        session.expectedButton = BUTTONS[RANDOM.nextInt(BUTTONS.length)];
                    } while (session.expectedButton.equals(oldButton) && BUTTONS.length > 1);

                    sendQTEWindowToClients(session);

                    System.out.println("[QTE] Stage " + session.currentStage + "/" + session.maxStages +
                            " | Button: " + session.expectedButton);
                }
            }

            case COMPLETE -> {
                cleanupSession(session);
            }
        }
    }


    private static void checkBothPressed(QTESession session) {
        if (!session.player1Pressed || !session.player2Pressed) return;

        System.out.println("[QTE] ✓✓ BOTH PRESSED! Stage " + session.currentStage + "/" + session.maxStages);

        if (session.onStageComplete != null) {
            session.onStageComplete.execute(session.player1Ref, session.player2Ref, session.currentStage);
        }

        if (session.currentStage < session.maxStages) {
            session.phase = QTESession.QTEPhase.STAGE_TRANSITION;
            session.ticksInStage = 0;

            if (session.player1Ref != null) {
                ServerPlayNetworking.send(session.player1Ref,
                        new QTEClearPayload(session.player1Id));
                session.player1Ref.sendMessage(
                        Text.literal("§a§l✓ STAGE " + session.currentStage + " CLEAR!"), true);
            }
            if (session.player2Ref != null && !session.isSolo) {
                ServerPlayNetworking.send(session.player2Ref,
                        new QTEClearPayload(session.player2Id));
                session.player2Ref.sendMessage(
                        Text.literal("§a§l✓ STAGE " + session.currentStage + " CLEAR!"), true);
            }
        } else {
            System.out.println("[QTE] ★★★ ALL STAGES COMPLETE! ★★★");
            session.phase = QTESession.QTEPhase.COMPLETE;

            if (session.player1Ref != null) {
                ServerPlayNetworking.send(session.player1Ref,
                        new QTEClearPayload(session.player1Id));
            }
            if (session.player2Ref != null && !session.isSolo) {
                ServerPlayNetworking.send(session.player2Ref,
                        new QTEClearPayload(session.player2Id));
            }

            if (session.onAllStagesComplete != null) {
                session.onAllStagesComplete.execute(session.player1Ref, session.player2Ref);
            }

            cleanupSession(session);
        }
    }

    private static void sendQTEWindowToClients(QTESession session) {

        long clientNow = System.currentTimeMillis();
        long windowStartOffset = (long) session.delayTicks * 50;
        long windowDuration = (long) session.windowTicks * 50;

        if (session.player1Ref != null) {
            ServerPlayNetworking.send(session.player1Ref, new QTEWindowPayload(
                    session.player1Id,
                    session.expectedButton,
                    session.currentStage,
                    clientNow + windowStartOffset,  // Client reconstructs as "my now + offset"
                    clientNow + windowStartOffset + windowDuration
            ));
        }

        if (session.player2Ref != null && !session.isSolo) {
            ServerPlayNetworking.send(session.player2Ref, new QTEWindowPayload(
                    session.player2Id,
                    session.expectedButton,
                    session.currentStage,
                    clientNow + windowStartOffset,
                    clientNow + windowStartOffset + windowDuration
            ));
        }
    }

    private static void sendQTEWindowToClient(ServerPlayerEntity player, QTESession session) {
        long clientNow = System.currentTimeMillis();
        long windowStartOffset = (long) session.delayTicks * 50;
        long windowDuration = (long) session.windowTicks * 50;

        ServerPlayNetworking.send(player, new QTEWindowPayload(
                player.getUuid(),
                session.expectedButton,
                session.currentStage,
                clientNow + windowStartOffset,
                clientNow + windowStartOffset + windowDuration
        ));
    }

    private static void sendPrompt(QTESession session) {
        String stageText = session.maxStages > 1
                ? " §7(Stage " + session.currentStage + "/" + session.maxStages + ")"
                : "";

        if (session.player1Ref != null) {
            session.player1Ref.sendMessage(
                    Text.literal("§e§lPRESS [" + session.expectedButton + "]!" + stageText), true);
        }
        if (session.player2Ref != null && !session.isSolo) {
            session.player2Ref.sendMessage(
                    Text.literal("§e§lPRESS [" + session.expectedButton + "]!" + stageText), true);
        }
    }

    private static void cleanupSession(QTESession session) {
        session.phase = QTESession.QTEPhase.COMPLETE;
        activeSessions.remove(session.player1Id);
        activeSessions.remove(session.player2Id);
        System.out.println("[QTE] Session cleaned up.");
    }
}
