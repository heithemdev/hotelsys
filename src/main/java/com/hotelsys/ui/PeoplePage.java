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
        VBox page = UI.page("Clients", "Identités et coordonnées", UI.button("+ Nouveau client", "primary", () -> edit(app, null, app::refresh))); page.setId("page-people");
        TextField search = UI.input("", "Rechercher un nom ou un téléphone"); search.setMaxWidth(360);
        TableView<Row> table = UI.table(); table.setId("guestsTable");
        UI.column(table, "Client", 240, r -> r.text("label")); UI.column(table, "Naissance", 160, r -> UI.date(r.text("birth_date")));
        UI.column(table, "Sexe", 100, r -> r.text("sex").equals("MALE") ? "Homme" : "Femme"); UI.column(table, "Téléphone", 180, r -> r.text("phone")); UI.column(table, "E-mail", 200, r -> r.text("email"));
        UI.column(table,"Document",180,r -> r.text("identity_number"));
        Button edit = UI.button("Modifier la fiche", "secondary", () -> { Row selected = table.getSelectionModel().getSelectedItem(); if (selected != null) { edit(app, selected, app::refresh); } });
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        UI.openRows(table, person -> edit(app, person, app::refresh)); page.getChildren().addAll(UI.row(16, search, UI.spacer(), edit), table, UI.muted("Double-cliquez sur une ligne pour ouvrir la fiche"));
        app.run(app.store()::guests, guests -> {
            FilteredList<Row> filtered = new FilteredList<>(FXCollections.observableArrayList(guests)); table.setItems(filtered);
            search.textProperty().addListener((observable, old, text) -> { String term = text.toLowerCase(Locale.FRENCH); filtered.setPredicate(r -> (r.text("label") + " " + r.text("phone") + " " + r.text("email") + " " + r.text("identity_number")).toLowerCase(Locale.FRENCH).contains(term)); });
        });
        return page;
    }
    public static void edit(HotelApp app, Row guest, Runnable saved) {
        UI.Form form = new UI.Form(app.stage(), guest == null ? "Nouveau client" : "Fiche client", "Identité, document et coordonnées");
        TextField first = UI.input(guest == null ? "" : guest.text("first_name"), "Prénom"), last = UI.input(guest == null ? "" : guest.text("last_name"), "Nom");
        TextField phone = UI.input(guest == null ? "" : guest.text("phone"), "Facultatif"), email = UI.input(guest == null ? "" : guest.text("email"), "Facultatif");
        UI.filter(first,100,"[\\s\\S]*",false);UI.filter(last,100,"[\\s\\S]*",false);UI.filter(email,200,"[^\\s]*",false);
        phone.setText(phone.getText().replaceAll("[^+0-9]",""));UI.filter(phone,16,"\\+?[0-9]{0,15}",false);
        DatePicker birth = UI.datePicker(guest == null ? null : guest.date("birth_date"));
        ComboBox<String> documentType=new ComboBox<>(FXCollections.observableArrayList("Carte d'identité","Passeport","Titre de séjour"));
        documentType.getSelectionModel().select(guest==null?0:guest.text("identity_type").equals("PASSPORT")?1:guest.text("identity_type").equals("RESIDENCE_PERMIT")?2:0);
        TextField document=UI.input(guest==null?"":guest.text("identity_number"),"Numéro du document",20);document.setId("guestDocument");
        Runnable filterDocument=() -> {document.setTextFormatter(null);UI.filter(document,20,documentType.getSelectionModel().getSelectedIndex()==0?"[0-9]*":"[A-Z0-9]*",true);};filterDocument.run();documentType.valueProperty().addListener((o,a,b) -> {document.clear();filterDocument.run();});
        ComboBox<String> sex = new ComboBox<>(FXCollections.observableArrayList("Homme", "Femme")); sex.setValue(guest == null || guest.text("sex").equals("MALE") ? "Homme" : "Femme");
        first.setId("guestFirst"); last.setId("guestLast"); birth.setId("guestBirth");
        form.body.getChildren().addAll(UI.columns(UI.field("Prénom", first), UI.field("Nom", last)), UI.columns(UI.field("Date de naissance", birth), UI.field("Sexe", sex)),UI.columns(UI.field("Document d'identité",documentType),UI.field("Numéro · facultatif pour un mineur",document)), UI.field("Téléphone", phone), UI.field("E-mail", email));
        form.action("Enregistrer le client", () -> {
            String surname = last.getText(), given = first.getText(), gender = sex.getValue().equals("Homme") ? "MALE" : "FEMALE", contact = phone.getText(), mail = email.getText(); LocalDate date = birth.getValue();
            String type=java.util.List.of("NATIONAL_ID","PASSPORT","RESIDENCE_PERMIT").get(documentType.getSelectionModel().getSelectedIndex()),number=document.getText();
            app.save(form, () -> app.store().saveGuest(guest == null ? 0 : guest.number("id"), surname, given, date, gender, contact, mail,type,number), saved);
        }); form.show();
    }
}
