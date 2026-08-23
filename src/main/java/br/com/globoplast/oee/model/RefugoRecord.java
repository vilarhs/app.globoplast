package br.com.globoplast.oee.model;

import java.time.LocalDate;

public record RefugoRecord(
        long erpId,
        String analysisId,
        LocalDate productiveDate,
        LocalDate rawDate,
        String orderNumber,
        double plannedQty,
        String machine,
        String product,
        String description,
        String client,
        String shift,
        String operator,
        double scrapKg,
        String motive,
        double unitWeightG,
        double itemCount,
        String sector,
        String firstDetectedAt
) {}
