package br.com.globoplast.oee.view;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterSelectionsTest {
    @Test
    void replacesWithNonBlankDistinctValuesAndCopiesSafely() {
        Set<String> selected = new LinkedHashSet<>(List.of("old"));
        FilterSelections.replace(selected, List.of(" A ", "", "A", "B"));
        assertEquals(new LinkedHashSet<>(List.of(" A ", "A", "B")), selected);
        assertEquals(new LinkedHashSet<>(List.of("A", "B")), FilterSelections.copy(List.of("A", "B")));
    }
}
