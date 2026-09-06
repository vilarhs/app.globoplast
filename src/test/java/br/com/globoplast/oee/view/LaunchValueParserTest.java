package br.com.globoplast.oee.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaunchValueParserTest {
    @Test
    void parsesDecimalAndSimpleSums() {
        assertEquals(12.5, LaunchValueParser.decimal("12,5", 0));
        assertEquals(7, LaunchValueParser.sumInt("2+ 5"));
        assertEquals(3.5, LaunchValueParser.scrapKg("1,5 + 2"));
        assertEquals(9, LaunchValueParser.decimal("x", 9));
    }

    @Test
    void parsesHoursAsMinutesOrDecimalHours() {
        assertEquals(1.5, LaunchValueParser.hours("1:30", 0), 0.0001);
        assertEquals(1.5, LaunchValueParser.hours("1.30", 0), 0.0001);
        assertEquals(2 + 25.0 / 60.0, LaunchValueParser.hours("2,25", 0), 0.0001);
        assertEquals(4, LaunchValueParser.hours("x", 4), 0.0001);
    }
}
