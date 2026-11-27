package com.hotelsys.ui;

import com.hotelsys.model.Row;
import com.hotelsys.service.HotelStore;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public final class UI {
    private UI() { }
    public static Label label(String text, String style) { Label label = new Label(text); label.getStyleClass().add(style); return label; }
    public static Label muted(String text) { Label label = label(text, "muted"); label.setWrapText(true); return label; }
    public static VBox box(double spacing, Node... nodes) { return new VBox(spacing, nodes); }
    public static HBox row(double spacing, Node... nodes) { HBox row = new HBox(spacing, nodes); row.setAlignment(Pos.CENTER_LEFT); return row; }
    public static Region spacer() { Region node = new Region(); HBox.setHgrow(node, Priority.ALWAYS); return node; }
    public static Button button(String text, String style, Runnable action) {
        Button button = new Button(text); button.getStyleClass().add(style);
        button.setOnAction(event -> { try { action.run(); } catch (RuntimeException failure) { alert(failure.getMessage()); } }); return button;
    }
    public static TextField input(String value, String prompt) { return input(value,prompt,150); }
    public static TextField input(String value,String prompt,int max) {
        TextField input=new TextField(value);input.setPromptText(prompt);input.setMaxWidth(Double.MAX_VALUE);
        filter(input,max,"[\\s\\S]*",false);return input;
    }
    public static void filter(TextField field,int max,String pattern,boolean uppercase) {
        field.setTextFormatter(new TextFormatter<String>(change -> {
            if(uppercase && change.isContentChange()){change.setText(change.getText().toUpperCase(Locale.ROOT));}
            String next=change.getControlNewText();return next.length()<=max && next.matches(pattern)?change:null;
        }));
    }
    public static TextField moneyInput(String value,String prompt){TextField field=input(value,prompt);filter(field,12,"[0-9]{0,9}([,.][0-9]{0,2})?",false);return field;}
    public static TextField countInput(String value,int min,int max){
        TextField field=input(value,"");countFilter(field,min,max);return field;
    }
    public static void countFilter(TextField field,int min,int max){field.setTextFormatter(new TextFormatter<String>(change -> {
            String next=change.getControlNewText();if(next.isEmpty()){return change;}
            if(!next.matches("[0-9]{1,3}")){return null;}int number=Integer.parseInt(next);return number>=min && number<=max?change:null;
        }));
    }
    public static DatePicker datePicker(LocalDate date){
        DatePicker picker=new DatePicker(date);DateTimeFormatter format=DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT);
        picker.setConverter(new StringConverter<>(){
            @Override public String toString(LocalDate value){return value==null?"":value.format(format);}
            @Override public LocalDate fromString(String text){if(text==null||text.isBlank()){return null;}try{return LocalDate.parse(text,format);}catch(java.time.format.DateTimeParseException invalid){return null;}}
        });filter(picker.getEditor(),10,"[0-9/]*",false);picker.setPromptText("jj/mm/aaaa");return picker;
    }
    public static ComboBox<Integer> category(int selected){
        ComboBox<Integer> combo=new ComboBox<>();for(int stars=0;stars<=5;stars++){combo.getItems().add(stars);}combo.setValue(selected);combo.setMaxWidth(Double.MAX_VALUE);
        java.util.function.Supplier<ListCell<Integer>> cells=() -> new ListCell<>(){
            @Override protected void updateItem(Integer value,boolean empty){super.updateItem(value,empty);setText(null);setGraphic(null);if(empty||value==null){return;}
                HBox line=row(12,label(HotelStore.category(value),"strong"));for(int i=0;i<value;i++){javafx.scene.shape.SVGPath star=new javafx.scene.shape.SVGPath();star.setContent("M8 0L10.5 5L16 6L12 10L13 16L8 13L3 16L4 10L0 6L5.5 5Z");star.setFill(javafx.scene.paint.Color.web("#A87929"));line.getChildren().add(star);}setGraphic(line);
            }
        };combo.setCellFactory(ignore -> cells.get());combo.setButtonCell(cells.get());return combo;
    }
    public static ToggleButton toggle(boolean selected){
        ToggleButton button=new ToggleButton();button.setSelected(selected);button.getStyleClass().add("switch");button.setAccessibleText("Activé");
        StackPane track=new StackPane();track.getStyleClass().add("switch-track");javafx.scene.shape.Circle knob=new javafx.scene.shape.Circle(9,javafx.scene.paint.Color.WHITE);knob.translateXProperty().bind(javafx.beans.binding.Bindings.when(button.selectedProperty()).then(10).otherwise(-10));track.getChildren().add(knob);button.setGraphic(track);return button;
    }
    public static Node serviceRow(String text,ToggleButton toggle){return row(12,label(text,"field-label"),spacer(),toggle);}
    public static VBox field(String name, Node control) { Label label = label(name, "field-label"); if (control instanceof Control c) { label.setLabelFor(c); c.setMaxWidth(Double.MAX_VALUE); c.setAccessibleText(name); } return box(6, label, control); }
    public static HBox columns(Node... nodes) {
        HBox row = row(16, nodes); for (Node node : nodes) { HBox.setHgrow(node, Priority.ALWAYS); if (node instanceof Region r) { r.setMaxWidth(Double.MAX_VALUE); r.setPrefWidth(1); } } return row;
    }
    public static ScrollPane scroll(Node content) { ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("clean-scroll"); return scroll; }
    public static VBox page(String title, String subtitle, Node action) {
        VBox heading = box(6, label(title, "page-title"), muted(subtitle)); HBox top = row(16, heading, spacer());
        if (action != null) { top.getChildren().add(action); } VBox page = box(24, top); page.getStyleClass().add("page"); return page;
    }
    public static VBox panel(Node... nodes) { VBox panel = box(16, nodes); panel.getStyleClass().add("panel"); return panel; }
    public static String money(long amount) { NumberFormat f = NumberFormat.getNumberInstance(Locale.FRANCE); f.setMinimumFractionDigits(2); f.setMaximumFractionDigits(2); return f.format(BigDecimal.valueOf(amount, 2)) + " DZD"; }
    public static String amount(long amount) { return BigDecimal.valueOf(amount, 2).toPlainString(); }
    public static long cents(String text) {
        try { long value = new BigDecimal(text.strip().replace(" ", "").replace("\u00a0", "").replace(',', '.')).movePointRight(2).longValueExact(); HotelStore.check(value >= 0, "Le montant ne peut pas être négatif"); return value; }
        catch (NumberFormatException | ArithmeticException failure) { throw new IllegalArgumentException("Montant invalide : utilisez au maximum deux décimales"); }
    }
    public static int integer(String text) { try { return Integer.parseInt(text.strip()); } catch (NumberFormatException failure) { throw new IllegalArgumentException("Saisissez un nombre entier"); } }
    public static String date(String date) { return date == null || date.isBlank() ? "—" : LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH)); }
    public static String time(String timestamp) { return java.time.Instant.parse(timestamp).atZone(HotelStore.ZONE).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH)); }
    public static String statusName(String status) { return switch (status) { case "RESERVED" -> "Réservé"; case "CHECKED_IN" -> "En séjour"; case "CHECKED_OUT" -> "Terminé"; case "CANCELLED" -> "Annulé"; case "FREE" -> "Disponible"; default -> status; }; }
    public static String mealName(String meal) { return switch (meal) { case "BREAKFAST" -> "Petit-déjeuner"; case "LUNCH" -> "Déjeuner"; case "DINNER" -> "Dîner"; default -> meal; }; }
    public static Label badge(String status) { Label label = label(statusName(status), "badge"); label.getStyleClass().add("status-" + status.toLowerCase(Locale.ROOT)); return label; }
    public static ComboBox<Row> choose(List<Row> rows) {
        ComboBox<Row> combo = new ComboBox<>(FXCollections.observableArrayList(rows)); combo.setMaxWidth(Double.MAX_VALUE); combo.setPromptText("Choisir");
        combo.setConverter(new StringConverter<>() { @Override public String toString(Row row) { return row == null ? "" : row.text("label"); } @Override public Row fromString(String s) { return null; } }); return combo;
    }
    public static void select(ComboBox<Row> combo, long id) { combo.getItems().stream().filter(row -> row.number("id") == id).findFirst().ifPresent(combo::setValue); }
    public static long id(ComboBox<Row> combo) { HotelStore.check(combo.getValue() != null, "Complétez la sélection"); return combo.getValue().number("id"); }
    public static TableView<Row> table() {
        TableView<Row> table = new TableView<>(); table.getStyleClass().add("data-table"); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(muted("Aucun élément à afficher")); table.setMinHeight(240); table.setPrefHeight(460); VBox.setVgrow(table, Priority.ALWAYS); return table;
    }
    public static void column(TableView<Row> table, String title, double width, Function<Row, String> value) {
        TableColumn<Row, String> column = new TableColumn<>(title); column.setPrefWidth(width); column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue()))); table.getColumns().add(column);
    }
    public static void statusColumn(TableView<Row> table) {
        TableColumn<Row, String> column = new TableColumn<>("État"); column.setPrefWidth(120); column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().text("status")));
        column.setCellFactory(ignore -> new TableCell<>() { @Override protected void updateItem(String item, boolean empty) { super.updateItem(item, empty); setText(null); setGraphic(empty || item == null ? null : badge(item)); } }); table.getColumns().add(column);
    }
    public static void openRows(TableView<Row> table, Consumer<Row> open) {
        table.setRowFactory(ignore -> { TableRow<Row> row = new TableRow<>(); row.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && !row.isEmpty()) { open.accept(row.getItem()); } }); return row; });
        table.setOnKeyPressed(event -> { if (event.getCode() == javafx.scene.input.KeyCode.ENTER && table.getSelectionModel().getSelectedItem() != null) { open.accept(table.getSelectionModel().getSelectedItem()); } });
    }
    public static void alert(String message) { Alert alert = new Alert(Alert.AlertType.INFORMATION, message == null ? "Opération impossible" : message, ButtonType.OK); alert.setHeaderText(null); alert.setTitle("HotelSys"); alert.showAndWait(); }
    public static void confirm(String message, Runnable yes) { Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK); alert.setHeaderText(null); alert.setTitle("Confirmer"); alert.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> yes.run()); }

    public static final class Form {
        public final Stage stage = new Stage();
        public final VBox body = box(16);
        public final Label error = label("", "error");
        public final Button submit = new Button("Enregistrer");
        public Form(Stage owner, String title, String subtitle) {
            stage.initOwner(owner); stage.initModality(Modality.WINDOW_MODAL); stage.setTitle(title + " · HotelSys");
            submit.getStyleClass().add("primary"); submit.setDefaultButton(true);
            Button cancel = button("Fermer", "secondary", stage::close); cancel.setCancelButton(true);
            ScrollPane scroll = scroll(body); scroll.setPrefViewportHeight(480);
            HBox footer = row(12, spacer(), cancel, submit); error.setWrapText(true);
            VBox root = box(20, box(6, label(title, "dialog-title"), muted(subtitle)), scroll, error, footer); root.setPadding(new Insets(28)); VBox.setVgrow(scroll, Priority.ALWAYS);
            Scene scene = new Scene(root, 720, 720); scene.getStylesheets().add(UI.class.getResource("/css/app.css").toExternalForm()); stage.setScene(scene);
            stage.setMinWidth(560); stage.setMinHeight(440);
            stage.setHeight(Math.min(760, javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() - 48));
        }
        public void action(String text, Runnable action) { submit.setText(text); submit.setOnAction(event -> { error.setText(""); try { action.run(); } catch (RuntimeException failure) { fail(failure.getMessage()); } }); }
        public void busy(boolean busy) { submit.setDisable(busy); body.setDisable(busy); }
        public void fail(String message) { busy(false); error.setText(message); }
        public void show() { stage.show(); }
    }
}
