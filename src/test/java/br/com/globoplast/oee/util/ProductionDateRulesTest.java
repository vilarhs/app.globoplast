package br.com.globoplast.oee.util;

import br.com.globoplast.oee.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionDateRulesTest {
    private final YearMonth lastMonth = YearMonth.now(AppConfig.ZONE).minusMonths(1);

    @Test
    void shiftCAndAnyShiftBeforeSixBelongToPreviousProductiveDay() {
        LocalDate rawDate = lastMonth.atDay(16);

        assertEquals(rawDate.minusDays(1), Norm.productiveDate(rawDate, "C"));
        assertEquals(rawDate.minusDays(1), Norm.productiveScrapDate(rawDate, "A", rawDate + "T05:59:59-03:00"));
        assertEquals(rawDate.minusDays(1), Norm.productiveScrapDate(rawDate, "B", rawDate + "T05:00:00-03:00"));
        assertEquals(rawDate, Norm.productiveScrapDate(rawDate, "A", rawDate + "T06:00:00-03:00"));
    }

    @Test
    void firstDayBeforeSixClosesInPreviousMonth() {
        LocalDate firstDay = lastMonth.plusMonths(1).atDay(1);

        assertEquals(lastMonth.atEndOfMonth(),
                Norm.productiveScrapDate(firstDay, "A", firstDay + "T05:30:00-03:00"));
        assertEquals(firstDay,
                Norm.productiveScrapDate(firstDay, "A", firstDay + "T06:00:00-03:00"));
    }
}
