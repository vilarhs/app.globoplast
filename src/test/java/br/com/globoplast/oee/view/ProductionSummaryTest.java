package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.LaunchRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionSummaryTest {
    @Test
    void selectedShiftKeepsOnlyItsProductionAndScrap() {
        LaunchRecord source = record(LocalDate.of(2026, 8, 24), "COL TPA 02", "Colocação de Tampa", 100, 80);
        source.setShiftA(40);
        source.setShiftB(60);
        source.setScrapAKg(1);
        source.setScrapBKg(2);
        source.setScrapTotalKg(3);
        source.setScrapTotalPcs(30);
        source.setUnitWeightG(100);

        LaunchRecord filtered = ProductionSummary.rowsForShifts(List.of(source), Set.of("B")).get(0);

        assertEquals(60, filtered.getTotalProduced());
        assertEquals(2, filtered.getScrapTotalKg());
        assertEquals(20, filtered.getScrapTotalPcs());
        assertEquals(25, filtered.getScrapPct());
        assertEquals(80, source.getTotalProduced());
    }

    @Test
    void summariesConsolidateMachineAndUseOneCapacityPerDay() {
        LaunchRecord first = record(LocalDate.of(2026, 8, 24), "IMPRESSORA 01", "Impressão", 100, 80);
        first.setId(1);
        first.setOrderNumber("59001");
        first.setProduct("7700000001");
        first.setScrapTotalPcs(20);
        first.setOeePct(60);
        LaunchRecord second = record(LocalDate.of(2026, 8, 24), "IMPRESSORA 01", "Impressão", 100, 70);
        second.setId(2);
        second.setOrderNumber("59002");
        second.setProduct("7700000002");
        second.setScrapTotalPcs(10);
        second.setOeePct(80);
        LaunchRecord nextDay = record(LocalDate.of(2026, 8, 25), "IMPRESSORA 01", "Impressão", 120, 90);
        nextDay.setOeePct(50);

        LaunchRecord daily = ProductionSummary.daily(List.of(first, second)).get(0);
        LaunchRecord monthly = ProductionSummary.monthly(List.of(first, second, nextDay)).get(0);

        assertEquals(150, daily.getTotalProduced());
        assertEquals(30, daily.getScrapTotalPcs());
        assertEquals("59001/59002", daily.getOrderNumber());
        assertEquals(2, daily.getLaunchCount());
        assertEquals(70, daily.getOeePct());
        assertEquals(55, monthly.getOeePct());
        assertEquals(220, ProductionSummary.capacityTargetByMachineDay(List.of(first, second, nextDay)));
    }

    private static LaunchRecord record(LocalDate date, String machine, String sector, int capacity, int produced) {
        LaunchRecord record = new LaunchRecord();
        record.setDate(date);
        record.setMachine(machine);
        record.setSector(sector);
        record.setCapacity24h(capacity);
        record.setTotalProduced(produced);
        return record;
    }
}
