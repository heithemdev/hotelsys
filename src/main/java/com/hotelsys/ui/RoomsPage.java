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
        if (app.store().user().admin()) { actions.getChildren().addAll(UI.button("Nouveau modèle", "secondary", () -> model(null)), UI.button("+ Nouvelle chambre", "primary", () -> room(null))); }
        VBox page = UI.page("Les chambres", "Un modèle pour les équipements, un prix pour chaque chambre", actions); page.setId("page-rooms");
        TabPane tabs = new TabPane(); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        TilePane cards = new TilePane(20, 20); cards.setPrefTileWidth(284); cards.setPrefColumns(3);
        VBox modelList = UI.box(16); Tab physical = new Tab("Chambres", UI.scroll(cards)), models = new Tab("Modèles", UI.scroll(modelList)); tabs.getTabs().addAll(physical, models); VBox.setVgrow(tabs, Priority.ALWAYS); page.getChildren().add(tabs);
        app.run(() -> new Data(app.store().rooms(), app.store().models()), data -> {
            if (data.rooms().isEmpty()) { cards.getChildren().add(UI.panel(UI.label("Aucune chambre pour le moment", "section-title"), UI.muted("Commencez par créer un modèle, puis vos chambres"))); }
            for (Row room : data.rooms()) {
                VBox card = UI.box(16, UI.row(12, UI.label(room.text("number"), "stat-value"), UI.spacer(), room.flag("active") ? UI.badge(room.text("occupancy")) : UI.badge("CANCELLED")), UI.label(room.text("model"), "section-title"), UI.muted(room.number("bed_count") + " lits · " + room.number("max_sharing_children") + " enfant(s) sans lit"), UI.muted(room.text("facilities")), UI.label(UI.money(room.number("nightly_price")) + " / nuit", "strong"));
                card.getStyleClass().add("room-card");
                if (!room.flag("active")) { ((Label) ((HBox) card.getChildren().getFirst()).getChildren().getLast()).setText("Inactive"); }
                if (app.store().user().admin()) {
                    card.getChildren().add(UI.row(8, UI.button("Modifier", "secondary", () -> room(room)), UI.button("Supprimer", "quiet", () -> UI.confirm("Supprimer la chambre " + room.text("number") + " ? Les chambres avec historique doivent être désactivées.", () -> app.run(() -> { app.store().deleteRoom(room.number("id")); return true; }, ignored -> app.refresh())))));
                }
                cards.getChildren().add(card);
            }
            if (data.models().isEmpty()) { modelList.getChildren().add(UI.panel(UI.label("Définissez votre premier modèle", "section-title"), UI.muted("Exemple : Classique, 2 lits, télévision, un enfant sans lit"))); }
            for (Row m : data.models()) {
                VBox description = UI.box(8, UI.label(m.text("name"), "section-title"), UI.muted(m.number("bed_count") + " lits · " + m.number("max_sharing_children") + " enfant(s) sans lit"), UI.muted(m.text("facilities")));
                HBox line = UI.row(16, description, UI.spacer()); if (app.store().user().admin()) { line.getChildren().add(UI.button("Modifier le modèle", "secondary", () -> model(m))); } modelList.getChildren().add(UI.panel(line));
            }
        }); return page;
    }
    private void room(Row existing) {
        app.run(app.store()::models, models -> {
            if (models.isEmpty()) { app.toast("Créez d'abord un modèle de chambre"); model(null); return; }
            UI.Form form = new UI.Form(app.stage(), existing == null ? "Nouvelle chambre" : "Modifier la chambre", "Le prix de la nuit concerne toute la chambre");
            TextField number = UI.input(existing == null ? "" : existing.text("number"), "Ex. 101"); ComboBox<Row> template = UI.choose(models);
            if (existing != null) { UI.select(template, existing.number("model_id")); }
            TextField price = UI.input(existing == null ? "" : UI.amount(existing.number("nightly_price")), "0,00"); CheckBox active = new CheckBox("Chambre active pour les nouvelles réservations"); active.setSelected(existing == null || existing.flag("active"));
            form.body.getChildren().addAll(UI.columns(UI.field("Numéro", number), UI.field("Modèle", template)), UI.field("Prix par nuit · DZD", price), active, UI.muted("Une désactivation conserve les réservations existantes et leur historique"));
            form.action("Enregistrer la chambre", () -> { long model = UI.id(template), amount = UI.cents(price.getText()); String roomNumber = number.getText(); boolean available = active.isSelected();
                app.save(form, () -> { app.store().saveRoom(existing == null ? 0 : existing.number("id"), roomNumber, model, amount, available); return true; }, app::refresh); }); form.show();
        });
    }
    private void model(Row existing) {
        app.run(() -> existing == null ? List.<Row>of() : app.store().mealRates(existing.number("id")), rates -> {
            UI.Form form = new UI.Form(app.stage(), existing == null ? "Nouveau modèle" : "Modifier le modèle", "Les nouveaux tarifs s'appliquent aux prochains devis");
            TextField name = UI.input(existing == null ? "" : existing.text("name"), "Ex. Classique"); TextField beds = UI.input(existing == null ? "2" : existing.text("bed_count"), "Lits"); TextField sharing = UI.input(existing == null ? "1" : existing.text("max_sharing_children"), "Enfants sans lit");
            TextArea facilities = new TextArea(existing == null ? "" : existing.text("facilities")); facilities.setPromptText("Télévision, balcon, jacuzzi…"); facilities.setPrefRowCount(2); facilities.setWrapText(true);
            form.body.getChildren().addAll(UI.field("Nom du modèle", name), UI.columns(UI.field("Nombre de lits", beds), UI.field("Enfants sans lit maximum", sharing)), UI.field("Équipements", facilities), UI.label("Repas par personne et par nuit", "section-title"));
            List<MealFields> fields = new ArrayList<>();
            for (String meal : List.of("BREAKFAST", "LUNCH", "DINNER")) {
                Row rate = rates.stream().filter(r -> r.text("meal").equals(meal)).findFirst().orElse(null); MealFields input = new MealFields(meal, rate); fields.add(input); form.body.getChildren().add(input.view());
            }
            form.action("Enregistrer le modèle", () -> {
                String title = name.getText(), equipment = facilities.getText(); int capacity = UI.integer(beds.getText()), allowance = UI.integer(sharing.getText());
                List<HotelStore.MealRate> prices = fields.stream().filter(f -> f.enabled.isSelected()).map(MealFields::rate).toList();
                app.save(form, () -> app.store().saveModel(existing == null ? 0 : existing.number("id"), title, capacity, allowance, equipment, prices), app::refresh);
            }); form.show();
        });
    }
    private static final class MealFields {
        private final String meal;
        private final CheckBox enabled;
        private final TextField adult, child;
        private final ComboBox<String> mode = new ComboBox<>(FXCollections.observableArrayList("Prix enfant · DZD", "Remise enfant · %"));
        MealFields(String meal, Row rate) {
            this.meal = meal; enabled = new CheckBox(UI.mealName(meal)); enabled.setSelected(rate != null);
            adult = UI.input(rate == null ? "" : UI.amount(rate.number("adult_price")), "Adulte · DZD");
            boolean percent = rate != null && rate.has("child_discount_percent"); mode.setValue(percent ? "Remise enfant · %" : "Prix enfant · DZD");
            child = UI.input(rate == null ? "" : percent ? rate.text("child_discount_percent") : UI.amount(rate.number("child_fixed_price")), "Tarif enfant");
        }
        Node view() { HBox inputs = UI.columns(UI.field("Adulte · DZD", adult), UI.field("Mode enfant", mode), UI.field("Valeur", child)); inputs.disableProperty().bind(enabled.selectedProperty().not()); return UI.box(10, enabled, inputs); }
        HotelStore.MealRate rate() { boolean percentage = mode.getValue().contains("%"); return new HotelStore.MealRate(meal, UI.cents(adult.getText()), percentage ? null : UI.cents(child.getText()), percentage ? UI.integer(child.getText()) : null); }
    }
}
