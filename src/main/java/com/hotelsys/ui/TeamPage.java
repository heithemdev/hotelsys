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
        VBox page = UI.page("L'équipe", "Un compte personnel pour chaque membre de la réception", UI.button("+ Nouveau compte", "primary", () -> edit(null))); page.setId("page-team");
        TableView<Row> table = UI.table(); UI.column(table, "Nom", 220, r -> r.text("display_name")); UI.column(table, "Rôle", 180, r -> r.text("role").equals("ADMIN") ? "Administrateur" : "Employé"); UI.column(table, "Accès", 140, r -> r.flag("active") ? "Actif" : "Désactivé");
        Button edit = UI.button("Modifier le compte", "secondary", () -> { Row selected = table.getSelectionModel().getSelectedItem(); if (selected != null) { edit(selected); } }); edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        UI.openRows(table, this::edit); page.getChildren().addAll(UI.row(12, UI.muted("La désactivation conserve l'historique de l'employé"), UI.spacer(), edit), table); app.run(app.store()::workers, rows -> table.setItems(FXCollections.observableArrayList(rows))); return page;
    }
    private void edit(Row user) {
        UI.Form form = new UI.Form(app.stage(), user == null ? "Nouveau compte" : "Modifier le compte", "Les droits sont vérifiés pour chaque opération");
        TextField name = UI.input(user == null ? "" : user.text("display_name"), "Nom complet",100);
        ComboBox<String> role = new ComboBox<>(FXCollections.observableArrayList("Réceptionniste", "Administrateur")); role.setValue(user != null && user.text("role").equals("ADMIN") ? "Administrateur" : "Réceptionniste");
        CheckBox active = new CheckBox("Compte actif"); active.setSelected(user == null || user.flag("active")); PasswordField password = new PasswordField(); password.setPromptText(user == null ? "12 caractères minimum" : "Laisser vide pour conserver le mot de passe");
        UI.filter(password,128,"[\\s\\S]*",false);
        form.body.getChildren().addAll(UI.field("Nom", name), UI.field("Rôle", role), active, UI.field(user == null ? "Mot de passe" : "Nouveau mot de passe · facultatif", password));
        form.action("Enregistrer le compte", () -> {
            String display = name.getText(), permission = role.getValue().equals("Administrateur") ? "ADMIN" : "WORKER"; boolean enabled = active.isSelected(); char[] secret = password.getText().toCharArray(); password.clear();
            app.save(form, () -> { try { app.store().saveUser(user == null ? 0 : user.number("id"), display, permission, enabled, secret); return true; } finally { Arrays.fill(secret, '\0'); } }, () -> {
                if (user != null && user.number("id") == app.store().user().id()) { app.store().logout(); app.showLogin(false); } else { app.refresh(); }
            });
        }); form.show();
    }
}
