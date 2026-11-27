package com.hotelsys;

import com.hotelsys.app.HotelApp;
import com.hotelsys.model.Row;
import com.hotelsys.ui.BookingEditor;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Un passage graphique facultatif sur données synthétiques, sans compte réel. */
public final class UiCheck {
    private static HotelApp app;
    private static final Path OUT = Path.of("target", "ui-check");
    private UiCheck() { }
    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT); System.setProperty("hotelsys.dataDir", Files.createTempDirectory(OUT,"database-").toString());
        List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> { errors.add(error); error.printStackTrace(); });
        Platform.startup(() -> { });
        try {
            fx(() -> { app = new HotelApp(); app.start(new Stage()); return null; });
            await(() -> find("#loginSubmit") != null);
            fx(() -> {
                text("#setupHotel", "Hôtel Atlas"); text("#setupName", "Nadia Benali");
                @SuppressWarnings("unchecked") ComboBox<Integer> category=(ComboBox<Integer>)find("#setupCategory");category.setValue(4);
                ((ToggleButton)find("#setupDinner")).setSelected(true);return null;
            });
            snapshot(app.stage(),"setup-business.png");
            fx(() -> {text("#loginPassword", "Reception-demo-2026");text("#setupConfirmation", "Reception-demo-2026");button("#loginSubmit").fire();return null;});
            await(() -> find("#page-dashboard") != null && find(".chart") != null);
            com.hotelsys.service.DemoData.seed(app.store());fx(() -> {app.refresh();return null;});await(() -> find(".chart")!=null);
            Thread.sleep(350); snapshot(app.stage(), "dashboard.png");
            fx(() -> {com.hotelsys.ui.PeoplePage.edit(app,null,app::refresh);return null;});await(() -> find("#guestDocument")!=null);
            fx(() -> {
                text("#guestFirst","Youssef");text("#guestLast","Karim");((DatePicker)find("#guestBirth")).setValue(java.time.LocalDate.of(1990,1,1));
                TextField document=(TextField)find("#guestDocument");document.setText("ABC");HotelStoreAssert.equal(0,document.getLength());document.setText("00012345");
                for(Window window:Window.getWindows()){if(window.getScene()!=null){for(Node node:window.getScene().getRoot().lookupAll(".button")){if(node instanceof Button b && b.getText().equals("Enregistrer le client")){b.fire();return null;}}}}
                throw new AssertionError("Bouton client absent");
            });await(() -> find("#guestDocument")==null);
            if(!app.store().guests().stream().anyMatch(r -> r.text("identity_number").equals("00012345"))){throw new AssertionError("Identité non conservée");}
            fx(() -> { app.navigate("calendar"); return null; }); await(() -> find(".stay-bar") != null);
            fx(() -> { BookingEditor.create(app, 0, app.store().today().plusDays(10)); return null; }); await(() -> find("#bookingHolder") != null);
            fx(() -> {
                combo("#bookingHolder").getSelectionModel().selectFirst(); combo("#bookingRoom").getSelectionModel().selectFirst();
                MenuButton people = (MenuButton) find("#bookingGuests"); ((CheckBox) ((CustomMenuItem) people.getItems().getFirst()).getContent()).setSelected(true);
                button("#quoteBooking").fire(); return null;
            });
            await(() -> !button("#saveBooking").isDisabled());
            fx(() -> { HotelStoreAssert.equal(7, app.store().bookings().size() + 1); return null; });
            fx(() -> { button("#saveBooking").fire(); return null; }); await(() -> find("#saveBooking") == null);
            fx(() -> { HotelStoreAssert.equal(7,app.store().bookings().size()); return null; });
            for (String page : List.of("people","rooms","cash","team","settings","reservations")) {
                fx(() -> { app.navigate(page); return null; }); await(() -> find("#page-" + page) != null); Thread.sleep(150); fx(() -> { app.stage().getScene().getRoot().applyCss(); app.stage().getScene().getRoot().layout(); return null; });
            }
            fx(() -> { app.store().logout(); app.showLogin(false); return null; }); await(() -> find("#loginSubmit") != null);
            await(() -> !button("#loginSubmit").isDisabled());snapshot(app.stage(),"login-business.png");
            fx(() -> { text("#loginPassword","incorrect"); button("#loginSubmit").fire(); return null; });
            await(() -> !button("#loginSubmit").isDisabled());
            fx(() -> { text("#loginPassword","Reception-demo-2026"); button("#loginSubmit").fire(); return null; }); await(() -> find("#page-reservations") != null);
            if (!errors.isEmpty()) { throw new AssertionError("Erreur sur le thread graphique",errors.getFirst()); }
            fx(() -> {TextField money=com.hotelsys.ui.UI.moneyInput("","");money.setText("abc");HotelStoreAssert.equal(0,money.getLength());money.setText("12.34");money.appendText("5");HotelStoreAssert.equal(5,money.getLength());TextField count=com.hotelsys.ui.UI.countInput("2",1,100);count.setText("101");HotelStoreAssert.equal(2,Long.parseLong(count.getText()));return null;});
            System.out.println("UI CHECK OK: setup, eight pages, quote, persisted booking, logout/login; 3 screenshots in target/ui-check.");
        } finally {
            fx(() -> { if (app != null) { app.stop(); } for (Window window : List.copyOf(Window.getWindows())) { window.hide(); } return null; }); Platform.exit();
        }
    }
    private static Node find(String selector) {
        for (Window window : Window.getWindows()) { if (window.getScene() != null) { window.getScene().getRoot().applyCss(); Node found = window.getScene().lookup(selector); if (found != null) { return found; } } } return null;
    }
    private static Button button(String selector) { return (Button) find(selector); }
    @SuppressWarnings("unchecked") private static ComboBox<Row> combo(String selector) { return (ComboBox<Row>) find(selector); }
    private static void text(String selector,String text) { ((TextInputControl) find(selector)).setText(text); }
    private static <T> T fx(Callable<T> action) throws Exception { FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(20,TimeUnit.SECONDS); }
    private static void await(Callable<Boolean> condition) throws Exception {
        for (int i=0;i<160;i++) { if (fx(condition)) { return; } Thread.sleep(50); } throw new AssertionError("Interface non prête");
    }
    private static void snapshot(Stage stage,String filename) throws Exception {
        fx(() -> {
            stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout(); WritableImage image=stage.getScene().snapshot(null);
            BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for (int y=0;y<png.getHeight();y++) { for (int x=0;x<png.getWidth();x++) { png.setRGB(x,y,image.getPixelReader().getArgb(x,y)); } }
            ImageIO.write(png,"png",OUT.resolve(filename).toFile()); return null;
        });
    }
    private static final class HotelStoreAssert {
        private HotelStoreAssert() { }
        static void equal(long expected,long actual) { if(expected!=actual){throw new AssertionError(expected+" != "+actual);} }
    }
}
