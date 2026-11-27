package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import com.hotelsys.model.Row;
import com.hotelsys.service.HotelStore;
import java.util.LinkedHashMap;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class CashPage {
    private final HotelApp app;
    public CashPage(HotelApp app) { this.app = app; }
    public Node view() {
        Button openSession = UI.button("Ouvrir une caisse", "primary", this::open);
        openSession.setVisible(false); openSession.managedProperty().bind(openSession.visibleProperty());
        VBox page = UI.page("Caisse", openSession); page.setId("page-cash");
        ComboBox<Row> sessions = UI.choose(List.of()); sessions.setPrefWidth(360); sessions.setAccessibleText("Session de caisse");
        VBox summary = UI.box(16); TableView<Row> table = UI.table();
        UI.column(table, "Date", 170, r -> UI.time(r.text("recorded_at")));
        UI.column(table, "Mouvement", 150, r -> switch (r.text("kind")) { case "PAYMENT" -> "Encaissement"; case "REFUND" -> "Remboursement"; default -> "Dépense"; });
        UI.column(table, "Montant", 170, r -> (r.text("kind").equals("PAYMENT") ? "+ " : "− ") + UI.money(r.number("amount")));
        UI.column(table, "Réservation", 130, r -> r.has("reservation_id") ? ReservationsPage.reference(r.number("reservation_id")) : "—");
        UI.column(table,"Client",170,r -> r.text("client")); UI.column(table, "Réceptionniste", 180, r -> r.text("display_name")); UI.column(table, "Motif", 220, r -> r.text("reason"));
        sessions.valueProperty().addListener((o,old,session) -> {
            if (session == null) { return; } boolean open = !session.has("closed_at");
            HBox totals = UI.columns(metric("Fonds d'ouverture", UI.money(session.number("opening_float"))), metric("Espèces attendues", UI.money(session.number("expected_cash"))), metric(open ? "Mouvements nets" : "Écart de clôture", UI.money(open ? session.number("expected_cash") - session.number("opening_float") : session.number("discrepancy"))));
            HBox actions = UI.row(12);
            if (open) {
                actions.getChildren().add(UI.button("Encaisser", "primary", () -> entry(app, "PAYMENT", null, app::refresh)));
                if (app.store().user().admin()) { actions.getChildren().addAll(UI.button("Rembourser", "secondary", () -> entry(app, "REFUND", null, app::refresh)), UI.button("Dépense", "secondary", () -> entry(app, "EXPENSE", null, app::refresh))); }
                actions.getChildren().add(UI.spacer());
                actions.getChildren().add(UI.button("Clôturer la caisse", "secondary", () -> close(session)));
            }
            summary.getChildren().setAll(open ? UI.panel(totals, new Separator(), actions) : UI.panel(totals));
            app.run(() -> app.store().cashEntries(session.number("id")), entries -> table.setItems(FXCollections.observableArrayList(entries)));
        });
        page.getChildren().addAll(UI.row(12, UI.label("Session", "field-label"), sessions), summary, table);
        app.run(app.store()::sessions, rows -> {
            openSession.setVisible(rows.stream().noneMatch(row -> !row.has("closed_at")));
            sessions.setItems(FXCollections.observableArrayList(rows.stream().map(row -> { var values = new LinkedHashMap<>(row.values()); values.put("label", "Caisse #" + row.number("id") + " · " + row.text("owner") + " · " + (row.has("closed_at") ? "Clôturée" : "Ouverte")); return new Row(values); }).toList()));
            if (!rows.isEmpty()) { sessions.getSelectionModel().selectFirst(); } else { summary.getChildren().setAll(UI.panel(UI.label("La caisse est fermée", "section-title"), UI.muted("Ouvrez une session pour enregistrer un mouvement"))); }
        }); return page;
    }
    private Node metric(String label, String value) { return UI.box(8, UI.muted(label), UI.label(value, "stat-value")); }
    private void open() {
        UI.Form form = new UI.Form(app.stage(), "Ouvrir une caisse", ""); TextField amount = UI.moneyInput("0", "0,00");
        form.body.getChildren().addAll(UI.field("Espèces au départ · DZD", amount), UI.muted("Le fonds de caisse est exclu des recettes"));
        form.action("Ouvrir la caisse", () -> { long value = UI.cents(amount.getText()); app.save(form, () -> app.store().openCash(value), app::refresh); }); form.show();
    }
    private void close(Row session) {
        UI.Form form = new UI.Form(app.stage(), "Clôturer la caisse", "Comptez les espèces avant de confirmer la clôture");
        TextField counted = UI.moneyInput("", "0,00"), note = UI.input("", "",1000); Label difference = UI.muted("Espèces attendues : " + UI.money(session.number("expected_cash")));
        counted.textProperty().addListener((o,a,b) -> { try { difference.setText("Écart : " + UI.money(UI.cents(b) - session.number("expected_cash"))); } catch (IllegalArgumentException invalid) { difference.setText("Espèces attendues : " + UI.money(session.number("expected_cash"))); } });
        form.body.getChildren().addAll(UI.field("Espèces comptées · DZD", counted), difference, UI.field("Note de clôture (facultatif)", note), UI.muted("Les mouvements de cette caisse seront ensuite verrouillés."));
        form.action("Confirmer la clôture", () -> { long value = UI.cents(counted.getText()); String text = note.getText(); app.save(form, () -> { app.store().closeCash(session.number("id"), value, text); return true; }, app::refresh); }); form.show();
    }
    private record EntryData(List<Row> sessions, List<Row> bookings) { }
    public static void entry(HotelApp app, String kind, Long bookingId, Runnable saved) {
        app.run(() -> new EntryData(app.store().sessions().stream().filter(s -> !s.has("closed_at")).toList(), app.store().bookings()), data -> {
            if (data.sessions().isEmpty()) { app.toast("Ouvrez votre caisse avant d'enregistrer un mouvement"); app.navigate("cash"); return; }
            long session = data.sessions().getFirst().number("id"); boolean expense = kind.equals("EXPENSE"), refund = kind.equals("REFUND");
            UI.Form form = new UI.Form(app.stage(), expense ? "Enregistrer une dépense" : refund ? "Rembourser une réservation" : "Encaisser un paiement", "Caisse n° " + session);
            List<Row> eligible = data.bookings().stream().filter(b -> refund ? b.number("net_paid") > 0 : b.number("balance") > 0).map(b -> { var values = new LinkedHashMap<>(b.values()); values.put("label", ReservationsPage.reference(b.number("id")) + " · " + b.text("label")); return new Row(values); }).toList();
            ComboBox<Row> booking = UI.choose(eligible); TextField amount = UI.moneyInput("", refund ? "Saisir le montant" : "0,00"), reason = UI.input("", "",1000); Label balance = UI.muted("");
            amount.setId("cashAmount"); reason.setId("cashReason");
            booking.valueProperty().addListener((observable, old, selected) -> {
                if (selected == null) {
                    amount.clear();
                    balance.setText("");
                } else if (refund) {
                    amount.clear();
                    long due = selected.number("balance");
                    balance.setText(due < 0 ? "Reste à rembourser · " + UI.money(-due)
                            : "Encaissements nets · " + UI.money(selected.number("net_paid")));
                } else {
                    amount.setText(UI.amount(selected.number("balance")));
                    balance.setText("Reste à régler · " + UI.money(selected.number("balance")));
                }
            });
            if (bookingId != null) { UI.select(booking, bookingId); }
            if (!expense) { form.body.getChildren().addAll(UI.field("Réservation", booking), balance); }
            form.body.getChildren().addAll(UI.field("Montant · DZD", amount), UI.field(expense || refund ? "Motif" : "Motif (facultatif)", reason));
            form.action(expense ? "Enregistrer la dépense" : refund ? "Confirmer le remboursement" : "Enregistrer le paiement", () -> {
                Long id = expense ? null : UI.id(booking); long value = UI.cents(amount.getText()); String note = reason.getText();
                app.save(form, () -> { app.store().cashEntry(session, kind, id, value, note); return true; }, () -> {if (kind.equals("PAYMENT")) { app.paymentConfirmed(); } saved.run();
                    if (refund) { app.toast("Remboursement enregistré · " + UI.money(value)); } });
            }); form.show();
        });
    }
}
