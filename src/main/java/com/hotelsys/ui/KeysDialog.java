package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import com.hotelsys.model.Row;
import com.hotelsys.service.HotelStore;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.control.*;

public final class KeysDialog {
    private KeysDialog() { }
    public static void open(HotelApp app){
        app.run(app.store()::keyStays,stays -> {
            if(stays.isEmpty()){app.toast("Aucun séjour avec des clés à suivre");return;}
            UI.Form form=new UI.Form(app.stage(),"Clés de la réception","");
            ComboBox<Row> room=UI.choose(stays),person=UI.choose(List.of());room.setId("keysRoom");person.setId("keysPerson");
            ComboBox<String> action=new ComboBox<>(FXCollections.observableArrayList("Recevoir les clés","Remettre les clés"));action.setId("keysAction");action.getSelectionModel().selectFirst();
            ToggleButton finalReturn=UI.toggle(false);Label hint=UI.muted(""); hint.managedProperty().bind(hint.textProperty().isNotEmpty());hint.visibleProperty().bind(hint.managedProperty());
            Runnable suggest=() -> {
                Row stay=room.getValue();if(stay==null){return;}
                boolean take=action.getSelectionModel().getSelectedIndex()==0;
                boolean active=stay.text("status").equals("CHECKED_IN");
                boolean close=!HotelStore.end(stay).minusHours(3).isAfter(app.store().localNow());
                finalReturn.setSelected(take && (!active || close));finalReturn.setDisable(!take || !active);
                hint.setText(!active?"Retour définitif uniquement pour ce séjour":close?"Départ prévu sous trois heures ou déjà passé":"");
            };
            room.valueProperty().addListener((o,a,b) -> {
                person.getItems().clear();form.submit.setDisable(true);if(b==null){return;}
                action.getSelectionModel().select(b.text("last_action").equals("TAKE")?1:0);suggest.run();
                app.run(() -> app.store().stayGuests(b.number("id")),guests -> {if(room.getValue()==b){person.setItems(FXCollections.observableArrayList(guests));if(!guests.isEmpty()){person.getSelectionModel().selectFirst();form.submit.setDisable(false);}}});
            });
            action.valueProperty().addListener((o,a,b) -> suggest.run());
            form.body.getChildren().addAll(UI.field("Chambre et séjour",room),UI.field("Client",person),UI.field("Mouvement",action),UI.serviceRow("Départ définitif",finalReturn),hint,UI.muted("Le retour définitif des clés clôt le séjour."));
            form.submit.setId("saveKeys");
            form.action("Consigner le mouvement",() -> {
                long stay=UI.id(room),guest=UI.id(person);String direction=action.getSelectionModel().getSelectedIndex()==0?"TAKE":"GIVE";boolean finish=finalReturn.isSelected();
                app.save(form,() -> {app.store().keys(stay,guest,direction,finish);return true;},() -> {app.refresh();app.toast("Mouvement de clés enregistré");});
            });room.getSelectionModel().selectFirst();form.show();
        });
    }
}
