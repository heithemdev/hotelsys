package com.hotelsys.ui;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;

public final class PaymentSound {
    private PaymentSound(){ }
    public static void play(){
        Thread.startVirtualThread(() -> {
            try(var input=AudioSystem.getAudioInputStream(PaymentSound.class.getResource("/sounds/payment.wav"))){
                Clip clip=AudioSystem.getClip();clip.open(input);
                clip.addLineListener(event -> {if(event.getType()==LineEvent.Type.STOP){clip.close();}});clip.start();
            }catch(Exception unavailable){ /* Le paiement reste confirmé même sans périphérique audio. */ }
        });
    }
}
