package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import com.hotelsys.model.Row;
import com.hotelsys.model.StayDraft;
import com.hotelsys.service.HotelStore;
import java.time.LocalDate;
import java.util.*;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class BookingEditor {
    private record Data(List<Row> guests, List<Row> rooms, Map<Long, Set<String>> meals) { }
    private final HotelApp app;
    private final Data data;
    private List<Row> currentGuests;
    private final UI.Form form;
    private final ComboBox<Row> holder;
    private final TextArea notes = new TextArea();
    private final VBox stays = UI.box(20);
    private final List<StayFields> fields = new ArrayList<>();
    private final Label total = UI.label("Calculez votre devis", "section-title");
    private final long editing;
    private Long acceptedTotal;

    public static void create(HotelApp app, long room, LocalDate arrival) {
        app.run(() -> load(app), data -> new BookingEditor(app, data, 0, 0, room, arrival, null, List.of(), Set.of()).form.show());
    }
    public static void edit(HotelApp app, Row booking, Row stay, List<Row> guests, List<Row> meals) {
        app.run(() -> load(app), data -> new BookingEditor(app, data, stay.number("id"), booking.number("holder_id"), stay.number("room_id"), stay.date("arrival"), stay.date("departure"), guests.stream().map(r -> r.number("id")).toList(), new HashSet<>(meals.stream().map(r -> r.text("meal")).toList())).form.show());
    }
    private static Data load(HotelApp app) throws java.sql.SQLException {
        List<Row> rooms = app.store().rooms().stream().filter(r -> r.flag("active")).toList(); Map<Long, Set<String>> rates = new HashMap<>();
        for (Row room : rooms) { long model = room.number("model_id"); if (!rates.containsKey(model)) { rates.put(model, new HashSet<>(app.store().offeredMealRates(model).stream().map(r -> r.text("meal")).toList())); } }
        return new Data(app.store().guests(), rooms, rates);
    }
    private BookingEditor(HotelApp app, Data data, long editing, long holderId, long room, LocalDate arrival, LocalDate departure, List<Long> occupants, Set<String> meals) {
        this.app = app; this.data = data; this.currentGuests = data.guests(); this.editing = editing;
        form = new UI.Form(app.stage(), editing == 0 ? "Nouvelle réservation" : "Modifier le séjour", editing == 0 ? "Une ou plusieurs chambres, chacune avec ses propres dates" : "Le devis utilise les tarifs actuels. Le contrôle familial sera renouvelé.");
        form.stage.setWidth(800);
        holder = UI.choose(data.guests()); holder.setId("bookingHolder"); UI.select(holder, holderId); holder.setDisable(editing != 0); holder.valueProperty().addListener((o,a,b) -> invalidate());
        Button newGuest = UI.button("+ Client", "secondary", () -> PeoplePage.edit(app, null, this::reloadGuests));
        form.body.getChildren().addAll(UI.columns(UI.field("Titulaire de la réservation", holder), UI.box(6, UI.muted("Nouvelle fiche"), newGuest)), stays);
        addStay(room, arrival, departure == null ? arrival.plusDays(1) : departure, occupants, meals);
        if (editing == 0) {
            notes.setPrefRowCount(2); notes.setWrapText(true);
            form.body.getChildren().addAll(UI.button("+ Ajouter une chambre", "secondary", () -> addStay(0, app.store().today(), app.store().today().plusDays(1), List.of(), Set.of())), UI.field("Notes · facultatif", notes));
        }
        Button calculate = UI.button("Calculer le devis", "secondary", this::calculate); calculate.setId("quoteBooking");
        form.body.getChildren().add(UI.panel(UI.row(16, total, UI.spacer(), calculate), UI.muted("Chambre au prix fixe par nuit. Repas choisis pour tous les occupants, avec le tarif enfant applicable.")));
        form.submit.setId("saveBooking");
        form.action(editing == 0 ? "Confirmer la réservation" : "Enregistrer le séjour", () -> {
            HotelStore.check(acceptedTotal != null, "Calculez le devis avant de confirmer"); long person = UI.id(holder); List<StayDraft> drafts = drafts(); long amount = acceptedTotal; String text = notes.getText();
            app.save(form, () -> { if (editing == 0) { return app.store().book(person, drafts, text, amount); } app.store().updateStay(editing, drafts.getFirst(), amount); return editing; }, () -> { app.refresh(); app.toast(editing == 0 ? "Réservation enregistrée" : "Séjour modifié"); });
        }); invalidate();
    }
    private void reloadGuests() {
        app.run(app.store()::guests, guests -> {
            currentGuests = guests;
            long previous = holder.getValue() == null ? 0 : holder.getValue().number("id"); holder.setItems(FXCollections.observableArrayList(guests)); UI.select(holder, previous);
            for (StayFields stay : fields) { stay.fillGuests(guests); } invalidate();
        });
    }
    private void addStay(long room, LocalDate arrival, LocalDate departure, List<Long> occupants, Set<String> meals) {
        StayFields stay = new StayFields(room, arrival, departure, occupants, meals); fields.add(stay); stays.getChildren().add(stay.view); invalidate();
    }
    private void invalidate() { acceptedTotal = null; total.setText("Calculez votre devis"); form.submit.setDisable(true); }
    private List<StayDraft> drafts() { return fields.stream().map(StayFields::draft).toList(); }
    private void calculate() {
        try {
            form.error.setText(""); long person = UI.id(holder); List<StayDraft> draft = drafts(); form.busy(true);
            app.run(() -> app.store().quote(person, draft, editing), quote -> {
                form.busy(false); acceptedTotal = quote.total(); total.setText(UI.money(quote.total())); form.submit.setDisable(false);
                for (int i = 0; i < fields.size(); i++) { fields.get(i).price.setText(UI.money(quote.roomTotals().get(i))); }
            }, message -> { form.fail(message); invalidate(); });
        } catch (RuntimeException failure) { form.fail(failure.getMessage()); }
    }
    private final class StayFields {
        private final ComboBox<Row> room = UI.choose(data.rooms());
        private final DatePicker arrival, departure;
        private final MenuButton people = new MenuButton("Choisir les occupants");
        private final Map<Long, CheckBox> occupants = new LinkedHashMap<>();
        private final Map<String, CheckBox> mealControls = new LinkedHashMap<>();
        private final Label capacity = UI.muted("Choisissez une chambre"), price = UI.label("", "strong");
        private final VBox view;
        StayFields(long roomId, LocalDate from, LocalDate to, List<Long> guests, Set<String> selectedMeals) {
            arrival = UI.datePicker(from); departure = UI.datePicker(to); people.setMaxWidth(Double.MAX_VALUE);
            room.setId("bookingRoom"); arrival.setId("stayArrival"); departure.setId("stayDeparture"); people.setId("bookingGuests");
            fillGuests(currentGuests); for (long id : guests) { if (occupants.containsKey(id)) { occupants.get(id).setSelected(true); } }
            HBox mealRow = UI.row(12);
            for (String meal : List.of("BREAKFAST", "LUNCH", "DINNER")) { CheckBox selected = new CheckBox(UI.mealName(meal)); selected.setSelected(selectedMeals.contains(meal)); selected.selectedProperty().addListener((o,a,b) -> invalidate()); mealControls.put(meal, selected); mealRow.getChildren().add(selected); }
            room.valueProperty().addListener((o,a,b) -> { updateRoom(); invalidate(); }); UI.select(room, roomId); updateRoom();
            arrival.valueProperty().addListener((o,a,b) -> invalidate()); departure.valueProperty().addListener((o,a,b) -> invalidate());
            Button remove = UI.button("Retirer", "quiet", this::remove); remove.setVisible(editing == 0);
            view = UI.panel(UI.row(12, UI.label("Chambre réservée", "section-title"), UI.spacer(), remove), UI.field("Chambre", room), capacity,
                    UI.columns(UI.field("Arrivée", arrival), UI.field("Départ", departure)), UI.field("Occupants de cette chambre", people), mealRow, price);
            updatePeople();
        }
        void remove() { fields.remove(this); stays.getChildren().remove(view); invalidate(); }
        void fillGuests(List<Row> guests) {
            Set<Long> chosen = new HashSet<>(); occupants.forEach((id, check) -> { if (check.isSelected()) { chosen.add(id); } }); occupants.clear(); people.getItems().clear();
            for (Row guest : guests) {
                CheckBox check = new CheckBox(guest.text("label") + " · " + UI.date(guest.text("birth_date"))); check.setSelected(chosen.contains(guest.number("id")));
                check.selectedProperty().addListener((o,a,b) -> { updatePeople(); invalidate(); }); occupants.put(guest.number("id"), check);
                CustomMenuItem item = new CustomMenuItem(check, false); people.getItems().add(item);
            } updatePeople();
        }
        void updatePeople() { long count = occupants.values().stream().filter(CheckBox::isSelected).count(); people.setText(count == 0 ? "Choisir les occupants" : count + " occupant(s) sélectionné(s)"); }
        void updateRoom() {
            Row selected = room.getValue(); capacity.setText(selected == null ? "Choisissez une chambre" : selected.number("bed_count") + " lits · " + selected.number("max_sharing_children") + " enfant(s) sans lit maximum · " + UI.money(selected.number("nightly_price")) + " / nuit");
            mealControls.forEach((meal, checkbox) -> { boolean available = selected != null && data.meals().getOrDefault(selected.number("model_id"), Set.of()).contains(meal); checkbox.setDisable(!available);checkbox.setVisible(available);checkbox.setManaged(available); if (!available) { checkbox.setSelected(false); } });
        }
        StayDraft draft() { return new StayDraft(UI.id(room), arrival.getValue(), departure.getValue(), occupants.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList(), new HashSet<>(mealControls.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList())); }
    }
}
