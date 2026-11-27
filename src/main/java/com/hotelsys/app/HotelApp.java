package com.hotelsys.app;

import com.hotelsys.repository.Db;
import com.hotelsys.service.HotelStore;
import com.hotelsys.ui.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.animation.*;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

public final class HotelApp extends Application {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "hotelsys-data"); t.setDaemon(true); return t; });
    private final Map<String, Button> nav = new LinkedHashMap<>();
    private final StackPane content = new StackPane();
    private StackPane root;
    private Stage stage;
    private HotelStore store;
    private String current = "dashboard";
    private Label hotelLabel;
    private boolean reduceMotion;

    public HotelStore store() { return store; }
    public Stage stage() { return stage; }
    @Override public void start(Stage primaryStage) {
        stage = primaryStage;
        Font.loadFont(getClass().getResourceAsStream("/fonts/Manrope.ttf"), 14);
        Font.loadFont(getClass().getResourceAsStream("/fonts/DMSerifDisplay.ttf"), 32);
        Db db = new Db(Path.of(System.getProperty("hotelsys.dataDir", "data"), "hotelsys.db")); store = new HotelStore(db);
        reduceMotion = Boolean.getBoolean("hotelsys.reduceMotion");
        root = new StackPane(UI.muted("Préparation de HotelSys…"));
        Scene scene = new Scene(root, 1440, 900); scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        stage.setTitle("HotelSys · Réception & séjours"); stage.getIcons().add(new Image(getClass().getResourceAsStream("/images/logo.png")));
        stage.setScene(scene); stage.setMinWidth(1120); stage.setMinHeight(720);
        var bounds = javafx.stage.Screen.getPrimary().getVisualBounds(); stage.setWidth(Math.min(1440, bounds.getWidth() - 32)); stage.setHeight(Math.min(940, bounds.getHeight() - 32)); stage.show();
        run(() -> { db.initialize(); return store.needsSetup(); }, this::showLogin);
    }
    public void showLogin(boolean setup) { root.getChildren().setAll(new LoginView(this, setup).view()); }
    public ImageView logo(double height) {
        ImageView mark = new ImageView(new Image(getClass().getResourceAsStream("/images/logo.png")));
        mark.setViewport(new javafx.geometry.Rectangle2D(285, 155, 680, 920)); mark.setFitHeight(height); mark.setPreserveRatio(true); return mark;
    }
    public void showShell() {
        nav.clear(); VBox sidebar = new VBox(4); sidebar.getStyleClass().add("sidebar"); sidebar.setPrefWidth(232); sidebar.setMinWidth(232);
        HBox brand = UI.row(10, logo(40), UI.label("HotelSys", "brand")); brand.setPadding(new Insets(8, 8, 28, 8)); sidebar.getChildren().add(brand);
        addNav(sidebar, "dashboard", "Tableau de bord", "home"); addNav(sidebar, "reservations", "Réservations", "book"); addNav(sidebar, "calendar", "Calendrier", "calendar");
        addNav(sidebar, "people", "Clients", "people"); addNav(sidebar, "rooms", "Chambres", "bed"); addNav(sidebar, "cash", "Caisse", "cash");
        if (store.user().admin()) { Separator line = new Separator(); VBox.setMargin(line, new Insets(20, 4, 16, 4)); sidebar.getChildren().add(line); addNav(sidebar, "team", "Équipe", "people"); addNav(sidebar, "settings", "Paramètres", "settings"); }
        Region stretch = new Region(); VBox.setVgrow(stretch, Priority.ALWAYS); sidebar.getChildren().add(stretch);
        hotelLabel = UI.label("", "small"); sidebar.getChildren().add(hotelLabel);
        String initials = store.user().name().isBlank() ? "H" : store.user().name().substring(0, 1).toUpperCase(java.util.Locale.FRENCH);
        Label avatar = UI.label(initials, "avatar");
        sidebar.getChildren().add(UI.row(12, avatar, UI.box(4, UI.label(store.user().name(), "strong"), UI.label(store.user().admin() ? "Administrateur" : "Réceptionniste", "small"))));
        Button logout = UI.button("Déconnexion", "nav", () -> { store.logout(); showLogin(false); }); logout.setGraphic(icon("logout")); sidebar.getChildren().add(logout);
        BorderPane shell = new BorderPane(); shell.setLeft(sidebar); shell.setCenter(content); root.getChildren().setAll(shell); refreshHotelName(); navigate(current);
    }
    public void refreshHotelName() { run(store::hotel, row -> { if (hotelLabel != null) { hotelLabel.setText(row.text("name")); } stage.setTitle("HotelSys · " + row.text("name")); }); }
    private void addNav(VBox parent, String key, String text, String icon) {
        Button button = UI.button(text, "nav", () -> navigate(key)); button.setId("nav-" + key); button.setGraphic(icon(icon)); button.setMaxWidth(Double.MAX_VALUE); nav.put(key, button); parent.getChildren().add(button);
    }
    private Node icon(String name) { return Icons.get(name); }
    public void navigate(String name) {
        current = name; nav.forEach((key, button) -> { button.getStyleClass().remove("nav-active"); if (key.equals(name)) { button.getStyleClass().add("nav-active"); } });
        Node page = switch (name) {
            case "reservations" -> new ReservationsPage(this).view(); case "calendar" -> new CalendarPage(this).view();
            case "people" -> new PeoplePage(this).view(); case "rooms" -> new RoomsPage(this).view(); case "cash" -> new CashPage(this).view();
            case "team" -> new TeamPage(this).view(); case "settings" -> new SettingsPage(this).view(); default -> new DashboardPage(this).view();
        };
        content.getChildren().setAll(page); animate(page);
    }
    public void refresh() { navigate(current); }
    public void reduceMotion(boolean value) { reduceMotion = value; }
    public boolean reducedMotion() { return reduceMotion; }
    private void animate(Node node) {
        if (reduceMotion) { return; }
        FadeTransition fade = new FadeTransition(Duration.millis(220), node); fade.setFromValue(0); fade.setToValue(1);
        TranslateTransition rise = new TranslateTransition(Duration.millis(220), node); rise.setFromY(8); rise.setToY(0);
        rise.setInterpolator(Interpolator.SPLINE(.22, 1, .36, 1)); new ParallelTransition(fade, rise).play();
    }
    public <T> void run(Callable<T> action, Consumer<T> success) { run(action, success, this::toast); }
    public <T> void run(Callable<T> action, Consumer<T> success, Consumer<String> error) {
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return action.call(); } };
        task.setOnSucceeded(event -> { try { success.accept(task.getValue()); } catch (RuntimeException failure) { log(failure); error.accept(message(failure)); } });
        task.setOnFailed(event -> { log(task.getException()); error.accept(message(task.getException())); }); worker.execute(task);
    }
    public void save(UI.Form form, Callable<?> action, Runnable success) {
        form.busy(true); run(action, ignored -> { form.stage.close(); success.run(); }, form::fail);
    }
    public void toast(String message) {
        Label toast = UI.label(message, "toast"); toast.setWrapText(true); toast.setMaxWidth(600);
        StackPane.setAlignment(toast, Pos.BOTTOM_RIGHT); StackPane.setMargin(toast, new Insets(24)); root.getChildren().add(toast);
        PauseTransition wait = new PauseTransition(Duration.seconds(5)); wait.setOnFinished(event -> root.getChildren().remove(toast)); wait.play();
    }
    private void log(Throwable failure) {
        if (failure instanceof IllegalArgumentException) { return; }
        try { Files.writeString(store.database().file().getParent().resolve("hotelsys.log"), java.time.Instant.now() + " " + failure + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (java.io.IOException ignored) { /* Le message utilisateur reste disponible si le journal ne peut pas être écrit. */ }
    }
    public static String message(Throwable failure) {
        String text = failure.getMessage() == null ? "Opération impossible" : failure.getMessage();
        if (failure instanceof IllegalArgumentException) { return text; }
        if (text.contains("family_check_required")) { return "Vérifiez les documents familiaux avant l'arrivée de cette chambre mixte"; }
        if (text.contains("last_active_admin")) { return "Le dernier administrateur actif doit être conservé"; }
        if (text.contains("room_overlap")) { return "La chambre est déjà réservée sur ces dates"; }
        if (text.contains("guest_overlap")) { return "Un occupant a déjà un séjour sur ces dates"; }
        if (text.contains("UNIQUE constraint")) { return "Cet identifiant, numéro ou nom existe déjà"; }
        if (text.contains("bed_capacity") || text.contains("sharing_capacity")) { return "La capacité de la chambre est dépassée"; }
        if (text.contains("locked") || text.contains("BUSY")) { return "Les données sont occupées ; réessayez dans un instant"; }
        return "L'opération n'a pas pu être enregistrée. Vérifiez les données et réessayez";
    }
    @Override public void stop() { worker.shutdownNow(); }
}
