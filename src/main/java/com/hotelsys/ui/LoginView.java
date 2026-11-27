package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import java.util.Arrays;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class LoginView {
    private final HotelApp app;
    private final boolean setup;
    public LoginView(HotelApp app, boolean setup) { this.app = app; this.setup = setup; }
    public Node view() {
        VBox left = UI.box(24, UI.row(12, app.logo(52), UI.label("HotelSys", "login-brand")));
        left.getStyleClass().add("login-story"); left.setPrefWidth(460); left.setMinWidth(360);
        Region above = new Region(), below = new Region(); VBox.setVgrow(above, Priority.ALWAYS); VBox.setVgrow(below, Priority.ALWAYS);
        Label headline = UI.label("Votre hôtel,\nbien organisé", "login-headline");
        Label story = UI.label("Chambres, séjours et caisse.\nToute la réception, au même endroit.", "login-description");
        left.getChildren().addAll(above, headline, story, below, UI.label("Pensé pour l'accueil, chaque jour", "login-description"));
        TextField hotel = UI.input("", "Nom de votre hôtel"), category = UI.input("Non classé", "Catégorie"), name = UI.input("", "Votre nom"), login = UI.input("", "Identifiant");
        hotel.setId("setupHotel"); name.setId("setupName"); login.setId("loginUsername");
        PasswordField password = new PasswordField(), confirmation = new PasswordField(); password.setId("loginPassword"); confirmation.setId("setupConfirmation");
        password.setPromptText(setup ? "12 caractères minimum" : "Mot de passe");
        Label error = UI.label("", "error"); error.setWrapText(true);
        Button submit = new Button(setup ? "Créer mon espace" : "Se connecter"); submit.setId("loginSubmit"); submit.setDefaultButton(true); submit.getStyleClass().add("primary"); submit.setMaxWidth(Double.MAX_VALUE);
        VBox form = UI.box(16, UI.label(setup ? "Configurer votre hôtel" : "Espace réception", "dialog-title"), UI.muted(setup ? "Créez le premier compte administrateur" : "Retrouvez vos chambres et les séjours à venir"));
        CheckBox demo = new CheckBox("Charger un exemple pour la démonstration"); demo.setId("setupDemo");
        if (setup) { form.getChildren().addAll(UI.field("Nom de l'hôtel", hotel), UI.columns(UI.field("Catégorie", category), UI.field("Votre nom", name))); }
        form.getChildren().addAll(UI.field("Identifiant", login), UI.field("Mot de passe", password));
        if (setup) { form.getChildren().addAll(UI.field("Confirmer le mot de passe", confirmation), demo); }
        form.getChildren().addAll(error, submit, UI.muted("Vos données restent dans le dossier de ce projet"));
        form.setMaxWidth(440); form.setPrefWidth(440);
        submit.setOnAction(event -> {
            error.setText(""); if (setup && !password.getText().equals(confirmation.getText())) { error.setText("Les mots de passe ne correspondent pas"); return; }
            String username = login.getText(), hotelName = hotel.getText(), hotelCategory = category.getText(), display = name.getText(); boolean sample = demo.isSelected();
            char[] secret = password.getText().toCharArray(); password.clear(); confirmation.clear(); submit.setDisable(true);
            app.run(() -> {
                try { return setup ? app.store().setup(hotelName, hotelCategory, display, username, secret, sample) : app.store().login(username, secret); }
                finally { Arrays.fill(secret, '\0'); }
            }, user -> app.showShell(), message -> { submit.setDisable(false); error.setText(message); });
        });
        VBox right = UI.box(0, form); right.setAlignment(Pos.CENTER); right.setPadding(new Insets(40)); right.setMinHeight(660);
        ScrollPane scroll = UI.scroll(right); HBox.setHgrow(scroll, Priority.ALWAYS);
        return UI.row(0, left, scroll);
    }
}
