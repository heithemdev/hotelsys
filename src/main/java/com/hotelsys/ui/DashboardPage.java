package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import com.hotelsys.model.Row;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class DashboardPage {
    private final HotelApp app;
    public DashboardPage(HotelApp app) { this.app = app; }
    private record Data(Row overview, List<Row> bookings, List<Row> receipts) { }
    public Node view() {
        Button add = UI.button("+ Nouvelle réservation", "primary", () -> BookingEditor.create(app, 0, app.store().today())); add.setId("newBooking");
        VBox page = UI.page("Tableau de bord", app.store().today().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)), add); page.setId("page-dashboard");
        VBox data = UI.box(24, UI.muted("Chargement de l'activité…")); page.getChildren().add(data);
        app.run(() -> new Data(app.store().overview(), app.store().bookings(), app.store().user().admin() ? app.store().dailyReceipts() : List.of()), result -> {
            Row o = result.overview();
            HBox metrics = UI.row(24, metric("Chambres en séjour", o.number("occupied") + " / " + o.number("rooms")), metric("Arrivées attendues", Long.toString(o.number("arrivals"))), metric("Départs du jour", Long.toString(o.number("departures"))));
            if (app.store().user().admin()) { metrics.getChildren().add(metric("Encaissements nets du jour", UI.money(o.number("revenue")))); }
            data.getChildren().setAll(metrics);
            TableView<Row> arrivals = UI.table(); arrivals.setPrefHeight(300);
            UI.column(arrivals, "Titulaire", 220, r -> r.text("label")); UI.column(arrivals, "Arrivée", 140, r -> UI.date(r.text("arrival")));
            UI.column(arrivals, "Chambres", 100, r -> r.text("rooms")); UI.statusColumn(arrivals);
            arrivals.setItems(FXCollections.observableArrayList(result.bookings().stream().filter(r -> !r.text("status").equals("CANCELLED") && !r.text("status").equals("CHECKED_OUT")).limit(8).toList()));
            UI.openRows(arrivals, r -> ReservationsPage.open(app, r.number("id")));
            VBox upcoming = UI.panel(UI.row(12, UI.label("Les prochains séjours", "section-title"), UI.spacer(), UI.button("Tout voir", "quiet", () -> app.navigate("reservations"))), arrivals);
            if (app.store().user().admin()) {
                CategoryAxis x = new CategoryAxis(); NumberAxis y = new NumberAxis();
                BarChart<String, Number> chart = new BarChart<>(x, y); chart.setLegendVisible(false); chart.setAnimated(!app.reducedMotion()); chart.setPrefHeight(260); chart.setCategoryGap(28); chart.setBarGap(0);
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                for (Row row : result.receipts()) { series.getData().add(new XYChart.Data<>(LocalDate.parse(row.text("day")).format(DateTimeFormatter.ofPattern("EEE d", Locale.FRENCH)), row.number("amount") / 100.0)); }
                chart.getData().add(series); y.setLabel("DZD");
                data.getChildren().add(UI.panel(UI.row(12, UI.label("Encaissements · 7 jours", "section-title"), UI.spacer(), UI.label("Paiements − remboursements", "small")), chart));
            } else { data.getChildren().add(UI.panel(UI.label("Préparer les prochains accueils", "section-title"), UI.muted("Consultez les chambres et les dates disponibles avant de confirmer un séjour."), UI.button("Ouvrir le calendrier", "secondary", () -> app.navigate("calendar")))); }
            data.getChildren().add(upcoming);
        });
        return UI.scroll(page);
    }
    private Node metric(String text, String value) { VBox metric = UI.box(8, UI.muted(text), UI.label(value, "stat-value")); metric.getStyleClass().add("metric"); HBox.setHgrow(metric, Priority.ALWAYS); return metric; }
}
