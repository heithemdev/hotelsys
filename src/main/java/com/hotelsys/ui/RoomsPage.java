package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import com.hotelsys.model.Row;
import com.hotelsys.service.HotelStore;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class RoomsPage {
    private final HotelApp app;
    public RoomsPage(HotelApp app) { this.app = app; }
    private record Data(List<Row> rooms, List<Row> models) { }
    public Node view() {
        HBox actions = UI.row(12);
        if (app.store().user().admin()) { actions.getChildren().addAll(UI.button("Nouveau modèle", "secondary", () -> model(null)), UI.button("Nouvelle chambre", "primary", () -> room(null))); }
        VBox page = UI.page("Chambres", actions); page.setId("page-rooms");
        TabPane tabs = new TabPane(); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        TilePane cards = new TilePane(16, 16);
        ScrollPane inventory = UI.scroll(cards);
        cards.widthProperty().addListener((obs, old, newWidth) -> {
            double width = newWidth.doubleValue();
            int columns = Math.max(1, (int) ((width + 16) / 284));
            cards.setPrefTileWidth(Math.max(1, Math.floor((width + 16) / columns) - 17));
        });
        VBox modelList = UI.box(16); Tab physical = new Tab("Chambres", inventory), models = new Tab("Modèles", UI.scroll(modelList)); tabs.getTabs().addAll(physical, models); VBox.setVgrow(tabs, Priority.ALWAYS); page.getChildren().add(tabs);
        if(!actions.getChildren().isEmpty()){actions.getChildren().getLast().setDisable(true);}
        app.run(() -> new Data(app.store().rooms(), app.store().models()), data -> {
            if(app.store().user().admin()){
                Button create=(Button)actions.getChildren().getLast();create.setDisable(data.models().isEmpty());
            }
            if (data.rooms().isEmpty()) { VBox empty=UI.panel(Icons.get("bed"),UI.label(data.models().isEmpty()?"Créez un modèle avant vos chambres":"Ajoutez votre première chambre", "section-title"), UI.muted("Un modèle réunit le prix par nuit, la capacité et la liste des équipements"));if(app.store().user().admin()){empty.getChildren().add(UI.button(data.models().isEmpty()?"Créer un modèle":"Créer une chambre","primary",() -> {if(data.models().isEmpty()){model(null);}else{room(null);}}));}cards.getChildren().add(empty); }
            for (Row room : data.rooms()) {
                Label number = UI.label(room.text("number"), "room-number");
                VBox card = UI.box(12, UI.row(8, number, UI.spacer(), room.flag("active") ? UI.badge(room.text("occupancy")) : UI.badge("CANCELLED")),
                        UI.box(4, UI.label(room.text("model"), "section-title"), UI.muted(capacity(room))));
                if (!room.text("facilities").isBlank()) { card.getChildren().add(UI.muted(room.text("facilities").replace("\n"," · "))); }
                Region gap = new Region(); VBox.setVgrow(gap, Priority.ALWAYS);
                card.getChildren().addAll(gap, new Separator(), UI.label(UI.money(room.number("nightly_price")) + " / nuit", "strong"));
                card.setMaxWidth(Double.MAX_VALUE);
                card.getStyleClass().add("room-card");
                if (!room.flag("active")) { ((Label) ((HBox) card.getChildren().getFirst()).getChildren().getLast()).setText("Inactive"); }
                if (app.store().user().admin()) {
                    card.getChildren().add(UI.row(8, UI.button("Modifier", "secondary", () -> room(room)), UI.button("Supprimer", "quiet", () -> UI.confirm("Supprimer la chambre " + room.text("number") + " ? Les chambres avec historique doivent être désactivées.", () -> app.run(() -> { app.store().deleteRoom(room.number("id")); return true; }, ignored -> app.refresh())))));
                }
                cards.getChildren().add(card);
            }
            if (data.models().isEmpty()) { modelList.getChildren().add(UI.panel(UI.label("Définissez votre premier modèle", "section-title"), UI.muted("Exemple : Classique, 2 lits, télévision, un enfant sans lit"))); }
            for (Row m : data.models()) {
                VBox description = UI.box(4, UI.label(m.text("name"), "section-title"), UI.muted(capacity(m)));
                if (!m.text("facilities").isBlank()) { description.getChildren().add(UI.muted(m.text("facilities").replace("\n"," · "))); }
                HBox line = UI.row(16, description, UI.spacer(), UI.label(UI.money(m.number("nightly_price")) + " / nuit", "strong")); if (app.store().user().admin()) { line.getChildren().add(UI.button("Modifier", "secondary", () -> model(m))); } modelList.getChildren().add(UI.panel(line));
            }
        }); return page;
    }
    static String capacity(Row model) {
        return UI.quantity(model.number("bed_count"), "lit", "lits") + " · "
                + UI.quantity(model.number("max_sharing_children"), "enfant sans lit", "enfants sans lit");
    }
    public void room(Row existing) {
        app.run(app.store()::models, models -> {
            if (models.isEmpty()) { return; }
            UI.Form form = new UI.Form(app.stage(), existing == null ? "Nouvelle chambre" : "Modifier la chambre", "Le tarif et les équipements sont hérités du modèle");
            TextField number = UI.input(existing == null ? "" : existing.text("number"), "Ex. 101"); ComboBox<Row> template = UI.choose(models);
            UI.filter(number,20,"[A-Z0-9-]*",true);
            if (existing != null) { UI.select(template, existing.number("model_id")); }
            Label price = UI.muted("");
            price.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() -> template.getValue() == null
                    ? "" : UI.money(template.getValue().number("nightly_price")) + " / nuit", template.valueProperty()));
            price.managedProperty().bind(price.textProperty().isNotEmpty()); price.visibleProperty().bind(price.managedProperty());
            CheckBox active = new CheckBox("Chambre active pour les nouvelles réservations"); active.setSelected(existing == null || existing.flag("active"));
            form.body.getChildren().addAll(UI.columns(UI.field("Numéro", number), UI.field("Modèle", template)), price, active);
            if (existing != null) { form.body.getChildren().add(UI.muted("Une chambre désactivée conserve son historique.")); }
            form.action("Enregistrer la chambre", () -> { long model = UI.id(template); String roomNumber = number.getText(); boolean available = active.isSelected();
                app.save(form, () -> { app.store().saveRoom(existing == null ? 0 : existing.number("id"), roomNumber, model, available); return true; }, app::refresh); }); form.show();
        });
    }
    public void model(Row existing) {
        UI.Form form = new UI.Form(app.stage(), existing == null ? "Nouveau modèle" : "Modifier le modèle", "Le tarif s’applique à toutes les chambres de ce modèle");
        TextField name=UI.input(existing==null?"":existing.text("name"),"Ex. Classique",80);
        TextField beds=UI.countInput(existing==null?"2":existing.text("bed_count"),1,100),sharing=UI.countInput(existing==null?"1":existing.text("max_sharing_children"),0,20);
        TextField price=UI.moneyInput(existing==null?"":UI.amount(existing.number("nightly_price")),"0,00");
        VBox equipment=UI.box(8);List<String> items=new ArrayList<>();name.setId("modelName");price.setId("modelPrice");form.submit.setId("saveModel");
        TextField item=UI.input("","Ajouter un équipement",100);
        item.setId("equipmentInput");HBox.setHgrow(item,Priority.ALWAYS);
        Runnable render=() -> {
            equipment.getChildren().clear();
            for(String value:List.copyOf(items)){
                HBox line=UI.row(8,Icons.get("check"),UI.label(value,"strong"),UI.spacer(),UI.button("Retirer","quiet",() -> {items.remove(value);equipment.getChildren().removeIf(n -> value.equals(n.getUserData()));}));line.setUserData(value);line.getStyleClass().add("equipment-row");equipment.getChildren().add(line);
            }
        };
        if(existing!=null){for(String value:existing.text("facilities").split("[\\n,·]+")){if(!value.isBlank()){items.add(value.strip());}}}render.run();
        Button add=UI.button("Ajouter","secondary",() -> {String value=HotelStore.required(item.getText(),"Équipement",100);HotelStore.check(items.stream().noneMatch(v -> v.equalsIgnoreCase(value)),"Cet équipement est déjà dans la liste");items.add(value);item.clear();render.run();Motion.enter(equipment.getChildren().getLast());});
        add.setId("equipmentAdd");
        item.setOnAction(e -> {add.fire();e.consume();});
        form.body.getChildren().addAll(UI.field("Nom du modèle",name),UI.columns(UI.field("Nombre de lits",beds),UI.field("Enfants sans lit maximum",sharing)),UI.field("Prix par nuit · DZD",price),UI.label("Équipements (facultatif)","section-title"),UI.row(8,item,add),equipment);
        form.action("Enregistrer le modèle",() -> {
            HotelStore.check(item.getText().isBlank(),"Ajoutez l'équipement saisi à la liste ou effacez-le");
            String title=name.getText(),facilities=String.join("\n",items);int capacity=UI.integer(beds.getText()),allowance=UI.integer(sharing.getText());long amount=UI.cents(price.getText());
            app.save(form,() -> app.store().saveModel(existing==null?0:existing.number("id"),title,capacity,allowance,facilities,amount),app::refresh);
        });form.show();
    }
}
