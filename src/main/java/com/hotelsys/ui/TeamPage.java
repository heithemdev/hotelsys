package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import com.hotelsys.model.Row;
import java.util.Arrays;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class TeamPage {
    private final HotelApp app;
    public TeamPage(HotelApp app) { this.app = app; }
    public Node view() {
        Button add = UI.button("Nouveau compte", "primary", () -> edit(null));
        TableView<Row> table = UI.table(); UI.column(table, "Nom", 220, r -> r.text("display_name")); UI.column(table, "Rôle", 180, r -> r.text("role").equals("ADMIN") ? "Administrateur" : "Réceptionniste"); UI.column(table, "Accès", 140, r -> r.flag("active") ? "Actif" : "Désactivé");
        Button edit = UI.button("Modifier le compte", "secondary", () -> { Row selected = table.getSelectionModel().getSelectedItem(); if (selected != null) { edit(selected); } }); edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        VBox page = UI.page("Équipe", UI.row(8, edit, add)); page.setId("page-team");
        UI.openRows(table, this::edit); page.getChildren().add(table); app.run(app.store()::workers, rows -> table.setItems(FXCollections.observableArrayList(rows))); return page;
    }
    private void edit(Row user) {
        UI.Form form = new UI.Form(app.stage(), user == null ? "Nouveau compte" : "Modifier le compte", "");
        TextField name = UI.input(user == null ? "" : user.text("display_name"), "Nom complet",100);
        String permission = user == null ? "WORKER" : user.text("role");
        Label role = UI.label(permission.equals("ADMIN") ? "Administrateur" : "Réceptionniste", "strong");
        CheckBox active = new CheckBox("Compte actif"); active.setSelected(user == null || user.flag("active")); PasswordField password = new PasswordField(); password.setPromptText(user == null ? "12 caractères minimum" : "Laisser vide pour conserver le mot de passe");
        UI.filter(password,128,"[\\s\\S]*",false);
        form.body.getChildren().addAll(UI.field("Nom", name), UI.field("Rôle", role), active, UI.field(user == null ? "Mot de passe" : "Nouveau mot de passe · facultatif", password));
        if (user != null) { form.body.getChildren().add(UI.muted("Un compte désactivé conserve son historique.")); }
        form.action("Enregistrer le compte", () -> {
            String display = name.getText(); boolean enabled = active.isSelected(); char[] secret = password.getText().toCharArray(); password.clear();
            app.save(form, () -> { try { app.store().saveUser(user == null ? 0 : user.number("id"), display, permission, enabled, secret); return true; } finally { Arrays.fill(secret, '\0'); } }, () -> {
                if (user != null && user.number("id") == app.store().user().id()) { app.store().logout(); app.showLogin(false); } else { app.refresh(); }
            });
        }); form.show();
    }
}
