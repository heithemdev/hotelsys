package com.hotelsys.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Tracés de type Lucide, grille 24 px et trait homogène. */
public final class Icons {
    private Icons() { }
    public static Node get(String name) {
        String data = switch (name) {
            case "key" -> "M15 3A6 6 0 1 1 11 13L3 21H0V18L8 10A6 6 0 0 1 15 3ZM17 7H17.1";
            case "check" -> "M4 12L9 17L20 6";
            case "person" -> "M20 21V19A4 4 0 0 0 16 15H8A4 4 0 0 0 4 19V21M16 7A4 4 0 1 1 8 7A4 4 0 1 1 16 7";
            case "lock" -> "M5 10H19V21H5ZM8 10V6A4 4 0 0 1 16 6V10";
            case "eye" -> "M2 12C6 3 18 3 22 12C18 21 6 21 2 12ZM15 12A3 3 0 1 1 9 12A3 3 0 1 1 15 12";
            case "eye-off" -> "M3 3L21 21M10 5C15 4 20 8 22 12L19 16M6 6L2 12C6 20 13 21 17 18";
            case "hotel" -> "M3 21H21M5 21V5H19V21M7 5V2H17V5M9 21V16H15V21M8 8H10V10H8ZM14 8H16V10H14Z";
            case "star" -> "M12 3L14.8 8.7L21 9.6L16.5 14L17.6 20.2L12 17.3L6.4 20.2L7.5 14L3 9.6L9.2 8.7Z";
            case "home" -> "M3 10L12 3L21 10V21H15V14H9V21H3Z";
            case "calendar" -> "M4 5H20V21H4ZM8 2V8M16 2V8M4 10H20";
            case "book" -> "M4 4H20V21H4ZM8 2V7M16 2V7M8 11H16M8 15H13";
            case "people" -> "M16 21V19A4 4 0 0 0 12 15H6A4 4 0 0 0 2 19V21M15 3A4 4 0 0 1 0 8M22 21V19A4 4 0 0 0 19 15M13 7A4 4 0 1 1 5 7A4 4 0 1 1 13 7";
            case "bed" -> "M3 3V21M3 17H21M21 21V11H3M7 11V7H13V11M13 7H18A3 3 0 0 1 21 10";
            case "cash" -> "M3 7H21V21H3ZM7 7V3H17V7M7 12H11M15 16H17";
            case "settings" -> "M12 3L15 5L18 5L19 8L22 10L21 13L19 15L18 19L15 19L12 22L9 20L6 20L5 17L2 14L3 11L5 9L6 5L9 5ZM16 12A4 4 0 1 1 8 12A4 4 0 1 1 16 12";
            case "logout" -> "M9 3H3V21H9M12 12H22M18 8L22 12L18 16";
            case "plus" -> "M12 4V20M4 12H20";
            case "chevron" -> "M9 5L16 12L9 19";
            default -> "M4 4H10V10H4ZM14 4H20V10H14ZM4 14H10V20H4ZM14 14H20V20H14Z";
        };
        SVGPath path = new SVGPath(); path.setContent(data); path.setFill(null); path.setStroke(Color.web("#665E68"));
        path.getStyleClass().add("line-icon"); path.setStrokeWidth(1.7); path.setStrokeLineCap(StrokeLineCap.ROUND); path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        Group group = new Group(path); group.setScaleX(.75); group.setScaleY(.75);
        javafx.scene.layout.StackPane box = new javafx.scene.layout.StackPane(group); box.setMinSize(24,24); box.setPrefSize(24,24); box.setMaxSize(24,24); return box;
    }
}
