package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class SettingsPage {
    private final HotelApp app;
    public SettingsPage(HotelApp app) { this.app = app; }
    public Node view() {
        VBox page = UI.page("Les paramètres", "Des règles simples, adaptées à votre hôtel", null); page.setId("page-settings");
        VBox content = UI.box(24); page.getChildren().add(content);
        app.run(app.store()::hotel, hotel -> {
            TextField name = UI.input(hotel.text("name"), "Nom de l'hôtel"), category = UI.input(hotel.text("category"), "Catégorie");
            TextField bed = UI.input(hotel.text("bed_sharing_age"), "Âge"), meal = UI.input(hotel.text("meal_child_age"), "Âge"); Label feedback = UI.label("", "error");
            Button save = UI.button("Enregistrer les paramètres", "primary", () -> {
                try {
                    String title = name.getText(), classification = category.getText(); int bedAge = UI.integer(bed.getText()), mealAge = UI.integer(meal.getText());
                    app.run(() -> { app.store().settings(title, classification, bedAge, mealAge); return true; }, ok -> { feedback.setText(""); app.refreshHotelName(); app.toast("Paramètres enregistrés"); }, feedback::setText);
                } catch (RuntimeException failure) { feedback.setText(failure.getMessage()); }
            });
            content.getChildren().addAll(UI.panel(UI.label("Votre établissement", "section-title"), UI.columns(UI.field("Nom de l'hôtel", name), UI.field("Catégorie", category))),
                    UI.panel(UI.label("Les enfants pendant le séjour", "section-title"), UI.columns(UI.field("Sans lit avant cet âge", bed), UI.field("Tarif enfant des repas avant cet âge", meal)), UI.muted("Seuil strict : avec 6 ans, un enfant de 5 ans partage sans lit et un enfant de 6 ans nécessite un lit. La limite par chambre reste définie dans son modèle."), UI.muted("Les changements s'appliquent aux nouveaux devis ; les réservations existantes conservent leurs conditions.")),
                    feedback, save);
            CheckBox motion = new CheckBox("Réduire les animations pour cette session"); motion.setSelected(app.reducedMotion()); motion.selectedProperty().addListener((o,a,b) -> app.reduceMotion(b));
            content.getChildren().add(UI.panel(UI.label("Confort d'utilisation", "section-title"), motion));
            content.getChildren().add(UI.panel(UI.label("Démonstration du mini-projet", "section-title"), UI.muted("Sur une base vide, ajoutez huit chambres et des séjours d'exemple pour présenter le calendrier. Aucune donnée existante n'est remplacée."), UI.button("Charger un exemple", "secondary", () -> UI.confirm("Ajouter les données de démonstration à cette base vide ?", () -> app.run(() -> { app.store().loadDemo(); return true; }, ok -> { app.refresh(); app.toast("Exemple chargé"); })))));
            content.getChildren().add(UI.muted("Données : " + app.store().database().file() + "\nPour sauvegarder : fermez l'application et copiez le fichier hotelsys.db."));
        }); return UI.scroll(page);
    }
}
