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
    private record Data(List<Row> guests, List<Row> rooms, Set<String> meals) { }
    private final HotelApp app;
    private final Data data;
    private List<Row> currentGuests;
    private final UI.Form form;
    private final ComboBox<Row> holder;
    private final TextArea notes = new TextArea();
    private final VBox stays = UI.box(20);
    private final List<StayFields> fields = new ArrayList<>();
    private final Label total = UI.label("Complétez le séjour", "section-title");
    private boolean quoteQueued;
    private long revision;
    private final long editing;
    private Long acceptedTotal;

    public static void create(HotelApp app, long room, LocalDate arrival) {
        app.run(() -> load(app), data -> {
            if(data.rooms().isEmpty()){app.navigate("rooms");app.toast("Créez un modèle puis une chambre avant de réserver");return;}
            new BookingEditor(app, data, 0, 0, room, arrival, null, List.of(), Set.of()).form.show();
        });
    }
    public static void edit(HotelApp app, Row booking, Row stay, List<Row> guests, List<Row> meals) {
        app.run(() -> load(app), data -> {
            BookingEditor editor=new BookingEditor(app,data,stay.number("id"),booking.number("holder_id"),stay.number("room_id"),stay.date("arrival"),stay.date("departure"),guests.stream().map(r -> r.number("id")).toList(),new HashSet<>(meals.stream().map(r -> r.text("meal")).toList()));
            editor.fields.getFirst().arrivalTime.setText(stay.text("arrival_time"));editor.fields.getFirst().departureTime.setText(stay.text("departure_time"));editor.form.show();
        });
    }
    private static Data load(HotelApp app) throws java.sql.SQLException {
        List<Row> rooms=app.store().rooms().stream().filter(r -> r.flag("active")).toList();
        Set<String> meals=new HashSet<>(app.store().offeredMealRates().stream().map(r -> r.text("meal")).toList());
        return new Data(app.store().guests(),rooms,meals);
    }
    private BookingEditor(HotelApp app, Data data, long editing, long holderId, long room, LocalDate arrival, LocalDate departure, List<Long> occupants, Set<String> meals) {
        this.app = app; this.data = data; this.currentGuests = data.guests(); this.editing = editing;
        form = new UI.Form(app.stage(), editing == 0 ? "Nouvelle réservation" : "Modifier le séjour", editing == 0 ? "" : "Le devis utilise les tarifs actuels. Le contrôle familial sera renouvelé.");
        form.stage.setWidth(800);
        holder = UI.choose(data.guests()); holder.setId("bookingHolder"); UI.select(holder, holderId); holder.setDisable(editing != 0); holder.valueProperty().addListener((o,a,b) -> invalidate());
        Button newGuest = UI.button("Nouveau client", "secondary", () -> PeoplePage.edit(app, null, this::reloadGuests));
        HBox holderRow = UI.row(8, holder, newGuest); HBox.setHgrow(holder, Priority.ALWAYS);
        form.body.getChildren().addAll(UI.field("Titulaire", holderRow), stays); holder.setAccessibleText("Titulaire");
        addStay(room, arrival, departure == null ? arrival.plusDays(1) : departure, occupants, meals);
        if (editing == 0) {
            notes.setPrefRowCount(2); notes.setWrapText(true);
            form.body.getChildren().addAll(UI.button("Ajouter une chambre", "secondary", () -> addStay(0, app.store().today(), app.store().today().plusDays(1), List.of(), Set.of())), UI.field("Notes · facultatif", notes));
        }
        form.footer.getChildren().addFirst(UI.box(0,UI.label("Total du séjour","small"),total));form.submit.setId("saveBooking");
        form.action(editing == 0 ? "Confirmer et encaisser" : "Enregistrer et encaisser", () -> {
            HotelStore.check(acceptedTotal != null, "Complétez le séjour et attendez le calcul automatique"); long person = UI.id(holder); List<StayDraft> drafts = drafts(); long amount = acceptedTotal; String text = notes.getText();
            app.save(form, () -> editing == 0
                    ? app.store().book(person, drafts, text, amount).paymentRecorded()
                    : app.store().updateStay(editing, drafts.getFirst(), amount), paid -> {
                if (paid) { app.playPaymentSound(); }
                app.refresh();
                app.toast(editing == 0 ? "Réservation enregistrée" : "Séjour modifié");
            });
        }); form.stage.setOnHidden(e -> revision++); invalidate();
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
    private void invalidate() {
        revision++;
        acceptedTotal = null;
        fields.forEach(f -> f.price.setText(""));
        total.setText("Calcul en cours…");
        form.submit.setDisable(true);
        if (quoteQueued) { return; }
        quoteQueued = true;
        javafx.application.Platform.runLater(() -> {
            quoteQueued = false;
            if (form.stage.isShowing()) { calculate(); }
        });
    }
    private List<StayDraft> drafts() { return fields.stream().map(StayFields::draft).toList(); }
    private void calculate() {
        final long request=revision;
        try {
            long person=UI.id(holder);List<StayDraft> draft=drafts();
            HotelStore.check(!draft.isEmpty() && draft.stream().allMatch(stay -> stay.arrival() != null && stay.departure() != null), "Dates requises");
            app.run(() -> app.store().quote(person,draft,editing),quote -> {
                if(request!=revision){return;}form.error.setText("");acceptedTotal=quote.total();total.setText(UI.money(quote.total()));form.submit.setDisable(false);
                for(int i=0;i<fields.size();i++){fields.get(i).price.setText(UI.money(quote.roomTotals().get(i)));}
            },message -> {if(request==revision){acceptedTotal=null;total.setText("Séjour à compléter");form.error.setText(message);form.submit.setDisable(true);}});
        }catch(RuntimeException incomplete){total.setText("Complétez le séjour");form.error.setText("");}
    }
    private final class StayFields {
        private final ComboBox<Row> room = UI.choose(data.rooms());
        private final DatePicker arrival, departure;
        private final TextField arrivalTime=UI.timeInput("14:00"),departureTime=UI.timeInput("11:00");
        private final MenuButton people = new MenuButton("Choisir les occupants");
        private final Map<Long, CheckBox> occupants = new LinkedHashMap<>();
        private final Map<String, CheckBox> mealControls = new LinkedHashMap<>();
        private final Label capacity = UI.muted(""), price = UI.label("", "strong");
        private final VBox view;
        private final HBox mealRow = UI.row(12);
        StayFields(long roomId, LocalDate from, LocalDate to, List<Long> guests, Set<String> selectedMeals) {
            arrival = UI.datePicker(from); departure = UI.datePicker(to); people.setMaxWidth(Double.MAX_VALUE);
            if(editing==0 && from.equals(app.store().today()) && app.store().localNow().toLocalTime().isAfter(java.time.LocalTime.of(14,0))){
                var next=app.store().localNow().plusMinutes(15).withSecond(0).withNano(0);if(!next.toLocalDate().equals(from) || next.toLocalTime().isAfter(java.time.LocalTime.of(22,0))){arrival.setValue(from.plusDays(1));departure.setValue(from.plusDays(2));arrivalTime.setText("08:00");}else{arrivalTime.setText(next.toLocalTime().toString());}
            }
            UI.minimumDate(arrival, app.store().today());
            UI.minimumDate(departure, arrival.getValue());
            arrivalTime.textProperty().addListener((o,a,b) -> invalidate());departureTime.textProperty().addListener((o,a,b) -> invalidate());
            room.setId("bookingRoom"); arrival.setId("stayArrival"); departure.setId("stayDeparture"); people.setId("bookingGuests");
            fillGuests(currentGuests); for (long id : guests) { if (occupants.containsKey(id)) { occupants.get(id).setSelected(true); } }
            if (!data.meals().isEmpty()) { mealRow.getChildren().add(UI.label("Repas par personne et par nuit", "field-label")); }
            for (String meal : List.of("BREAKFAST", "LUNCH", "DINNER")) { CheckBox selected = new CheckBox(UI.mealName(meal)); selected.setSelected(selectedMeals.contains(meal)); selected.selectedProperty().addListener((o,a,b) -> invalidate()); mealControls.put(meal, selected); mealRow.getChildren().add(selected); }
            room.valueProperty().addListener((o,a,b) -> { updateRoom(); invalidate(); }); UI.select(room, roomId); updateRoom();
            arrival.valueProperty().addListener((observable, old, selected) -> {
                UI.minimumDate(departure, selected == null ? app.store().today() : selected);
                LocalDate end = departure.getValue();
                if (selected != null && end != null && end.isBefore(selected)) {
                    departure.setValue(selected.plusDays(1));
                }
                invalidate();
            });
            departure.valueProperty().addListener((o,a,b) -> invalidate());
            Button remove = UI.button("Retirer", "quiet", this::remove); remove.setVisible(editing == 0); remove.setManaged(editing == 0);
            view = UI.panel(UI.row(12, UI.label("Séjour", "section-title"), UI.spacer(), remove), UI.field("Chambre", room), capacity,
                    UI.columns(UI.field("Arrivée", UI.row(8, arrival, arrivalTime)), UI.field("Départ", UI.row(8, departure, departureTime))),UI.muted("De 08:00 à 22:00 · trois heures entre deux séjours dans la même chambre"), UI.field("Occupants", people), mealRow, price);
            arrivalTime.setPrefWidth(64); departureTime.setPrefWidth(64);
            arrival.setAccessibleText("Date d’arrivée"); departure.setAccessibleText("Date de départ");
            arrivalTime.setAccessibleText("Heure d’arrivée"); departureTime.setAccessibleText("Heure de départ");
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
        void updatePeople() { long count = occupants.values().stream().filter(CheckBox::isSelected).count(); people.setText(count == 0 ? "Choisir les occupants" : UI.quantity(count, "occupant", "occupants")); }
        void updateRoom() {
            Row selected = room.getValue();
            mealRow.setVisible(selected != null && !data.meals().isEmpty()); mealRow.setManaged(mealRow.isVisible());
            capacity.setVisible(selected != null); capacity.setManaged(selected != null);
            capacity.setText(selected == null ? "" : RoomsPage.capacity(selected) + " · " + UI.money(selected.number("nightly_price")) + " / nuit");
            mealControls.forEach((meal, checkbox) -> { boolean available = selected != null && data.meals().contains(meal); checkbox.setDisable(!available);checkbox.setVisible(available);checkbox.setManaged(available); if (!available) { checkbox.setSelected(false); } });
        }
        StayDraft draft() { return new StayDraft(UI.id(room), arrival.getValue(), departure.getValue(), occupants.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList(), new HashSet<>(mealControls.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList()),UI.parseTime(arrivalTime),UI.parseTime(departureTime)); }
    }
}
