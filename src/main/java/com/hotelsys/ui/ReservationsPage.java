package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import com.hotelsys.model.Row;
import com.hotelsys.service.HotelStore;
import java.util.*;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class ReservationsPage {
    private final HotelApp app;
    public ReservationsPage(HotelApp app) { this.app = app; }
    public Node view() {
        Button add=UI.button("Nouvelle réservation","primary",() -> BookingEditor.create(app,0,app.store().today()));
        VBox page = UI.page("Réservations", add); page.setId("page-reservations");UI.bookingPrerequisite(app,add,page);
        TableView<Row> table = UI.table(); table.setId("bookingsTable"); TextField search = UI.input("", "Client ou référence"); search.setPrefWidth(248); search.setAccessibleText("Rechercher une réservation");
        ComboBox<String> status = new ComboBox<>(FXCollections.observableArrayList("Tous les séjours", "Réservé", "En séjour", "Terminé", "Annulé")); status.getSelectionModel().selectFirst();
        UI.column(table, "Référence", 100, r -> reference(r.number("id"))); UI.column(table, "Titulaire", 200, r -> r.text("label"));
        UI.column(table, "Arrivée", 140, r -> UI.date(r.text("arrival"))); UI.column(table, "Départ", 140, r -> UI.date(r.text("departure")));
        UI.column(table, "À facturer", 160, r -> UI.money(r.number("total_due"))); UI.column(table, "Solde", 160, r -> r.number("balance") < 0 ? UI.money(-r.number("balance")) + " à rembourser" : UI.money(r.number("balance"))); UI.statusColumn(table);
        Button details = UI.button("Ouvrir le dossier", "secondary", () -> { Row row = table.getSelectionModel().getSelectedItem(); if (row != null) { open(app, row.number("id")); } }); details.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        page.getChildren().addAll(UI.row(12, search, status, UI.spacer(), details), table);
        UI.openRows(table, booking -> open(app, booking.number("id")));
        app.run(app.store()::bookings, bookings -> {
            FilteredList<Row> filtered = new FilteredList<>(FXCollections.observableArrayList(bookings)); table.setItems(filtered);
            Runnable apply = () -> { String term = search.getText().toLowerCase(Locale.FRENCH); filtered.setPredicate(r -> (r.text("label") + " " + reference(r.number("id"))).toLowerCase(Locale.FRENCH).contains(term) && (status.getSelectionModel().getSelectedIndex() == 0 || UI.statusName(r.text("status")).equals(status.getValue()))); };
            search.textProperty().addListener((o,a,b) -> apply.run()); status.valueProperty().addListener((o,a,b) -> apply.run());
        }); return page;
    }
    public static String reference(long id) { return "R-" + String.format(Locale.ROOT, "%04d", id); }
    private record StayData(Row stay, List<Row> guests, List<Row> meals) { }
    private record Details(Row booking, List<StayData> stays) { }
    public static void open(HotelApp app, long id) {
        app.run(() -> {
            Row booking = app.store().bookings().stream().filter(r -> r.number("id") == id).findFirst().orElseThrow(() -> new IllegalArgumentException("Réservation introuvable"));
            List<StayData> stays = new ArrayList<>(); for (Row line : app.store().stays(id)) { stays.add(new StayData(line, app.store().stayGuests(line.number("id")), app.store().stayMeals(line.number("id")))); } return new Details(booking, stays);
        }, details -> show(app, details));
    }
    private static void show(HotelApp app, Details details) {
        Row booking = details.booking(); long id = booking.number("id");
        UI.Form form = new UI.Form(app.stage(), reference(id) + " · " + booking.text("label"), UI.date(booking.text("arrival")) + " → " + UI.date(booking.text("departure")));
        form.stage.setWidth(840);
        long refunded = booking.number("refunded");
        long balance = booking.number("balance");
        form.body.getChildren().add(UI.panel(UI.columns(
                amount("À facturer", booking.number("total_due")),
                amount("Encaissé", Math.addExact(booking.number("net_paid"), refunded)),
                amount("Remboursé", refunded),
                amount(balance < 0 ? "Reste à rembourser" : "Reste à régler", Math.abs(balance)))));
        if (!booking.text("notes").isBlank()) { form.body.getChildren().add(UI.muted(booking.text("notes"))); }
        Runnable reopen = () -> { form.stage.close(); app.refresh(); open(app, id); };
        for (StayData data : details.stays()) {
            Row line = data.stay(); long key = line.number("id");
            VBox panel = UI.panel(UI.row(12, UI.label("Chambre " + line.text("number") + " · " + line.text("model"), "section-title"), UI.spacer(), UI.badge(line.text("status"))),
                    UI.muted(UI.date(line.text("arrival")) + " à " + line.text("arrival_time") + " → " + UI.date(line.text("departure")) + " à " + line.text("departure_time") + " · " + UI.money(line.number("total"))),
                    UI.label(String.join(", ", data.guests().stream().map(r -> r.text("label")).toList()), "strong"),
                    UI.muted(data.meals().isEmpty() ? "Hébergement seul" : String.join(" · ", data.meals().stream().map(r -> UI.mealName(r.text("meal"))).toList())));
            HBox actions = UI.row(8);
            if (line.text("status").equals("RESERVED")) {
                boolean mixed = data.guests().stream().map(r -> r.text("sex")).distinct().count() > 1;
                if (mixed) {
                    panel.getChildren().add(UI.muted(line.has("verified_at") ? "Contrôle familial validé" : "Documents familiaux à vérifier avant l'arrivée"));
                    actions.getChildren().add(UI.button("Documents", "secondary", () -> verify(app, form, key, reopen)));
                }
                Button checkin=UI.button("Enregistrer l'arrivée", "primary", () -> app.run(() -> { app.store().changeStatus(key, "CHECKED_IN"); return true; }, ignored -> reopen.run()));
                checkin.setDisable(app.store().localNow().isBefore(HotelStore.start(line)) || !app.store().localNow().isBefore(HotelStore.end(line)) || mixed && !line.has("verified_at"));actions.getChildren().add(checkin);
                actions.getChildren().add(UI.button("Modifier", "secondary", () -> { form.stage.close(); BookingEditor.edit(app, booking, line, data.guests(), data.meals()); }));
                actions.getChildren().add(UI.button("Annuler", "quiet", () -> UI.confirm("Annuler le séjour de la chambre " + line.text("number") + " ? Les paiements restent dans l'historique.", () -> app.run(() -> { app.store().changeStatus(key, "CANCELLED"); return true; }, ignored -> reopen.run()))));
            } else if (line.text("status").equals("CHECKED_IN")) { actions.getChildren().add(UI.button("Prolonger", "secondary", () -> extend(app, form, line, reopen))); actions.getChildren().add(UI.button("Enregistrer le départ", "primary", () -> UI.confirm("Confirmer le départ de la chambre " + line.text("number") + " ? Le prix convenu reste inchangé.", () -> app.run(() -> { app.store().changeStatus(key, "CHECKED_OUT"); return true; }, ignored -> reopen.run())))); }
            if (line.text("status").equals("CHECKED_OUT")) {
                String checkout = line.text("checked_out_at");
                var ready = java.time.Instant.parse(checkout).plusSeconds(3 * 60 * 60);
                panel.getChildren().add(UI.muted("Départ enregistré · " + UI.time(checkout) + "  |  Chambre prête · " + UI.time(ready.toString())));
            }
            if (!actions.getChildren().isEmpty()) { panel.getChildren().add(actions); }
            form.body.getChildren().add(panel);
        }
        boolean refund = balance < 0;
        form.action(refund ? "Rembourser" : "Encaisser", () -> { form.stage.close(); CashPage.entry(app, refund ? "REFUND" : "PAYMENT", id, () -> { app.refresh(); open(app, id); }); });
        form.submit.setDisable(balance == 0 || refund && !app.store().user().admin());
        form.show();
    }
    private static VBox amount(String label, long cents) {
        return UI.box(4, UI.muted(label), UI.label(UI.money(cents), "section-title"));
    }

    private static void extend(HotelApp app,UI.Form owner,Row stay,Runnable saved){
        UI.Form form=new UI.Form(owner.stage,"Prolonger le séjour","Le prix accepté est conservé. Le supplément sera encaissé automatiquement.");
        DatePicker date=UI.datePicker(stay.date("departure").plusDays(1));TextField time=UI.timeInput(stay.text("departure_time"));
        Label supplement=UI.label("Calcul du supplément…","section-title");
        form.body.getChildren().addAll(UI.columns(UI.field("Nouveau départ",date),UI.field("Heure",time)),supplement,UI.muted("La prolongation est disponible si aucune réservation ne suit, avec trois heures de préparation."));
        javafx.animation.PauseTransition debounce=new javafx.animation.PauseTransition(javafx.util.Duration.millis(240));
        Runnable calculate=() -> {form.submit.setDisable(true);var end=date.getValue();try{var hour=UI.parseTime(time);app.run(() -> app.store().extensionQuote(stay.number("id"),end,hour),amount -> {if(java.util.Objects.equals(end,date.getValue()) && time.getText().equals(hour.toString())){supplement.setText("Supplément : "+UI.money(amount));form.error.setText("");form.submit.setDisable(false);}},form.error::setText);}catch(RuntimeException invalid){form.error.setText(invalid.getMessage());}};
        debounce.setOnFinished(e -> calculate.run());date.valueProperty().addListener((o,a,b) -> {form.submit.setDisable(true);debounce.playFromStart();});time.textProperty().addListener((o,a,b) -> {form.submit.setDisable(true);debounce.playFromStart();});form.stage.setOnHidden(e -> debounce.stop());calculate.run();
        form.action("Prolonger et encaisser",() -> {var end=date.getValue();var hour=UI.parseTime(time);app.save(form, () -> app.store().extendStay(stay.number("id"), end, hour), paid -> {
            if (paid) { app.playPaymentSound(); }
            saved.run();
        });});form.show();
    }
    private static void verify(HotelApp app, UI.Form owner, long line, Runnable saved) {
        UI.Form form = new UI.Form(owner.stage, "Contrôle familial", "Une trace de vérification, sans conserver de copie des documents");
        ComboBox<String> relation = new ComboBox<>(FXCollections.observableArrayList("Époux", "Parents par le sang", "Famille mixte")); relation.getSelectionModel().selectFirst();
        ComboBox<String> document = new ComboBox<>(FXCollections.observableArrayList("Livret de famille", "Acte de mariage", "Acte de naissance", "Autre document")); document.getSelectionModel().selectFirst();
        TextField note = UI.input("", "Précision facultative"); CheckBox confirmed = new CheckBox("J'ai vérifié la parenté de tous les occupants concernés"); confirmed.setWrapText(true);
        form.body.getChildren().addAll(UI.field("Relation vérifiée", relation), UI.field("Document présenté", document), UI.field("Note (facultatif, sauf autre document)", note), confirmed);
        form.action("Valider le contrôle", () -> {
            HotelStore.check(confirmed.isSelected(), "Confirmez la vérification des documents");
            String basis = List.of("SPOUSES", "BLOOD_RELATIVES", "MIXED_FAMILY").get(relation.getSelectionModel().getSelectedIndex());
            String type = List.of("FAMILY_BOOK", "MARRIAGE_CERTIFICATE", "BIRTH_CERTIFICATE", "OTHER").get(document.getSelectionModel().getSelectedIndex()); String text = note.getText();
            app.save(form, () -> { app.store().verifyFamily(line, basis, type, text); return true; }, saved);
        }); form.show();
    }
}
