package br.com.globoplast.oee.view;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterActivityTest {
    @Test
    void detectsChangedDatesAndSelectedCollections() {
        LocalDate day = LocalDate.of(2026, 9, 6);
        assertFalse(FilterActivity.dateChanged(day, day, new LocalDate[]{day, day}));
        assertTrue(FilterActivity.dateChanged(day.plusDays(1), day, new LocalDate[]{day, day}));
        assertFalse(FilterActivity.any(List.of(), List.of()));
        assertTrue(FilterActivity.any(List.of(), List.of("Extrusão")));
    }
}
