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
        VBox page = UI.page("Clients", UI.button("Nouveau client", "primary", () -> edit(app, null, app::refresh))); page.setId("page-people");
        TextField search = UI.input("", "Nom, téléphone, e-mail ou document"); search.setPrefWidth(304); search.setAccessibleText("Rechercher un client");
        TableView<Row> table = UI.table(); table.setId("guestsTable");
        UI.column(table, "Client", 240, r -> r.text("label")); UI.column(table, "Naissance", 160, r -> UI.date(r.text("birth_date")));
        UI.column(table, "Sexe", 100, r -> r.text("sex").equals("MALE") ? "Homme" : "Femme"); UI.column(table, "Téléphone", 180, r -> r.text("phone")); UI.column(table, "E-mail", 200, r -> r.text("email"));
        UI.column(table,"Document",180,r -> r.text("identity_number"));
        Button edit = UI.button("Modifier", "secondary", () -> { Row selected = table.getSelectionModel().getSelectedItem(); if (selected != null) { edit(app, selected, app::refresh); } });
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        Button open = UI.button("Ouvrir la fiche", "secondary", () -> profile(app, table.getSelectionModel().getSelectedItem()));
        open.disableProperty().bind(edit.disableProperty());
        UI.openRows(table, person -> profile(app, person)); page.getChildren().addAll(UI.row(12, search, UI.spacer(), edit, open), table);
        app.run(app.store()::guests, guests -> {
            FilteredList<Row> filtered = new FilteredList<>(FXCollections.observableArrayList(guests)); table.setItems(filtered);
            search.textProperty().addListener((observable, old, text) -> { String term = text.toLowerCase(Locale.FRENCH); filtered.setPredicate(r -> (r.text("label") + " " + r.text("phone") + " " + r.text("email") + " " + r.text("identity_number")).toLowerCase(Locale.FRENCH).contains(term)); });
        });
        return page;
    }
    public static void profile(HotelApp app,Row person){
        app.run(() -> new Object[]{app.store().personStays(person.number("id")),app.store().keyHistory(person.number("id"))},data -> {
            @SuppressWarnings("unchecked") List<Row> stays=(List<Row>)data[0];
            @SuppressWarnings("unchecked") List<Row> history=(List<Row>)data[1];
            UI.Form form=new UI.Form(app.stage(),person.text("label"),"Historique des clés par séjour");form.stage.setWidth(960);
            ComboBox<Row> period=UI.choose(stays);TableView<Row> table=UI.table();table.setPlaceholder(UI.muted("Aucun mouvement de clés pour ce séjour"));
            UI.column(table,"Date",168,r -> UI.time(r.text("recorded_at")));UI.column(table,"Chambre",80,r -> r.text("number"));UI.column(table,"Client",160,r -> r.text("client"));
            UI.column(table,"Mouvement",180,r -> r.text("action").equals("GIVE")?"Clés remises au client":r.flag("final_return")?"Retour définitif":"Dépôt temporaire");UI.column(table,"Réceptionniste",160,r -> r.text("display_name"));
            Label details=UI.muted("");
            period.valueProperty().addListener((o,a,b) -> {if(b!=null){table.setItems(FXCollections.observableArrayList(history.stream().filter(e -> e.number("reservation_room_id")==b.number("id")).toList()));details.setText(UI.date(b.text("arrival"))+" "+b.text("arrival_time")+" → "+UI.date(b.text("departure"))+" "+b.text("departure_time")+" · "+UI.statusName(b.text("status")));}});
            form.body.getChildren().addAll(UI.row(12,UI.muted(java.util.stream.Stream.of(person.text("phone"), person.text("email")).filter(s -> !s.isBlank()).collect(java.util.stream.Collectors.joining(" · "))),UI.spacer(),UI.button("Modifier l'identité","secondary",() -> {form.stage.close();edit(app,person,app::refresh);})),UI.field("Période et chambre",period),details,table);
            form.action("Ouvrir la réservation",() -> {long id=UI.id(period);Row selected=period.getItems().stream().filter(r -> r.number("id")==id).findFirst().orElseThrow();form.stage.close();ReservationsPage.open(app,selected.number("reservation_id"));});
            if(stays.isEmpty()){form.submit.setDisable(true);}else{period.getSelectionModel().selectFirst();}form.show();
        });
    }
    public static void edit(HotelApp app, Row guest, Runnable saved) {
        UI.Form form = new UI.Form(app.stage(), guest == null ? "Nouveau client" : "Fiche client", "");
        TextField first = UI.input(guest == null ? "" : guest.text("first_name"), ""), last = UI.input(guest == null ? "" : guest.text("last_name"), "");
        TextField phone = UI.input(guest == null ? "" : guest.text("phone"), ""), email = UI.input(guest == null ? "" : guest.text("email"), "");
        UI.filter(first,100,"[\\s\\S]*",false);UI.filter(last,100,"[\\s\\S]*",false);UI.filter(email,200,"[^\\s]*",false);
        phone.setText(phone.getText().replaceAll("[^+0-9]",""));UI.filter(phone,16,"\\+?[0-9]{0,15}",false);
        DatePicker birth = UI.datePicker(guest == null ? null : guest.date("birth_date"));
        ComboBox<String> documentType=new ComboBox<>(FXCollections.observableArrayList("Carte d'identité","Passeport","Titre de séjour"));
        documentType.getSelectionModel().select(guest==null?0:guest.text("identity_type").equals("PASSPORT")?1:guest.text("identity_type").equals("RESIDENCE_PERMIT")?2:0);
        TextField document=UI.input(guest==null?"":guest.text("identity_number"),"Numéro du document",20);document.setId("guestDocument");
        Runnable filterDocument=() -> {document.setTextFormatter(null);UI.filter(document,20,documentType.getSelectionModel().getSelectedIndex()==0?"[0-9]*":"[A-Z0-9]*",true);};filterDocument.run();documentType.valueProperty().addListener((o,a,b) -> {document.clear();filterDocument.run();});
        ComboBox<String> sex = new ComboBox<>(FXCollections.observableArrayList("Homme", "Femme")); sex.setValue(guest == null || guest.text("sex").equals("MALE") ? "Homme" : "Femme");
        first.setId("guestFirst"); last.setId("guestLast"); birth.setId("guestBirth");
        form.body.getChildren().addAll(UI.columns(UI.field("Prénom", first), UI.field("Nom", last)), UI.columns(UI.field("Date de naissance", birth), UI.field("Sexe", sex)),UI.columns(UI.field("Document d'identité",documentType),UI.field("Numéro · facultatif pour un mineur",document)), UI.columns(UI.field("Téléphone (facultatif)", phone), UI.field("E-mail (facultatif)", email)));
        form.action("Enregistrer le client", () -> {
            String surname = last.getText(), given = first.getText(), gender = sex.getValue().equals("Homme") ? "MALE" : "FEMALE", contact = phone.getText(), mail = email.getText(); LocalDate date = birth.getValue();
            String type=java.util.List.of("NATIONAL_ID","PASSPORT","RESIDENCE_PERMIT").get(documentType.getSelectionModel().getSelectedIndex()),number=document.getText();
            app.save(form, () -> app.store().saveGuest(guest == null ? 0 : guest.number("id"), surname, given, date, gender, contact, mail,type,number), saved);
        }); form.show();
    }
}
