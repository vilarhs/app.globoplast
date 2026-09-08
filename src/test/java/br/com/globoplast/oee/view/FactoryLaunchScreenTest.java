package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.LaunchRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FactoryLaunchScreenTest {
    @Test
    void expandsOnlyFilledShiftsForDailySummary() {
        LaunchRecord record = new LaunchRecord();
        record.setOrderNumber("59501");
        record.setProduct("7764040068");
        record.setShiftA(120);
        record.setShiftB(0);
        record.setShiftC(80);

        List<FactoryLaunchScreen.FactoryDayLine> rows = FactoryLaunchScreen.daySummaryRows(List.of(record));
        assertEquals(List.of(
                new FactoryLaunchScreen.FactoryDayLine("59501", "7764040068", "A", 120),
                new FactoryLaunchScreen.FactoryDayLine("59501", "7764040068", "C", 80)), rows);
    }
}
