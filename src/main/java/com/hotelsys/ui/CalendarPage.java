package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import com.hotelsys.model.Row;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import static java.time.DayOfWeek.MONDAY;

public final class CalendarPage {
    private static final int WEEKS = 5;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH);
    private final HotelApp app;
    private final GridPane grid = new GridPane();
    private final DatePicker jump = new DatePicker();
    private List<Row> stays = List.of();
    private LocalDate start;

    public CalendarPage(HotelApp app) {
        this.app = app;
    }

    public Node view() {
        Button add = UI.button("Nouvelle réservation", "primary", () -> BookingEditor.create(app, 0, app.store().today()));
        VBox page = UI.page("Planning des séjours", add);
        page.setId("page-calendar");
        UI.bookingPrerequisite(app, add, page);

        jump.setPrefWidth(184);
        jump.setEditable(false);
        jump.setAccessibleText("Choisir une semaine");
        jump.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(LocalDate date) { return date == null ? "" : UI.date(date.toString()); }
            @Override public LocalDate fromString(String text) { return jump.getValue(); }
        });
        Button previous = UI.button("‹", "secondary", () -> showWeek(start.minusWeeks(1)));
        previous.setId("previousWeek");
        previous.setAccessibleText("Semaine précédente");
        Button next = UI.button("›", "secondary", () -> showWeek(start.plusWeeks(1)));
        next.setId("nextWeek");
        next.setAccessibleText("Semaine suivante");
        jump.valueProperty().addListener((observable, old, selected) -> { if (selected != null) { showWeek(selected); } });
        HBox toolbar = UI.row(8, jump, previous, next,
                UI.button("Aujourd’hui", "secondary", () -> showWeek(app.store().today())), UI.spacer(),
                UI.badge("RESERVED"), UI.badge("CHECKED_IN"), UI.badge("CHECKED_OUT"));

        grid.getStyleClass().add("calendar");
        for (int day = 0; day < 7; day++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 7);
            grid.getColumnConstraints().add(column);
            String name = MONDAY.plus(day).getDisplayName(java.time.format.TextStyle.FULL, Locale.FRENCH);
            Label heading = UI.label(name, "calendar-head");
            heading.setMaxWidth(Double.MAX_VALUE);
            grid.add(heading, day, 0);
        }

        VBox daily = UI.box(0);
        page.getChildren().addAll(toolbar, grid, daily);
        showWeek(app.store().today());
        app.run(app.store()::calendar, rows -> {
            stays = rows;
            render();
            LocalDate today = app.store().today();
            daily.getChildren().setAll(UI.columns(
                    dayList("Arrivées du jour", "arrival", "RESERVED", today),
                    dayList("Départs du jour", "departure", "CHECKED_IN", today)));
        });
        return UI.scroll(page);
    }

    private void showWeek(LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(MONDAY));
        boolean changed = !monday.equals(start);
        start = monday;
        if (changed) { render(); }
        if (!monday.equals(jump.getValue())) { jump.setValue(monday); }
    }

    private void render() {
        grid.getChildren().remove(7, grid.getChildren().size());
        LocalDate today = app.store().today();
        LocalDate end = start.plusWeeks(WEEKS);
        List<List<Row>> days = new ArrayList<>();
        for (int day = 0; day < WEEKS * 7; day++) { days.add(new ArrayList<>()); }
        for (Row stay : stays) {
            LocalDate arrival = stay.date("arrival"), departure = stay.date("departure").plusDays(1);
            LocalDate first = arrival.isBefore(start) ? start : arrival;
            LocalDate last = departure.isAfter(end) ? end : departure;
            for (LocalDate date = first; date.isBefore(last); date = date.plusDays(1)) {
                days.get((int) ChronoUnit.DAYS.between(start, date)).add(stay);
            }
        }
        for (int day = 0; day < days.size(); day++) {
            LocalDate date = start.plusDays(day);
            List<Row> bookings = days.get(day);
            bookings.sort(Comparator.comparing(stay -> stay.text("status").equals("CHECKED_OUT")));
            grid.add(dayCell(date, today, bookings), day % 7, day / 7 + 1);
        }
    }

    private Node dayCell(LocalDate date, LocalDate today, List<Row> bookings) {
        Label label = UI.label(date.format(DAY), "planning-date");
        VBox cell = UI.box(2, label);
        cell.getStyleClass().add("calendar-cell");
        cell.setPrefHeight(88);
        cell.setMinWidth(0);
        if (date.equals(today)) { cell.getStyleClass().add("calendar-today"); }
        if (!date.isBefore(today)) {
            cell.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) { BookingEditor.create(app, 0, date); }
            });
        }
        for (Row stay : bookings.subList(0, Math.min(2, bookings.size()))) {
            Button entry = UI.button(stayLabel(stay), "stay-bar", () -> ReservationsPage.open(app, stay.number("reservation_id")));
            entry.getStyleClass().add("status-" + stay.text("status").toLowerCase(Locale.ROOT));
            entry.setAccessibleText(stayLabel(stay) + ", " + UI.statusName(stay.text("status")));
            entry.setTooltip(new Tooltip(entry.getAccessibleText() + "\n" + UI.date(stay.text("arrival")) + " à " + stay.text("arrival_time")
                    + " → " + UI.date(stay.text("departure")) + " à " + stay.text("departure_time")));
            entry.setMaxWidth(Double.MAX_VALUE);
            entry.setMinWidth(0);
            entry.setOnMouseClicked(event -> event.consume());
            cell.getChildren().add(entry);
        }
        if (bookings.size() > 2) {
            MenuButton more = new MenuButton("+" + (bookings.size() - 2));
            more.getStyleClass().add("calendar-more");
            more.setOnMouseClicked(event -> event.consume());
            more.setAccessibleText("Autres séjours du " + UI.date(date.toString()));
            for (Row stay : bookings.subList(2, bookings.size())) {
                MenuItem item = new MenuItem(stayLabel(stay) + " · " + UI.statusName(stay.text("status")));
                item.setOnAction(e -> ReservationsPage.open(app, stay.number("reservation_id")));
                more.getItems().add(item);
            }
            cell.getChildren().add(more);
        }
        return cell;
    }

    private String stayLabel(Row stay) { return stay.text("number") + " · " + stay.text("label"); }

    private VBox dayList(String title, String field, String status, LocalDate today) {
        VBox panel = UI.panel(UI.label(title, "section-title"));
        List<Row> rows = stays.stream().filter(s -> s.date(field).equals(today) && s.text("status").equals(status)).toList();
        if (rows.isEmpty()) { panel.getChildren().add(UI.muted("Aucun séjour prévu")); }
        for (Row stay : rows) {
            panel.getChildren().add(UI.row(8, UI.button(stay.text("label"), "quiet", () -> ReservationsPage.open(app, stay.number("reservation_id"))),
                    UI.spacer(), UI.label("Ch. " + stay.text("number"), "small")));
        }
        return panel;
    }
}
