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
    private Node header(){
        HBox header=UI.row(12,app.logo(32),UI.label("HotelSys","brand"));header.getStyleClass().add("access-header");return header;
    }
    private PasswordField password(String id){PasswordField field=new PasswordField();field.setId(id);UI.filter(field,128,"[\\s\\S]*",false);return field;}
    private Node setup(){
        TextField hotel=UI.input("","Nom de l'hôtel",150),name=UI.input("","Nom complet",100);hotel.setId("setupHotel");name.setId("setupName");
        ComboBox<Integer> rating=UI.category(0);rating.setId("setupCategory");
        ToggleButton breakfast=UI.toggle(true),lunch=UI.toggle(false),dinner=UI.toggle(false);breakfast.setId("setupBreakfast");lunch.setId("setupLunch");dinner.setId("setupDinner");
        PasswordField password=password("loginPassword"),confirmation=password("setupConfirmation");
        VBox establishment=UI.panel(UI.label("Établissement","section-title"),UI.field("Nom de l'hôtel",hotel),UI.field("Classement",rating),new Separator(),UI.label("Services de restauration","section-title"),UI.serviceRow("Petit-déjeuner",breakfast),UI.serviceRow("Déjeuner",lunch),UI.serviceRow("Dîner",dinner));
        VBox account=UI.panel(UI.label("Compte administrateur","section-title"),UI.field("Nom complet",name),UI.field("Mot de passe · 12 caractères minimum",password),UI.field("Confirmer le mot de passe",confirmation));
        establishment.setSpacing(12);account.setSpacing(12);
        Label error=UI.label("","error");error.setWrapText(true); error.managedProperty().bind(error.textProperty().isNotEmpty()); error.visibleProperty().bind(error.managedProperty()); Button submit=new Button("Créer l’établissement");submit.setId("loginSubmit");submit.getStyleClass().add("primary");submit.setDefaultButton(true); Motion.press(submit);
        submit.setOnAction(event -> {
            error.setText("");if(!password.getText().equals(confirmation.getText())){error.setText("Les mots de passe ne correspondent pas");return;}
            String title=hotel.getText(),display=name.getText();int stars=rating.getValue();Set<String> services=new HashSet<>();
            if(breakfast.isSelected()){services.add("BREAKFAST");}if(lunch.isSelected()){services.add("LUNCH");}if(dinner.isSelected()){services.add("DINNER");}
            char[] secret=password.getText().toCharArray();submit.setDisable(true);
            app.run(() -> {try{return app.store().setup(title,stars,display,secret,services);}finally{Arrays.fill(secret,'\0');}},user -> {password.clear();confirmation.clear();app.showShell();},message -> {submit.setDisable(false);error.setText(message);});
        });
        HBox groups=UI.columns(establishment,account); groups.setAlignment(Pos.TOP_LEFT); groups.setFillHeight(false);
        VBox content=UI.box(24,UI.label("Configurer l'établissement","page-title"),groups,error,UI.row(12,UI.spacer(),submit));content.setMaxWidth(1040);content.setPadding(new Insets(40,32,32,32));
        VBox center=UI.box(0,content);center.setAlignment(Pos.TOP_CENTER);BorderPane page=new BorderPane();page.setTop(header());page.setCenter(UI.scroll(center));page.setId("setupPage");return page;
    }
    private Node login(){
        Label hotel=UI.label("","login-hotel"); hotel.setWrapText(true);
        TextField adminName=UI.input("","Nom complet",100);adminName.setId("loginName");
        HBox nameInput=UI.row(8,Icons.get("person"),adminName);nameInput.getStyleClass().add("login-input");adminName.setAccessibleText("Nom complet");HBox.setHgrow(adminName,Priority.ALWAYS);
        ComboBox<Row> accounts=UI.choose(List.of());accounts.setId("loginAccount"); accounts.setAccessibleText("Compte réceptionniste");
        ComboBox<String> role=new ComboBox<>(FXCollections.observableArrayList("Administrateur","Réceptionniste"));role.getSelectionModel().selectFirst();role.setMaxWidth(Double.MAX_VALUE);
        PasswordField password=password("loginPassword"); password.setAccessibleText("Mot de passe");
        TextField visiblePassword=UI.input("","",128);visiblePassword.setId("loginPasswordVisible");visiblePassword.setAccessibleText("Mot de passe");visiblePassword.textProperty().bindBidirectional(password.textProperty());visiblePassword.setVisible(false);visiblePassword.setManaged(false);
        StackPane passwordFields=new StackPane(password,visiblePassword);HBox.setHgrow(passwordFields,Priority.ALWAYS);
        ToggleButton reveal=new ToggleButton();reveal.setId("loginReveal");reveal.setGraphic(Icons.get("eye"));reveal.getStyleClass().add("icon-button");reveal.setAccessibleText("Afficher le mot de passe");
        reveal.selectedProperty().addListener((o,a,b) -> {
            password.setVisible(!b);password.setManaged(!b);visiblePassword.setVisible(b);visiblePassword.setManaged(b);
            reveal.setGraphic(Icons.get(b?"eye-off":"eye"));reveal.setAccessibleText(b?"Masquer le mot de passe":"Afficher le mot de passe");
            if(b){visiblePassword.requestFocus();visiblePassword.positionCaret(visiblePassword.getLength());}else{password.requestFocus();password.positionCaret(password.getLength());}
        });
        HBox passwordInput=UI.row(8,Icons.get("lock"),passwordFields,reveal);passwordInput.getStyleClass().add("login-input");
        for(TextField field:List.of(adminName,password,visiblePassword)){
            field.focusedProperty().addListener((o,a,b) -> {nameInput.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"),adminName.isFocused());passwordInput.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"),password.isFocused()||visiblePassword.isFocused());});
        }
        Label error=UI.label("","error");error.setWrapText(true);error.managedProperty().bind(error.textProperty().isNotEmpty());error.visibleProperty().bind(error.managedProperty());
        Button submit=UI.button("Se connecter","primary",() -> { });submit.setId("loginSubmit");submit.setDefaultButton(true);submit.setMaxWidth(Double.MAX_VALUE);
        VBox name=UI.field("Votre nom",nameInput);
        Runnable load=() -> {
            boolean admin=role.getSelectionModel().getSelectedIndex()==0;password.clear();error.setText("");
            name.getChildren().set(1,admin?nameInput:accounts);reveal.setSelected(false);submit.setDisable(false);
            if(!admin){submit.setDisable(true);app.run(() -> app.store().loginAccounts("WORKER"),rows -> {if (role.getSelectionModel().getSelectedIndex() == 0) { return; } accounts.setItems(FXCollections.observableArrayList(rows));if(!rows.isEmpty()){accounts.getSelectionModel().selectFirst();submit.setDisable(false);}else{error.setText("Aucun réceptionniste actif. L'administrateur peut créer un compte dans Équipe.");}});}
        };
        role.valueProperty().addListener((o,a,b) -> load.run());
        submit.setOnAction(event -> {
            String permission=role.getSelectionModel().getSelectedIndex()==0?"ADMIN":"WORKER";
            String display=permission.equals("ADMIN")?adminName.getText():accounts.getValue()==null?"":accounts.getValue().text("label");
            char[] secret=password.getText().toCharArray();submit.setDisable(true);error.setText("");
            app.run(() -> {try{return app.store().login(display,permission,secret);}finally{Arrays.fill(secret,'\0');}},user -> {password.clear();app.showShell();},message -> {submit.setDisable(false);password.clear();error.setText(message);});
        });
        HBox stars=UI.row(0); stars.getStyleClass().add("hotel-rating");
        VBox identity=UI.box(4,hotel,stars);
        HBox heading=UI.row(16,app.logo(48),identity);
        role.setId("loginRole"); role.setAccessibleText("Rôle");
        VBox card=UI.box(20,heading,new Separator(),UI.label("Connexion","login-title"),role,name,UI.field("Mot de passe",passwordInput),error,submit);
        card.getStyleClass().add("login-card");card.setMaxWidth(432);card.setPrefWidth(432);
        VBox middle=UI.box(0,card);middle.setAlignment(Pos.CENTER);middle.setPadding(new Insets(24));
        ScrollPane access=UI.scroll(middle);access.setFitToHeight(true);
        BorderPane page=new BorderPane();page.getStyleClass().add("login-page");page.setTop(header());page.setCenter(access);
        app.run(app.store()::hotel,row -> {
            hotel.setText(row.text("name"));int rating=HotelStore.stars(row.text("category")); stars.setAccessibleText(HotelStore.category(rating));
            if(rating==0){stars.getChildren().setAll(UI.label("Non classé","small"));}else{for(int i=0;i<rating;i++){stars.getChildren().add(Icons.get("star"));}}
        });load.run();Motion.enter(card);return page;
    }
}
