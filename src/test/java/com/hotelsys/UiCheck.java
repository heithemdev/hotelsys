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
                text("#setupHotel", "Hôtel El Bahia"); text("#setupName", "Heithem"); text("#loginUsername", "demo.admin");
                text("#loginPassword", "Reception-demo-2026"); text("#setupConfirmation", "Reception-demo-2026"); ((CheckBox) find("#setupDemo")).setSelected(true); button("#loginSubmit").fire(); return null;
            });
            await(() -> find("#page-dashboard") != null && find(".chart") != null);
            Thread.sleep(350); snapshot(app.stage(), "dashboard.png");
            fx(() -> { app.navigate("calendar"); return null; }); await(() -> find(".stay-bar") != null); Thread.sleep(300); snapshot(app.stage(), "calendar.png");
            fx(() -> { BookingEditor.create(app, 0, app.store().today().plusDays(10)); return null; }); await(() -> find("#bookingHolder") != null);
            fx(() -> {
                combo("#bookingHolder").getSelectionModel().selectFirst(); combo("#bookingRoom").getSelectionModel().selectFirst();
                MenuButton people = (MenuButton) find("#bookingGuests"); ((CheckBox) ((CustomMenuItem) people.getItems().getFirst()).getContent()).setSelected(true);
                button("#quoteBooking").fire(); return null;
            });
            await(() -> !button("#saveBooking").isDisabled());
            fx(() -> { HotelStoreAssert.equal(7, app.store().bookings().size() + 1); return null; });
            Stage dialog = fx(() -> (Stage) find("#saveBooking").getScene().getWindow()); snapshot(dialog,"booking.png");
            fx(() -> { button("#saveBooking").fire(); return null; }); await(() -> find("#saveBooking") == null);
            fx(() -> { HotelStoreAssert.equal(7,app.store().bookings().size()); return null; });
            for (String page : List.of("people","rooms","cash","team","settings","reservations")) {
                fx(() -> { app.navigate(page); return null; }); await(() -> find("#page-" + page) != null); Thread.sleep(150); fx(() -> { app.stage().getScene().getRoot().applyCss(); app.stage().getScene().getRoot().layout(); return null; });
            }
            fx(() -> { app.store().logout(); app.showLogin(false); return null; }); await(() -> find("#loginSubmit") != null);
            fx(() -> { text("#loginUsername","demo.admin"); text("#loginPassword","incorrect"); button("#loginSubmit").fire(); return null; });
            await(() -> !button("#loginSubmit").isDisabled());
            fx(() -> { text("#loginPassword","Reception-demo-2026"); button("#loginSubmit").fire(); return null; }); await(() -> find("#page-reservations") != null);
            if (!errors.isEmpty()) { throw new AssertionError("Erreur sur le thread graphique",errors.getFirst()); }
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
