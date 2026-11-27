package com.hotelsys.ui;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.util.Duration;

public final class Motion {
    private Motion() { }
    public static boolean reduced;
    public static final Interpolator EASE = Interpolator.SPLINE(.22,1,.36,1);
    public static void enter(Node node) {
        if(reduced){return;}
        node.setOpacity(.65); node.setTranslateY(4);
        Timeline animation=new Timeline(new KeyFrame(Duration.millis(160),new KeyValue(node.opacityProperty(),1,EASE),new KeyValue(node.translateYProperty(),0,EASE)));
        play(node, "entrance", animation);
    }
    private static void play(Node node, String key, Timeline animation) {
        if (node.getProperties().put(key, animation) instanceof Animation previous) { previous.pause(); }
        animation.setOnFinished(event -> node.getProperties().remove(key, animation));
        animation.play();
    }
    public static void slide(Node node,double x){
        if (reduced) {
            if (node.getProperties().remove("slide-animation") instanceof Animation previous) { previous.stop(); }
            node.setTranslateX(x); return;
        }
        Timeline animation=new Timeline(new KeyFrame(Duration.millis(160),new KeyValue(node.translateXProperty(),x,EASE)));play(node, "slide-animation", animation);
    }
    public static void press(javafx.scene.control.ButtonBase button){
        button.pressedProperty().addListener((o,a,b) -> {
            double scale = b ? .987 : 1;
            if (reduced) {
                if (button.getProperties().remove("press-animation") instanceof Animation previous) { previous.stop(); }
                button.setScaleX(1); button.setScaleY(1); return;
            }
            play(button, "press-animation", new Timeline(new KeyFrame(Duration.millis(b ? 85 : 130),
                    new KeyValue(button.scaleXProperty(), scale, EASE), new KeyValue(button.scaleYProperty(), scale, EASE))));
        });
    }
}
