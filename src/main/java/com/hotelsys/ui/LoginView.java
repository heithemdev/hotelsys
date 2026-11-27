package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import com.hotelsys.model.Row;
import com.hotelsys.service.HotelStore;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class LoginView {
    private final HotelApp app;
    private final boolean setup;
    public LoginView(HotelApp app,boolean setup){this.app=app;this.setup=setup;}
    public Node view(){return setup?setup():login();}
    private Node header(String context){
        HBox header=UI.row(12,app.logo(36),UI.label("HotelSys","brand"),UI.spacer(),UI.muted(context));header.getStyleClass().add("access-header");return header;
    }
    private PasswordField password(String id){PasswordField field=new PasswordField();field.setId(id);UI.filter(field,128,"[\\s\\S]*",false);return field;}
    private Node setup(){
        TextField hotel=UI.input("","Nom de l'hôtel",150),name=UI.input("","Nom complet",100);hotel.setId("setupHotel");name.setId("setupName");
        ComboBox<Integer> rating=UI.category(0);rating.setId("setupCategory");
        ToggleButton breakfast=UI.toggle(true),lunch=UI.toggle(false),dinner=UI.toggle(false);breakfast.setId("setupBreakfast");lunch.setId("setupLunch");dinner.setId("setupDinner");
        PasswordField password=password("loginPassword"),confirmation=password("setupConfirmation");
        VBox establishment=UI.panel(UI.label("Établissement","section-title"),UI.field("Nom de l'hôtel",hotel),UI.field("Classement",rating),new Separator(),UI.label("Services de restauration","section-title"),UI.serviceRow("Petit-déjeuner",breakfast),UI.serviceRow("Déjeuner",lunch),UI.serviceRow("Dîner",dinner));
        VBox account=UI.panel(UI.label("Compte administrateur","section-title"),UI.field("Nom complet",name),UI.field("Mot de passe",password),UI.muted("12 caractères minimum"),UI.field("Confirmer le mot de passe",confirmation));
        establishment.setSpacing(12);account.setSpacing(12);
        Label error=UI.label("","error");error.setWrapText(true);Button submit=new Button("Terminer la configuration");submit.setId("loginSubmit");submit.getStyleClass().add("primary");submit.setDefaultButton(true);
        submit.setOnAction(event -> {
            error.setText("");if(!password.getText().equals(confirmation.getText())){error.setText("Les mots de passe ne correspondent pas");return;}
            String title=hotel.getText(),display=name.getText();int stars=rating.getValue();Set<String> services=new HashSet<>();
            if(breakfast.isSelected()){services.add("BREAKFAST");}if(lunch.isSelected()){services.add("LUNCH");}if(dinner.isSelected()){services.add("DINNER");}
            char[] secret=password.getText().toCharArray();submit.setDisable(true);
            app.run(() -> {try{return app.store().setup(title,stars,display,secret,services);}finally{Arrays.fill(secret,'\0');}},user -> {password.clear();confirmation.clear();app.showShell();},message -> {submit.setDisable(false);error.setText(message);});
        });
        VBox content=UI.box(20,UI.label("Configurer l'établissement","page-title"),UI.columns(establishment,account),error,UI.row(12,UI.spacer(),submit));content.setMaxWidth(1040);content.setPadding(new Insets(24));
        VBox center=UI.box(0,content);center.setAlignment(Pos.TOP_CENTER);BorderPane page=new BorderPane();page.setTop(header("Configuration initiale"));page.setCenter(UI.scroll(center));page.setId("setupPage");return page;
    }
    private Node login(){
        Label hotel=UI.label("","dialog-title"),category=UI.muted("");ComboBox<Row> accounts=UI.choose(List.of());accounts.setId("loginAccount");
        PasswordField password=password("loginPassword");password.setPromptText("Mot de passe");
        ToggleGroup roles=new ToggleGroup();ToggleButton admin=new ToggleButton("Administrateur"),worker=new ToggleButton("Employé");admin.setId("loginAdmin");worker.setId("loginWorker");
        admin.setToggleGroup(roles);worker.setToggleGroup(roles);admin.setUserData("ADMIN");worker.setUserData("WORKER");admin.setSelected(true);admin.getStyleClass().add("role-choice");worker.getStyleClass().add("role-choice");admin.setMaxWidth(Double.MAX_VALUE);worker.setMaxWidth(Double.MAX_VALUE);
        Label error=UI.label("","error");error.setWrapText(true);Button submit=new Button("Se connecter");submit.setId("loginSubmit");submit.getStyleClass().add("primary");submit.setMaxWidth(Double.MAX_VALUE);submit.setDefaultButton(true);
        Runnable load=() -> {
            if(roles.getSelectedToggle()==null){admin.setSelected(true);return;}String role=roles.getSelectedToggle().getUserData().toString();password.clear();error.setText("");accounts.getItems().clear();submit.setDisable(true);
            app.run(() -> app.store().loginAccounts(role),rows -> {
                if(roles.getSelectedToggle()!=null && roles.getSelectedToggle().getUserData().equals(role)){
                    accounts.setItems(FXCollections.observableArrayList(rows));if(!rows.isEmpty()){accounts.getSelectionModel().selectFirst();submit.setDisable(false);}else{error.setText("Aucun compte actif pour cet espace");}
                }
            },error::setText);
        };
        roles.selectedToggleProperty().addListener((o,a,b) -> {if(b==null && a!=null){a.setSelected(true);}else{load.run();}});accounts.valueProperty().addListener((o,a,b) -> password.clear());
        submit.setOnAction(event -> {
            if(accounts.getValue()==null){error.setText("Sélectionnez votre nom");return;}
            long id=accounts.getValue().number("id");String role=roles.getSelectedToggle().getUserData().toString();char[] secret=password.getText().toCharArray();submit.setDisable(true);error.setText("");
            app.run(() -> {try{return app.store().login(id,role,secret);}finally{Arrays.fill(secret,'\0');}},user -> {password.clear();app.showShell();},message -> {submit.setDisable(false);password.clear();error.setText(message);});
        });
        VBox card=UI.panel(hotel,category,new Separator(),UI.label("Connexion","section-title"),UI.columns(admin,worker),UI.field("Votre nom",accounts),UI.field("Mot de passe",password),error,submit);card.setMaxWidth(480);card.setPrefWidth(480);
        VBox middle=UI.box(24,card);middle.setAlignment(Pos.CENTER);middle.setPadding(new Insets(40));BorderPane page=new BorderPane();page.setTop(header("Réception"));page.setCenter(middle);
        app.run(app.store()::hotel,row -> {hotel.setText(row.text("name"));category.setText(row.text("category"));});load.run();return page;
    }
}
