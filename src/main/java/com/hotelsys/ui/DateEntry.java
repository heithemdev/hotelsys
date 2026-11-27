package com.hotelsys.ui;

import java.time.LocalDate;
import java.time.format.*;

public final class DateEntry {
    private DateEntry() { }
    private static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    public static String formatDigits(String input){
        String digits=input.replace("/","");
        if(!digits.matches("[0-9]{0,8}")){return null;}
        if(digits.length()>=3 && digits.charAt(2)>'1' && digits.length()<8){digits=digits.substring(0,2)+"0"+digits.substring(2);}
        if(digits.length()>8){return null;}
        if(digits.length()>4){return digits.substring(0,2)+"/"+digits.substring(2,4)+"/"+digits.substring(4);}
        if(digits.length()>2){return digits.substring(0,2)+"/"+digits.substring(2);}
        return digits;
    }
    public static LocalDate parse(String text){try{return LocalDate.parse(text,FORMAT);}catch(DateTimeParseException invalid){return null;}}
}
