package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.RefugoRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScrapFilterOptionsTest {
    @Test
    void returnsDistinctSortedNonBlankValues() {
        RefugoRecord beta = record("Beta");
        RefugoRecord alpha = record("Alpha");
        RefugoRecord duplicate = record("Beta");
        RefugoRecord blank = record(" ");

        assertEquals(List.of("Alpha", "Beta"),
                ScrapFilterOptions.values(List.of(beta, alpha, duplicate, blank), RefugoRecord::client));
    }

    @Test
    void handlesNullOrEmptyRows() {
        assertEquals(List.of(), ScrapFilterOptions.values(null, RefugoRecord::client));
        assertEquals(List.of(), ScrapFilterOptions.values(List.of(), RefugoRecord::client));
    }

    private static RefugoRecord record(String client) {
        return new RefugoRecord(1, "a", LocalDate.now(), LocalDate.now(), "1", 0,
                "M", "P", "D", client, "A", "O", 0, "M", 0, 0, "S", "");
    }
}
