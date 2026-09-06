package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.model.Machine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaunchFilterOptionsTest {
    @Test
    void filtersMachinesBySectorAndSortsWithoutDuplicates() {
        List<Machine> machines = List.of(
                new Machine(1, "ZETA", 1, "Extrusão"),
                new Machine(2, "ALFA", 1, "Impressão"),
                new Machine(3, "ZETA", 1, "Extrusão"));

        assertEquals(List.of("ZETA"), LaunchFilterOptions.machines(machines, List.of("extrusão")));
        assertEquals(List.of("ALFA", "ZETA"), LaunchFilterOptions.machines(machines, List.of()));
    }

    @Test
    void extractsDistinctNonBlankClientsInOrder() {
        LaunchRecord first = new LaunchRecord();
        first.setClientErp("Beta");
        LaunchRecord second = new LaunchRecord();
        second.setClientErp("Alpha");
        LaunchRecord duplicate = new LaunchRecord();
        duplicate.setClientErp("Beta");
        LaunchRecord blank = new LaunchRecord();

        assertEquals(List.of("Alpha", "Beta"),
                LaunchFilterOptions.clients(List.of(first, second, duplicate, blank)));
    }
}
