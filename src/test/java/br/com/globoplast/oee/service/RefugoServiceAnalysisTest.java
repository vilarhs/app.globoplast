package br.com.globoplast.oee.service;

import br.com.globoplast.oee.model.RefugoRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefugoServiceAnalysisTest {
    @Test
    void aggregatesUsingStableAnalysisKeys() {
        RefugoService service = new RefugoService(null);
        RefugoRecord first = record(1, "Extrusão", "77001", "FALHA", 2.5, LocalDate.of(2026, 8, 1));
        RefugoRecord second = record(2, "extrusão", "77001", "FALHA", 1.5, LocalDate.of(2026, 8, 1));
        RefugoRecord third = record(3, "Impressão", "77101", "FALHA", 5, LocalDate.of(2026, 9, 1));

        Map<String, Double> sectors = service.aggregate(List.of(first, second, third), "Setor");

        assertEquals(List.of("IMPRESSÃO", "EXTRUSÃO"), sectors.keySet().stream().toList());
        assertEquals(4.0, sectors.get("EXTRUSÃO"));
        assertEquals("2026-08", service.analysisKey(first, "Comparativo Mensal"));
        assertTrue(service.matches(second, "Setor", "EXTRUSÃO"));
        assertTrue(service.analysisKey(first, "Motivo").contains("FALHA"));
    }

    private RefugoRecord record(long id, String sector, String product, String motive,
                                double kilograms, LocalDate date) {
        return new RefugoRecord(id, Long.toString(id), date, date, "59001", 1000,
                "MÁQUINA", product, "PRODUTO", "CLIENTE", "A", "OPERADOR",
                kilograms, motive, 10, 1, sector, "");
    }
}
