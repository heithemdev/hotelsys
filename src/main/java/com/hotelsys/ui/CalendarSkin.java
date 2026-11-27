package com.hotelsys.ui;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Popup;

public final class CalendarSkin extends SkinBase<DatePicker> {
    private final HBox field;
    private final Popup popup=new Popup();
    private final VBox panel=new VBox(12);
    private YearMonth month;
    public CalendarSkin(DatePicker picker){
        super(picker);
        TextField editor=picker.getEditor();
        Runnable sync=() -> {if(picker.getValue()!=null){String text=picker.getConverter().toString(picker.getValue());if(!text.equals(editor.getText())){editor.setText(text);}}};sync.run();
        registerChangeListener(picker.valueProperty(),o -> sync.run());
        editor.setEditable(picker.isEditable());registerChangeListener(picker.editableProperty(),o -> editor.setEditable(picker.isEditable()));
        editor.setPromptText("jj/mm/aaaa");HBox.setHgrow(editor,Priority.ALWAYS);
        Button open=new Button();open.setGraphic(Icons.get("calendar"));open.getStyleClass().add("calendar-open");open.setAccessibleText("Ouvrir le calendrier");open.setOnAction(e -> {if(picker.isShowing()){picker.hide();}else{picker.show();}});
        field=UI.row(0,editor,open);getChildren().add(field);
        field.getStyleClass().add("date-field");
        panel.getStyleClass().add("date-popup");panel.getStylesheets().add(UI.class.getResource("/css/app.css").toExternalForm());popup.getContent().add(panel);popup.setAutoHide(true);popup.setHideOnEscape(true);popup.setOnHidden(e -> picker.hide());
        registerChangeListener(picker.showingProperty(),o -> {
            if(picker.isShowing()){
                LocalDate selected = picker.getValue();
                LocalDate visible = selected == null ? LocalDate.now(com.hotelsys.service.HotelStore.ZONE) : selected;
                if (!UI.dateAllowed(picker, visible)) { visible = UI.minimumDate(picker); }
                month = YearMonth.from(visible);
                render(); var bounds=picker.localToScreen(picker.getBoundsInLocal());
                if(bounds!=null){popup.show(picker,bounds.getMinX(),bounds.getMaxY()+6);Motion.enter(panel);}
            }else{popup.hide();}
        });
    }
    private void render(){
        Button previous=UI.button("‹","calendar-nav",() -> {month=month.minusMonths(1);render();});previous.setAccessibleText("Mois précédent");
        Button next=UI.button("›","calendar-nav",() -> {month=month.plusMonths(1);render();});next.setAccessibleText("Mois suivant");
        ComboBox<String> months=new ComboBox<>();for(int i=1;i<=12;i++){months.getItems().add(Month.of(i).getDisplayName(java.time.format.TextStyle.FULL,Locale.FRENCH));}months.getSelectionModel().select(month.getMonthValue()-1);months.setPrefWidth(148);
        ComboBox<Integer> years=new ComboBox<>();for(int y=1900;y<=LocalDate.now(com.hotelsys.service.HotelStore.ZONE).getYear()+20;y++){years.getItems().add(y);}years.setValue(month.getYear());years.setPrefWidth(96);
        months.setOnAction(e -> {month=month.withMonth(months.getSelectionModel().getSelectedIndex()+1);render();});years.setOnAction(e -> {month=month.withYear(years.getValue());render();});
        GridPane grid=new GridPane();grid.setHgap(4);grid.setVgap(4);
        String[] days={"L","M","M","J","V","S","D"};for(int i=0;i<7;i++){Label l=UI.label(days[i],"calendar-weekday");l.setMinWidth(36);l.setAlignment(javafx.geometry.Pos.CENTER);grid.add(l,i,0);}
        LocalDate first=month.atDay(1);int offset=first.getDayOfWeek().getValue()-1;
        for(int i=0;i<42;i++){
            LocalDate date=first.minusDays(offset).plusDays(i);Button day=UI.button(Integer.toString(date.getDayOfMonth()),"calendar-day",() -> {getSkinnable().setValue(date);getSkinnable().hide();});
            day.setDisable(!UI.dateAllowed(getSkinnable(), date));
            day.setAccessibleText(date.format(DateTimeFormatter.ofPattern("dd MMMM uuuu",Locale.FRENCH)));
            if(date.getMonth()!=month.getMonth()){day.getStyleClass().add("outside");}
            if(date.equals(getSkinnable().getValue())){day.getStyleClass().add("chosen");}
            if(date.equals(LocalDate.now(com.hotelsys.service.HotelStore.ZONE))){day.getStyleClass().add("today");}grid.add(day,i%7,i/7+1);
        }
        LocalDate currentDate = LocalDate.now(com.hotelsys.service.HotelStore.ZONE);
        Button today = UI.button("Aujourd'hui", "quiet", () -> { getSkinnable().setValue(currentDate); getSkinnable().hide(); });
        today.setDisable(!UI.dateAllowed(getSkinnable(), currentDate));
        panel.getChildren().setAll(UI.row(4,previous,months,years,next),grid,today);
    }
    @Override protected void layoutChildren(double x,double y,double w,double h){field.resizeRelocate(x,y,w,h);}
    @Override protected double computePrefWidth(double h,double t,double r,double b,double l){return 172+l+r;}
    @Override protected double computePrefHeight(double w,double t,double r,double b,double l){return 44+t+b;}
    @Override public void dispose(){popup.hide();super.dispose();}
}
