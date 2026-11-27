package com.hotelsys.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record StayDraft(long roomId, LocalDate arrival, LocalDate departure, List<Long> guests, Set<String> meals) {
    public StayDraft { guests = List.copyOf(guests); meals = Set.copyOf(meals); }
}
