package com.hotelsys.ui;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineEvent;

public final class PaymentSound {
    private PaymentSound() { }

    public static void play(Consumer<Exception> failed) {
        Thread.startVirtualThread(() -> {
            try (var input = AudioSystem.getAudioInputStream(PaymentSound.class.getResource("/sounds/payment.wav"));
                 var clip = AudioSystem.getClip()) {
                clip.open(input);
                var finished = new CountDownLatch(1);
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) { finished.countDown(); }
                });
                clip.start();
                long timeout = TimeUnit.MICROSECONDS.toMillis(clip.getMicrosecondLength()) + 1500;
                if (!finished.await(timeout, TimeUnit.MILLISECONDS) || clip.getFramePosition() < clip.getFrameLength()) {
                    throw new IOException("Lecture incomplète du son de paiement");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                failed.accept(interrupted);
            } catch (Exception unavailable) {
                failed.accept(unavailable);
            }
        });
    }
}
