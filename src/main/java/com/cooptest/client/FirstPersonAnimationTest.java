package com.cooptest.client;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;


public class FirstPersonAnimationTest {

    private static boolean isActive = false;
    private static InteractionType currentInteraction = InteractionType.NONE;

    
    public enum InteractionType {
        NONE,           
        GRAB_READY,     
        DAP_CHARGE,     
        DAP_HIT,        
        PERFECT_DAP,    
        HIGHFIVE_START, 
        HIGHFIVE_HIT,   
        HIGHFIVE_COMBO, 
        HUG,            
        PUSH,           
        THROW           
    }

   
    public static void init() {
        System.out.println("[First Person] Animation system initialized!");
    }

    
    public static void playGrabReady() {
        setFirstPersonMode(
                InteractionType.GRAB_READY,
                true, 
                true,  
                true, 
                true   
        );
    }

 
    public static void playDapCharge() {
        setFirstPersonMode(
                InteractionType.DAP_CHARGE,
                true,   
                false,  
                true,   
                false   
        );
        System.out.println("[First Person] DAP CHARGE - Right arm back, ready to strike!");
    }

   
    public static void playDapHit() {
        setFirstPersonMode(
                InteractionType.DAP_HIT,
                true,  
                false,  
                true,   
                false   
        );
        System.out.println("[First Person] DAP HIT - Forward strike with handshake!");
    }

 
    public static void playPerfectDap() {
        setFirstPersonMode(
                InteractionType.PERFECT_DAP,
                true,  
                false,  
                true,   
                false  
        );
        System.out.println("[First Person] PERFECT DAP - Extended handshake!");
    }

  
    public static void playHighFiveStart() {
        setFirstPersonMode(
                InteractionType.HIGHFIVE_START,
                true,   // Show right arm (going up)
                false,  
                true,   
                false  
        );
        System.out.println("[First Person] HIGH FIVE START - Hand going up!");
    }

   
    public static void playHighFiveHit() {
        setFirstPersonMode(
                InteractionType.HIGHFIVE_HIT,
                true,   // Show right arm (coming down)
                false,  
                true,   
                false  
        );
        System.out.println("[First Person] HIGH FIVE HIT - Hand coming down!");
    }

    
    public static void playHighFiveCombo() {
        setFirstPersonMode(
                InteractionType.HIGHFIVE_COMBO,
                true,
                true,   
                true,   
                true   
        );
        System.out.println("[First Person] HIGH FIVE COMBO - Right frozen, left going up!");
    }

    public static void playHug() {
        setFirstPersonMode(
                InteractionType.HUG,
                true,   
                true,  
                true,   
                true   
        );
        System.out.println("[First Person] HUG - Both arms out for warm hug!");
    }

    
    public static void playPush() {
        setFirstPersonMode(
                InteractionType.PUSH,
                true,  
                true,   
                true,   
                true   
        );
        System.out.println("[First Person] PUSH - Both arms pushing up!");
    }

  
    public static void playThrow() {
        setFirstPersonMode(
                InteractionType.THROW,
                true,   
                true,  
                true,   
                true    
        );
        System.out.println("[First Person] THROW - Both arms throwing!");
    }

    public static void showBothHands() {
        setFirstPersonMode(
                InteractionType.HUG, 
                true,  
                true,   
                true,   
                true    
        );
        System.out.println("[First Person] BOTH HANDS - Generic both hands visible!");
    }

    
    public static void stop() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !isActive) return;
        if (!(client.player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller != null) {
                controller.setFirstPersonMode(FirstPersonMode.NONE);
            }

            isActive = false;
            currentInteraction = InteractionType.NONE;
            System.out.println("[First Person]  Stopped - hands hidden");

        } catch (Exception e) {
            System.out.println("[First Person]  Error stopping: " + e.getMessage());
        }
    }

  
    private static void setFirstPersonMode(InteractionType type,
                                           boolean showRightArm,
                                           boolean showLeftArm,
                                           boolean showRightItem,
                                           boolean showLeftItem) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!(client.player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        try {
            PlayerAnimationController controller = getController(clientPlayer);
            if (controller == null) {
                System.out.println("[First Person] Controller not available!");
                return;
            }

            FirstPersonConfiguration config = new FirstPersonConfiguration(
                    showRightArm,    // Right arm visibility
                    showLeftArm,     // Left arm visibility
                    showRightItem,   // Right item visibility
                    showLeftItem     // Left item visibility
            );

            
            controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
            controller.setFirstPersonConfiguration(config);

            isActive = true;
            currentInteraction = type;

        } catch (Exception e) {
            System.out.println("[First Person]  Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    
    private static PlayerAnimationController getController(AbstractClientPlayerEntity player) {
        return (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(
                player,
                CoopAnimationHandler.ANIMATION_LAYER_ID
        );
    }


    public static boolean isActive() {
        return isActive;
    }

   
    public static InteractionType getCurrentInteraction() {
        return currentInteraction;
    }
}
