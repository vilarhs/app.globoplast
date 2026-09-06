package br.com.globoplast.oee.view;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaunchInputNormalizerTest {
    @Test
    void normalizesMachineNamesAndNumericTokens() {
        assertEquals("extrusora8", LaunchInputNormalizer.machineKey(" Extrusora 08 "));
        assertEquals("coldetampa2", LaunchInputNormalizer.machineKey("COL TPA 02"));
    }

    @Test
    void cleansSentinelValuesAndUppercasesProducts() {
        assertEquals("", LaunchInputNormalizer.clean(" Nenhum "));
        assertEquals("", LaunchInputNormalizer.clean("NaN"));
        assertEquals("771502", LaunchInputNormalizer.product(" 771502 ", Locale.forLanguageTag("pt-BR"), "Não informado"));
        assertEquals("Não informado", LaunchInputNormalizer.product(" ", Locale.forLanguageTag("pt-BR"), "Não informado"));
    }
}
