package br.com.globoplast.oee.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaunchDisplayValuesTest {
    @Test
    void formatsNumbersAccordingToLanguageAndHidesZero() {
        assertEquals("", LaunchDisplayValues.number(0, "pt-BR"));
        assertEquals("12,5", LaunchDisplayValues.number(12.5, "pt-BR"));
        assertEquals("12.5", LaunchDisplayValues.number(12.5, "en-US"));
        assertEquals("24", LaunchDisplayValues.integer(24));
        assertEquals("", LaunchDisplayValues.integer(0));
    }
}
