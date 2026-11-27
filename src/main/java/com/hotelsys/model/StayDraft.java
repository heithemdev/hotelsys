package com.hotelsys.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record StayDraft(long roomId, LocalDate arrival, LocalDate departure, List<Long> guests, Set<String> meals, LocalTime arrivalTime, LocalTime departureTime) {
    public StayDraft(long roomId, LocalDate arrival, LocalDate departure, List<Long> guests, Set<String> meals) {
        this(roomId, arrival, departure, guests, meals, LocalTime.of(14,0), LocalTime.of(11,0));
    }
    public StayDraft { guests = List.copyOf(guests); meals = Set.copyOf(meals); }
    public LocalDateTime starts() { return arrival.atTime(arrivalTime); }
    public LocalDateTime ends() { return departure.atTime(departureTime); }
}
