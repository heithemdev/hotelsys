package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import com.hotelsys.model.Row;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class PeoplePage {
    private final HotelApp app;
    public PeoplePage(HotelApp app) { this.app = app; }
    public Node view() {
        VBox page = UI.page("Les clients", "Les bonnes informations, pour un accueil attentionné", UI.button("+ Nouveau client", "primary", () -> edit(app, null, app::refresh))); page.setId("page-people");
        TextField search = UI.input("", "Rechercher un nom ou un téléphone"); search.setMaxWidth(360);
        TableView<Row> table = UI.table(); table.setId("guestsTable");
        UI.column(table, "Client", 240, r -> r.text("label")); UI.column(table, "Naissance", 160, r -> UI.date(r.text("birth_date")));
        UI.column(table, "Sexe", 100, r -> r.text("sex").equals("MALE") ? "Homme" : "Femme"); UI.column(table, "Téléphone", 180, r -> r.text("phone")); UI.column(table, "E-mail", 200, r -> r.text("email"));
        Button edit = UI.button("Modifier la fiche", "secondary", () -> { Row selected = table.getSelectionModel().getSelectedItem(); if (selected != null) { edit(app, selected, app::refresh); } });
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        UI.openRows(table, person -> edit(app, person, app::refresh)); page.getChildren().addAll(UI.row(16, search, UI.spacer(), edit), table, UI.muted("Double-cliquez sur une ligne pour ouvrir la fiche"));
        app.run(app.store()::guests, guests -> {
            FilteredList<Row> filtered = new FilteredList<>(FXCollections.observableArrayList(guests)); table.setItems(filtered);
            search.textProperty().addListener((observable, old, text) -> { String term = text.toLowerCase(Locale.FRENCH); filtered.setPredicate(r -> (r.text("label") + " " + r.text("phone") + " " + r.text("email")).toLowerCase(Locale.FRENCH).contains(term)); });
        });
        return page;
    }
    public static void edit(HotelApp app, Row guest, Runnable saved) {
        UI.Form form = new UI.Form(app.stage(), guest == null ? "Nouveau client" : "Fiche client", "L'identité est réutilisée pour les prochains séjours");
        TextField first = UI.input(guest == null ? "" : guest.text("first_name"), "Prénom"), last = UI.input(guest == null ? "" : guest.text("last_name"), "Nom");
        TextField phone = UI.input(guest == null ? "" : guest.text("phone"), "Facultatif"), email = UI.input(guest == null ? "" : guest.text("email"), "Facultatif");
        DatePicker birth = new DatePicker(guest == null ? null : guest.date("birth_date")); birth.setPromptText("jj/mm/aaaa");
        ComboBox<String> sex = new ComboBox<>(FXCollections.observableArrayList("Homme", "Femme")); sex.setValue(guest == null || guest.text("sex").equals("MALE") ? "Homme" : "Femme");
        first.setId("guestFirst"); last.setId("guestLast"); birth.setId("guestBirth");
        form.body.getChildren().addAll(UI.columns(UI.field("Prénom", first), UI.field("Nom", last)), UI.columns(UI.field("Date de naissance", birth), UI.field("Sexe", sex)), UI.field("Téléphone", phone), UI.field("E-mail", email), UI.muted("Les âges sont calculés à partir de la naissance. L'identité d'un séjour actif doit être corrigée avec sa réservation."));
        form.action("Enregistrer le client", () -> {
            String surname = last.getText(), given = first.getText(), gender = sex.getValue().equals("Homme") ? "MALE" : "FEMALE", contact = phone.getText(), mail = email.getText(); LocalDate date = birth.getValue();
            app.save(form, () -> app.store().saveGuest(guest == null ? 0 : guest.number("id"), surname, given, date, gender, contact, mail), saved);
        }); form.show();
    }
}
