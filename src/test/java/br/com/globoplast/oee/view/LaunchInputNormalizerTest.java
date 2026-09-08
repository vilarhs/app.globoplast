package br.com.globoplast.oee.view;

import org.junit.jupiter.api.Test;
import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.service.CatalogService;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaunchInputNormalizerTest {
    @Test
    void normalizesMachineNamesAndNumericTokens() {
        assertEquals("extrusora8", LaunchInputNormalizer.machineKey(" Extrusora 08 "));
        assertEquals("coltpa2", LaunchInputNormalizer.machineKey("COL TPA 02"));
        assertEquals("coldetampa2", LaunchInputNormalizer.legacyMachineKey("COL TPA 02"));
    }

    @Test
    void prefersCurrentCatalogNameBeforeHistoricalAlias() {
        Machine current = new Machine(1, "COL TPA 01", 50_000, "COL DE TAMPA");
        Machine historical = new Machine(2, "COL DE TAMPA 01", 50_000, "COL DE TAMPA");
        CatalogService catalog = new CatalogService(null, null) {
            @Override public Map<String, Machine> machineMap() {
                Map<String, Machine> machines = new LinkedHashMap<>();
                machines.put(current.name(), current);
                machines.put(historical.name(), historical);
                return machines;
            }
            @Override public java.util.List<Machine> machines() { return java.util.List.of(current, historical); }
        };
        assertEquals(current, CatalogMachineResolver.find(catalog, " col tpa 01 "));
        assertEquals(historical, CatalogMachineResolver.find(catalog, "COL DE TAMPA 01"));
    }

    @Test
    void cleansSentinelValuesAndUppercasesProducts() {
        assertEquals("", LaunchInputNormalizer.clean(" Nenhum "));
        assertEquals("", LaunchInputNormalizer.clean("NaN"));
        assertEquals("771502", LaunchInputNormalizer.product(" 771502 ", Locale.forLanguageTag("pt-BR"), "Não informado"));
        assertEquals("Não informado", LaunchInputNormalizer.product(" ", Locale.forLanguageTag("pt-BR"), "Não informado"));
    }
}
