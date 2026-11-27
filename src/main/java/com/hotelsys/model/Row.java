package com.hotelsys.model;

import java.time.LocalDate;
import java.util.Map;

/** Une ligne de résultat en lecture ; les écritures passent par HotelStore. */
public record Row(Map<String, Object> values) {
    public Row { values = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(values)); }
    public String text(String key) { Object value = values.get(key); return value == null ? "" : value.toString(); }
    public long number(String key) { Object value = values.get(key); return value == null ? 0 : ((Number) value).longValue(); }
    public boolean flag(String key) { return number(key) != 0; }
    public LocalDate date(String key) { return LocalDate.parse(text(key)); }
    public boolean has(String key) { return values.get(key) != null; }
    @Override public String toString() { return text("label"); }
}
