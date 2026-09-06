package br.com.globoplast.oee.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaunchDateFormatTest {
    @Test
    void formatsTrashDatesAndLeavesInvalidValuesVisible() {
        assertEquals("—", LaunchDateFormat.trash(""));
        assertEquals("24/08/2026 15:30", LaunchDateFormat.trash("2026-08-24T15:30:00-03:00"));
        assertEquals("valor inválido", LaunchDateFormat.trash("valor inválido"));
    }
}
