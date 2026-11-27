package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import com.hotelsys.service.HotelStore;
import java.util.HashSet;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class SettingsPage {
    private record Data(com.hotelsys.model.Row hotel,java.util.List<com.hotelsys.model.Row> rates) { }
    private final HotelApp app;
    public SettingsPage(HotelApp app){this.app=app;}
    public Node view(){
        VBox page=UI.page("Paramètres",null);page.setId("page-settings");VBox content=UI.box(24);page.getChildren().add(content);
        app.run(() -> new Data(app.store().hotel(),app.store().mealRates()),data -> {
            var hotel=data.hotel();java.util.Map<String,TextField> prices=new java.util.LinkedHashMap<>();for(var rate:data.rates()){prices.put(rate.text("meal"),UI.moneyInput(UI.amount(rate.number("adult_price")),"0,00"));}
            TextField name=UI.input(hotel.text("name"),"Nom de l'hôtel",150);ComboBox<Integer> rating=UI.category(HotelStore.stars(hotel.text("category")));
            TextField bed=UI.countInput(hotel.text("bed_sharing_age"),0,18);
            ToggleButton breakfast=UI.toggle(hotel.flag("breakfast_enabled")),lunch=UI.toggle(hotel.flag("lunch_enabled")),dinner=UI.toggle(hotel.flag("dinner_enabled")),sound=UI.toggle(hotel.flag("payment_sound"));
            Label feedback=UI.label("","error"); feedback.setWrapText(true);
            feedback.managedProperty().bind(feedback.textProperty().isNotEmpty()); feedback.visibleProperty().bind(feedback.managedProperty());
            Button save=UI.button("Enregistrer","primary",() -> {
                try{
                    String title=name.getText();int stars=rating.getValue(),bedAge=UI.integer(bed.getText());Set<String> services=new HashSet<>();
                    if(breakfast.isSelected()){services.add("BREAKFAST");}if(lunch.isSelected()){services.add("LUNCH");}if(dinner.isSelected()){services.add("DINNER");}boolean enabled=sound.isSelected();java.util.Map<String,Long> amounts=new java.util.HashMap<>();prices.forEach((key,input) -> amounts.put(key,UI.cents(input.getText())));
                    app.run(() -> {app.store().settings(title,stars,bedAge,services,enabled,amounts);return true;},ok -> {feedback.setText("");app.paymentSoundEnabled(enabled);app.refreshHotelName();app.toast("Paramètres enregistrés");},feedback::setText);
                }catch(RuntimeException invalid){feedback.setText(invalid.getMessage());}
            });
            VBox establishment=UI.panel(UI.label("Établissement","section-title"),UI.field("Nom",name),UI.field("Classement",rating));
            VBox services = UI.panel(UI.label("Restauration", "section-title"), UI.muted("Tarif par personne et par nuit · DZD"));
            for (var entry : java.util.List.of(java.util.Map.entry("BREAKFAST", breakfast), java.util.Map.entry("LUNCH", lunch), java.util.Map.entry("DINNER", dinner))) {
                String meal = UI.mealName(entry.getKey());
                TextField price = prices.get(entry.getKey());
                price.setPrefWidth(96); price.setMaxWidth(96); price.setAccessibleText(meal + " · prix en DZD");
                entry.getValue().setAccessibleText(meal);
                services.getChildren().add(UI.row(8, UI.label(meal, "field-label"), UI.spacer(), price, entry.getValue()));
            }
            ToggleButton motion = UI.toggle(app.reducedMotion());
            motion.selectedProperty().addListener((o,a,b) -> app.reduceMotion(b));
            VBox preferences = UI.panel(UI.label("Préférences", "section-title"),
                    UI.row(8, UI.label("Son à l'encaissement", "field-label"), UI.spacer(),
                            UI.button("Écouter", "secondary", () -> { feedback.setText(""); app.previewPaymentSound(feedback::setText); }),
                            sound), UI.serviceRow("Réduire les animations", motion));
            sound.setAccessibleText("Son à l'encaissement");
            establishment.getChildren().add(UI.field("Partage sans lit avant cet âge (ans)", bed));
            HBox groups = UI.columns(establishment, UI.box(16, services, preferences));
            groups.setAlignment(javafx.geometry.Pos.TOP_LEFT); groups.setFillHeight(false);
            content.getChildren().addAll(groups, feedback, UI.row(12, UI.spacer(), save));
        });return UI.scroll(page);
    }
}
