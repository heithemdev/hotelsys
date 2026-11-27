package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import com.hotelsys.model.Row;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class CalendarPage {
    private final HotelApp app;
    private LocalDate start;
    private final VBox calendar = UI.box(20);
    private final DatePicker jump = new DatePicker();
    public CalendarPage(HotelApp app) { this.app = app; start = app.store().today().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)); }
    private record Data(List<Row> rooms, List<Row> stays, List<Row> todayStays) { }
    public Node view() {
        VBox page = UI.page("Planning des séjours", "Disponibilité par chambre", UI.button("+ Nouvelle réservation", "primary", () -> BookingEditor.create(app, 0, app.store().today()))); page.setId("page-calendar");
        Button previous = UI.button("‹", "secondary", () -> { start = start.minusWeeks(1); load(); }); previous.setAccessibleText("Semaine précédente");
        Button next = UI.button("›", "secondary", () -> { start = start.plusWeeks(1); load(); }); next.setAccessibleText("Semaine suivante");
        jump.setPrefWidth(180); jump.setEditable(false);
        jump.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(LocalDate date) { return date == null ? "" : date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)); }
            @Override public LocalDate fromString(String text) { return jump.getValue(); }
        });
        jump.setOnAction(event -> { if (jump.getValue() != null) { LocalDate selected = jump.getValue().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)); if (!selected.equals(start)) { start = selected; load(); } } });
        HBox toolbar = UI.row(8, jump, previous, next, UI.button("Aujourd'hui", "secondary", () -> { start = app.store().today().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)); load(); }), UI.spacer(), UI.badge("RESERVED"), UI.badge("CHECKED_IN"), UI.badge("CHECKED_OUT"));
        page.getChildren().addAll(UI.panel(toolbar), calendar); load(); return UI.scroll(page);
    }
    private void load() {
        LocalDate week = start;
        jump.setValue(week);
        app.run(() -> new Data(app.store().rooms(), app.store().calendar(week, week.plusDays(7)), app.store().calendar(app.store().today().minusDays(1), app.store().today().plusDays(1))), data -> { if (week.equals(start)) { render(week, data); } });
    }
    private void render(LocalDate week, Data data) {
        GridPane grid = new GridPane(); grid.getStyleClass().add("calendar"); grid.setMinWidth(900);
        grid.getColumnConstraints().add(new ColumnConstraints(150));
        for (int i = 0; i < 7; i++) { ColumnConstraints day = new ColumnConstraints(); day.setHgrow(Priority.ALWAYS); day.setMinWidth(100); grid.getColumnConstraints().add(day); }
        grid.add(cell(UI.label("Chambre", "strong"), false, true, 60), 0, 0);
        for (int day = 0; day < 7; day++) { LocalDate date = week.plusDays(day); grid.add(cell(UI.label(date.format(DateTimeFormatter.ofPattern("EEE d", Locale.FRENCH)), "strong"), date.equals(app.store().today()), true, 60), day + 1, 0); }
        if (data.rooms().isEmpty()) { calendar.getChildren().setAll(UI.panel(UI.label("Votre planning commence avec les chambres", "section-title"), UI.muted("Créez un modèle et vos chambres pour préparer le premier séjour"), UI.button("Gérer les chambres", "secondary", () -> app.navigate("rooms")))); return; }
        int row = 1;
        for (Row room : data.rooms()) {
            VBox title = UI.box(4, UI.label(room.text("number"), "strong"), UI.label(room.text("model") + " · " + room.number("bed_count") + " lits", "small"));
            if (!room.flag("active")) { title.getChildren().add(UI.label("Inactive", "small")); }
            grid.add(cell(title, false, false, 72), 0, row);
            for (int day = 0; day < 7; day++) {
                LocalDate date = week.plusDays(day); StackPane tile = cell(new Region(), date.equals(app.store().today()), false, 72);
                if (room.flag("active")) { tile.setOnMouseClicked(event -> { if (event.getClickCount() == 2) { BookingEditor.create(app, room.number("id"), date.isBefore(app.store().today()) ? app.store().today() : date); } }); }
                grid.add(tile, day + 1, row);
            }
            for (Row stay : data.stays()) {
                if (stay.number("room_id") != room.number("id")) { continue; }
                int from = (int) Math.max(0, ChronoUnit.DAYS.between(week, stay.date("arrival")));
                int until = (int) Math.min(7, ChronoUnit.DAYS.between(week, stay.date("departure")));
                String summary = stay.number("guests") + (stay.number("guests") == 1 ? " personne" : " personnes");
                if (stay.text("meals").contains("BREAKFAST")) { summary += " · Petit-déj."; }
                VBox bar = UI.box(4, UI.label(stay.text("label"), "strong"), UI.label(summary, "small"));
                bar.getStyleClass().addAll("stay-bar", "status-" + stay.text("status").toLowerCase(Locale.ROOT)); bar.setMaxHeight(60); GridPane.setMargin(bar, new Insets(6));
                bar.setOnMouseClicked(event -> ReservationsPage.open(app, stay.number("reservation_id"))); bar.setFocusTraversable(true); bar.setAccessibleText(stay.text("label") + ", chambre " + room.text("number"));
                bar.setOnKeyPressed(event -> { if (event.getCode() == javafx.scene.input.KeyCode.ENTER) { ReservationsPage.open(app, stay.number("reservation_id")); } });
                grid.add(bar, from + 1, row, Math.max(1, until - from), 1);
            }
            row++;
        }
        ScrollPane horizontal = new ScrollPane(grid); horizontal.setFitToWidth(true); horizontal.getStyleClass().add("clean-scroll"); horizontal.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        LocalDate today = app.store().today();
        VBox arrivals = dayList("Arrivées du jour", data.todayStays().stream().filter(s -> s.date("arrival").equals(today) && s.text("status").equals("RESERVED")).toList());
        VBox departures = dayList("Départs du jour", data.todayStays().stream().filter(s -> s.date("departure").equals(today) && s.text("status").equals("CHECKED_IN")).toList());
        calendar.getChildren().setAll(horizontal, UI.columns(arrivals, departures));
    }
    private VBox dayList(String title, List<Row> rows) {
        VBox panel = UI.panel(UI.label(title, "section-title"));
        if (rows.isEmpty()) { panel.getChildren().add(UI.muted("Aucun séjour prévu")); }
        for (Row stay : rows) { panel.getChildren().add(UI.row(8, UI.button(stay.text("label"), "quiet", () -> ReservationsPage.open(app, stay.number("reservation_id"))), UI.spacer(), UI.label("Ch. " + stay.text("number"), "small"))); } return panel;
    }
    private StackPane cell(Node node, boolean today, boolean head, double height) {
        StackPane pane = new StackPane(node); pane.setAlignment(javafx.geometry.Pos.CENTER_LEFT); pane.setMinHeight(height); pane.setPrefHeight(height); pane.setMaxHeight(height); pane.getStyleClass().add("calendar-cell");
        if (head) { pane.getStyleClass().add("calendar-head"); } if (today) { pane.getStyleClass().add("calendar-today"); } return pane;
    }
}
