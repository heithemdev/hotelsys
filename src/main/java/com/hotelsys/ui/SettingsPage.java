package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import com.hotelsys.service.HotelStore;
import java.util.HashSet;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class SettingsPage {
    private final HotelApp app;
    public SettingsPage(HotelApp app){this.app=app;}
    public Node view(){
        VBox page=UI.page("Paramètres","Établissement, restauration et préférences",null);page.setId("page-settings");VBox content=UI.box(24);page.getChildren().add(content);
        app.run(app.store()::hotel,hotel -> {
            TextField name=UI.input(hotel.text("name"),"Nom de l'hôtel",150);ComboBox<Integer> rating=UI.category(HotelStore.stars(hotel.text("category")));
            TextField bed=UI.countInput(hotel.text("bed_sharing_age"),0,18),meal=UI.countInput(hotel.text("meal_child_age"),0,18);
            ToggleButton breakfast=UI.toggle(hotel.flag("breakfast_enabled")),lunch=UI.toggle(hotel.flag("lunch_enabled")),dinner=UI.toggle(hotel.flag("dinner_enabled")),sound=UI.toggle(hotel.flag("payment_sound"));
            Label feedback=UI.label("","error");
            Button save=UI.button("Enregistrer","primary",() -> {
                try{
                    String title=name.getText();int stars=rating.getValue(),bedAge=UI.integer(bed.getText()),mealAge=UI.integer(meal.getText());Set<String> services=new HashSet<>();
                    if(breakfast.isSelected()){services.add("BREAKFAST");}if(lunch.isSelected()){services.add("LUNCH");}if(dinner.isSelected()){services.add("DINNER");}boolean enabled=sound.isSelected();
                    app.run(() -> {app.store().settings(title,stars,bedAge,mealAge,services,enabled);return true;},ok -> {feedback.setText("");app.refreshHotelName();app.toast("Paramètres enregistrés");},feedback::setText);
                }catch(RuntimeException invalid){feedback.setText(invalid.getMessage());}
            });
            VBox establishment=UI.panel(UI.label("Établissement","section-title"),UI.field("Nom",name),UI.field("Classement",rating));
            VBox services=UI.panel(UI.label("Restauration","section-title"),UI.serviceRow("Petit-déjeuner",breakfast),UI.serviceRow("Déjeuner",lunch),UI.serviceRow("Dîner",dinner));
            content.getChildren().addAll(UI.columns(establishment,services),UI.panel(UI.label("Tarifs et capacité enfants","section-title"),UI.columns(UI.field("Partage sans lit avant cet âge",bed),UI.field("Tarif enfant des repas avant cet âge",meal))),UI.panel(UI.label("Préférences","section-title"),UI.serviceRow("Son de confirmation d'un paiement",sound)),feedback,UI.row(12,UI.spacer(),save));
            CheckBox motion=new CheckBox("Réduire les animations");motion.setSelected(app.reducedMotion());motion.selectedProperty().addListener((o,a,b) -> app.reduceMotion(b));content.getChildren().add(motion);
        });return UI.scroll(page);
    }
}
