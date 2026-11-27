package com.hotelsys.ui;

import com.hotelsys.app.HotelApp;
import com.hotelsys.model.Row;
import com.hotelsys.service.HotelStore;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

public final class DashboardPage {
    private final HotelApp app;
    public DashboardPage(HotelApp app){this.app=app;}
    public Node view(){
        Button add=UI.button("Nouvelle réservation","primary",() -> BookingEditor.create(app,0,app.store().today()));add.setId("newBooking");
        VBox page=UI.page("Tableau de bord",add);page.setId("page-dashboard");UI.bookingPrerequisite(app,add,page);
        Button keys = UI.button("Gérer les clés", "secondary", () -> KeysDialog.open(app));
        keys.setGraphic(Icons.get("key")); keys.setDisable(true);
        HBox operations = UI.row(0); operations.getStyleClass().add("reception-metrics");
        VBox reception = UI.panel(UI.row(12, UI.label("Réception du jour", "section-title"), UI.spacer(), keys), operations);
        page.getChildren().add(reception);
        app.run(app.store()::overview,o -> operations.getChildren().setAll(metric("Chambres occupées",o.number("occupied")+" / "+o.number("rooms"),null),metric("Arrivées attendues",o.text("arrivals"),null),metric("Départs du jour",o.text("departures"),null)));

        Runnable refreshKeys=() -> app.run(app.store()::keyStays,stays -> {
            if(reception.getChildren().size()>2){reception.getChildren().remove(2,reception.getChildren().size());}
            keys.setDisable(stays.isEmpty());
            for(Row stay:stays){if(stay.text("status").equals("CHECKED_IN") && !HotelStore.end(stay).minusHours(3).isAfter(app.store().localNow())){
                boolean ended=!HotelStore.end(stay).isAfter(app.store().localNow());
                reception.getChildren().add(UI.row(12,UI.badge(ended?"OVERDUE":"DUE"),UI.label("Ch. "+stay.text("number")+" · "+stay.text("holder"),"strong"),UI.spacer(),UI.muted("Départ "+UI.date(stay.text("departure"))+" à "+stay.text("departure_time")),UI.button("Ouvrir","secondary",() -> ReservationsPage.open(app,stay.number("reservation_id")))));
            }}
        });refreshKeys.run();
        javafx.animation.Timeline clock=new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(30),e -> refreshKeys.run()));clock.setCycleCount(javafx.animation.Animation.INDEFINITE);clock.play();page.sceneProperty().addListener((o,a,b) -> {if(b==null){clock.stop();}});
        if(app.store().user().admin()){business(page);}else{upcoming(page);}
        return UI.scroll(page);
    }
    private void business(VBox page){
        LocalDate today=app.store().today();ComboBox<String> period=new ComboBox<>(FXCollections.observableArrayList("Aujourd'hui","7 derniers jours","Ce mois","30 derniers jours","Personnalisé"));period.setValue("Ce mois");
        DatePicker from=UI.datePicker(today.withDayOfMonth(1)),through=UI.datePicker(today);from.setPrefWidth(160);through.setPrefWidth(160);
        VBox report=UI.box(16);Button refresh=new Button("Actualiser");refresh.getStyleClass().add("secondary");Label error=UI.label("","error");error.visibleProperty().bind(error.textProperty().isNotEmpty());error.managedProperty().bind(error.visibleProperty());
        Runnable load=() -> {
            LocalDate begin=from.getValue(),end=through.getValue();refresh.setDisable(true);error.setText("");
            app.run(() -> app.store().statistics(begin,end),stats -> {refresh.setDisable(false);if(java.util.Objects.equals(from.getValue(),begin)&&java.util.Objects.equals(through.getValue(),end)){render(report,stats,begin,end);}},message -> {refresh.setDisable(false);error.setText(message);});
        };
        period.setOnAction(event -> {String choice=period.getValue();if(choice.equals("Personnalisé")){return;}through.setValue(today);from.setValue(choice.equals("Aujourd'hui")?today:choice.equals("7 derniers jours")?today.minusDays(6):choice.equals("30 derniers jours")?today.minusDays(29):today.withDayOfMonth(1));load.run();});
        refresh.setOnAction(event -> load.run());page.getChildren().addAll(UI.row(8,period,UI.label("Du","small"),from,UI.label("au","small"),through,UI.spacer(),refresh),error,report);load.run();
    }
    private void render(VBox report,HotelStore.BusinessStats stats,LocalDate from,LocalDate through){
        Row t=stats.totals();
        HBox totals = UI.columns(
                metric("Ventes de séjours", UI.money(t.number("sales")), "Nuits et repas réservés sur la période, hors annulations"),
                metric("Encaissements nets", UI.money(t.number("net_receipts")), "Paiements moins remboursements"),
                metric("Dépenses", UI.money(t.number("expenses")), null),
                metric("Flux de trésorerie", UI.money(t.number("cash_flow")), "Encaissements nets moins dépenses"));
        totals.getStyleClass().add("metric-strip");
        VBox debt=UI.panel(UI.label("Soldes ouverts","section-title"));
        if(stats.balances().isEmpty()){debt.getChildren().add(UI.muted("Aucun montant à encaisser"));}
        for(Row balance:stats.balances()){
            debt.getChildren().add(UI.box(6,UI.row(8,UI.button(balance.text("label"),"quiet",() -> ReservationsPage.open(app,balance.number("id"))),UI.spacer(),UI.label(UI.money(balance.number("balance")),"strong")),UI.label(ReservationsPage.reference(balance.number("id")),"small")));
        }
        VBox chart=UI.panel(UI.row(12,UI.label("Encaissements et dépenses","section-title"),UI.spacer(),UI.label("DZD","small")),chart(stats.daily(),from,through));
        GridPane analysis = new GridPane();
        ColumnConstraints trend = new ColumnConstraints(); trend.setPercentWidth(65);
        ColumnConstraints balances = new ColumnConstraints(); balances.setPercentWidth(35);
        analysis.getColumnConstraints().addAll(trend, balances);
        analysis.addRow(0, chart, debt); GridPane.setMargin(chart, new javafx.geometry.Insets(0,16,0,0));
        report.getChildren().setAll(totals, analysis);
    }
    private Node chart(List<Row> rows,LocalDate from,LocalDate through){
        long days=ChronoUnit.DAYS.between(from,through);NumberAxis x=new NumberAxis(0,Math.max(1,days),Math.max(1,days/6)),y=new NumberAxis();
        x.setTickLabelFormatter(new StringConverter<>(){@Override public String toString(Number value){return from.plusDays(value.longValue()).format(DateTimeFormatter.ofPattern("dd MMM",Locale.FRENCH));}@Override public Number fromString(String value){return 0;}});
        y.setTickLabelFormatter(new StringConverter<>() {
            private final java.text.NumberFormat format = java.text.NumberFormat.getNumberInstance(Locale.FRANCE);
            @Override public String toString(Number value) { return format.format(value); }
            @Override public Number fromString(String value) { throw new UnsupportedOperationException(); }
        });
        LineChart<Number,Number> chart=new LineChart<>(x,y);chart.setAnimated(false);chart.setCreateSymbols(days<15);chart.setPrefHeight(280);chart.setMinWidth(200);x.setMinorTickVisible(false);y.setMinorTickVisible(false);chart.setLegendSide(javafx.geometry.Side.BOTTOM);
        XYChart.Series<Number,Number> receipts=new XYChart.Series<>(),expenses=new XYChart.Series<>();receipts.setName("Encaissements nets");expenses.setName("Dépenses");
        for(int i=0;i<rows.size();i++){Row row=rows.get(i);receipts.getData().add(new XYChart.Data<>(i,row.number("receipts")/100.0));expenses.getData().add(new XYChart.Data<>(i,row.number("expenses")/100.0));}
        chart.getData().add(receipts);chart.getData().add(expenses);return chart;
    }
    private void upcoming(VBox page){
        TableView<Row> table=UI.table();UI.column(table,"Titulaire",220,r -> r.text("label"));UI.column(table,"Arrivée",160,r -> UI.date(r.text("arrival")));UI.column(table,"Chambres",100,r -> r.text("rooms"));UI.statusColumn(table);UI.openRows(table,r -> ReservationsPage.open(app,r.number("id")));
        page.getChildren().add(UI.panel(UI.label("Séjours à accueillir","section-title"),table));
        app.run(app.store()::bookings,rows -> table.setItems(FXCollections.observableArrayList(rows.stream().filter(r -> r.text("status").equals("RESERVED")||r.text("status").equals("CHECKED_IN")).sorted(Comparator.comparing(r -> r.text("arrival"))).toList())));
    }
    private VBox metric(String text,String value,String explanation){Label title=UI.label(text,"muted");Label figure=UI.label(value,"stat-value");figure.setWrapText(true);VBox metric=UI.box(8,title,figure);metric.getStyleClass().add("metric");HBox.setHgrow(metric,Priority.ALWAYS);metric.setPrefWidth(1);if(explanation!=null){Tooltip tip=new Tooltip(explanation);tip.setWrapText(true);tip.setMaxWidth(340);Tooltip.install(metric,tip);}return metric;}
}
