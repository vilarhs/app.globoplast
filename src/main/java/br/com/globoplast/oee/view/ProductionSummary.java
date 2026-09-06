package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.util.Norm;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ProductionSummary {
    private ProductionSummary() { }

    static List<LaunchRecord> rowsForShifts(List<LaunchRecord> source, Set<String> shifts) {
        if (source == null || source.isEmpty()) return List.of();
        Set<String> selected = normalizedShifts(shifts);
        if (selected.isEmpty()) return source;
        return source.stream()
                .filter(record -> selected.stream().anyMatch(shift -> hasData(record, shift)))
                .map(record -> recordForShifts(record, selected))
                .toList();
    }

    static List<LaunchRecord> daily(List<LaunchRecord> rows) {
        Map<String, List<LaunchRecord>> groups = groupByMachineAndSector(rows);
        List<LaunchRecord> result = new ArrayList<>();
        for (List<LaunchRecord> group : groups.values()) {
            List<LaunchRecord> sorted = group.stream()
                    .sorted(Comparator.comparingLong(LaunchRecord::getId)).toList();
            LaunchRecord summary = baseSummary(sorted);
            summary.setId(sorted.stream().mapToLong(LaunchRecord::getId).max().orElse(0));
            summary.setDate(sorted.get(0).getDate());
            summary.setProduct(combineUnique(sorted.stream().map(LaunchRecord::getProduct).toList(), " / "));
            summary.setOrderNumber(combineOrders(sorted.stream().map(LaunchRecord::getOrderNumber).toList()));
            summary.setScheduledHours(sorted.stream().mapToDouble(LaunchRecord::getScheduledHours).sum());
            summary.setShiftA(sorted.stream().mapToInt(LaunchRecord::getShiftA).sum());
            summary.setShiftB(sorted.stream().mapToInt(LaunchRecord::getShiftB).sum());
            summary.setShiftC(sorted.stream().mapToInt(LaunchRecord::getShiftC).sum());
            summary.setSetupHours(sorted.stream().mapToDouble(LaunchRecord::getSetupHours).sum());
            summary.setBreakdownHours(sorted.stream().mapToDouble(LaunchRecord::getBreakdownHours).sum());
            summary.setScrapPct(scrapPct(summary));
            summary.setOeePct(average(sorted, Metric.OEE));
            summary.setAvailabilityPct(average(sorted, Metric.AVAILABILITY));
            summary.setPerformancePct(average(sorted, Metric.PERFORMANCE));
            summary.setQualityPct(average(sorted, Metric.QUALITY));
            summary.setLaunchCount(sorted.size());
            result.add(summary);
        }
        result.sort(Comparator.comparing(LaunchRecord::getMachine, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    static List<LaunchRecord> monthly(List<LaunchRecord> rows) {
        Map<String, List<LaunchRecord>> groups = groupByMachineAndSector(rows);
        List<LaunchRecord> result = new ArrayList<>();
        for (List<LaunchRecord> group : groups.values()) {
            LaunchRecord summary = baseSummary(group);
            Map<LocalDate, LaunchRecord> dailyIndicators = new LinkedHashMap<>();
            for (LaunchRecord record : group) dailyIndicators.putIfAbsent(record.getDate(), record);
            List<LaunchRecord> indicators = new ArrayList<>(dailyIndicators.values());
            summary.setOeePct(average(indicators, Metric.OEE));
            summary.setAvailabilityPct(average(indicators, Metric.AVAILABILITY));
            summary.setPerformancePct(average(indicators, Metric.PERFORMANCE));
            summary.setQualityPct(average(indicators, Metric.QUALITY));
            result.add(summary);
        }
        result.sort(Comparator.comparingDouble(LaunchRecord::getOeePct).reversed());
        return result;
    }

    static long capacityTargetByMachineDay(List<LaunchRecord> rows) {
        Map<String, Integer> capacities = new LinkedHashMap<>();
        for (LaunchRecord record : rows) {
            if (record.getDate() == null || record.getMachine() == null) continue;
            String key = record.getDate() + "¦" + record.getMachine();
            capacities.merge(key, Math.max(0, record.getCapacity24h()), Math::max);
        }
        return capacities.values().stream().mapToLong(Integer::longValue).sum();
    }

    static double average(List<LaunchRecord> rows, Metric metric) {
        return Norm.round(rows.stream().mapToDouble(record -> switch (metric) {
            case AVAILABILITY -> record.getAvailabilityPct();
            case PERFORMANCE -> record.getPerformancePct();
            case QUALITY -> record.getQualityPct();
            case OEE -> record.getOeePct();
        }).average().orElse(0), 2);
    }

    enum Metric { OEE, AVAILABILITY, PERFORMANCE, QUALITY }

    private static Map<String, List<LaunchRecord>> groupByMachineAndSector(List<LaunchRecord> rows) {
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream().collect(Collectors.groupingBy(
                record -> record.getMachine() + "¦" + record.getSector(),
                LinkedHashMap::new, Collectors.toList()));
    }

    private static LaunchRecord baseSummary(List<LaunchRecord> group) {
        LaunchRecord summary = new LaunchRecord();
        summary.setMachine(group.get(0).getMachine());
        summary.setSector(group.get(0).getSector());
        summary.setCapacity24h(group.stream().mapToInt(LaunchRecord::getCapacity24h).max().orElse(0));
        summary.setTotalProduced(group.stream().mapToInt(LaunchRecord::getTotalProduced).sum());
        summary.setScrapTotalKg(Norm.round(group.stream().mapToDouble(LaunchRecord::getScrapTotalKg).sum(), 3));
        summary.setScrapTotalPcs(group.stream().mapToInt(LaunchRecord::getScrapTotalPcs).sum());
        summary.setChangeovers(group.stream().mapToInt(LaunchRecord::getChangeovers).sum());
        summary.setProblem(combineObservations(group));
        return summary;
    }

    private static Set<String> normalizedShifts(Set<String> shifts) {
        Set<String> selected = new LinkedHashSet<>();
        if (shifts == null) return selected;
        for (String shift : List.of("A", "B", "C")) {
            if (shifts.stream().anyMatch(value -> value != null && shift.equalsIgnoreCase(value))) selected.add(shift);
        }
        return selected;
    }

    private static boolean hasData(LaunchRecord record, String shift) {
        return switch (shift) {
            case "A" -> record.getShiftA() > 0 || record.getScrapAKg() > 0;
            case "B" -> record.getShiftB() > 0 || record.getScrapBKg() > 0;
            case "C" -> record.getShiftC() > 0 || record.getScrapCKg() > 0;
            default -> false;
        };
    }

    private static LaunchRecord recordForShifts(LaunchRecord original, Set<String> shifts) {
        LaunchRecord record = original.copy();
        record.setShiftA(shifts.contains("A") ? Math.max(0, original.getShiftA()) : 0);
        record.setShiftB(shifts.contains("B") ? Math.max(0, original.getShiftB()) : 0);
        record.setShiftC(shifts.contains("C") ? Math.max(0, original.getShiftC()) : 0);
        record.setTotalProduced(record.getShiftA() + record.getShiftB() + record.getShiftC());
        record.setScrapAKg(shifts.contains("A") ? Math.max(0, original.getScrapAKg()) : 0);
        record.setScrapBKg(shifts.contains("B") ? Math.max(0, original.getScrapBKg()) : 0);
        record.setScrapCKg(shifts.contains("C") ? Math.max(0, original.getScrapCKg()) : 0);
        record.setScrapTotalKg(Norm.round(record.getScrapAKg() + record.getScrapBKg() + record.getScrapCKg(), 3));
        int scrapPieces = 0;
        if (record.getUnitWeightG() > 0) {
            scrapPieces = (int) Math.round(record.getScrapTotalKg() * 1000.0 / record.getUnitWeightG());
        } else if (original.getScrapTotalKg() > 0 && original.getScrapTotalPcs() > 0) {
            scrapPieces = (int) Math.round(original.getScrapTotalPcs() * record.getScrapTotalKg() / original.getScrapTotalKg());
        }
        record.setScrapTotalPcs(Math.max(0, scrapPieces));
        record.setScrapPct(scrapPct(record));
        return record;
    }

    private static double scrapPct(LaunchRecord record) {
        int processed = Math.max(0, record.getTotalProduced()) + Math.max(0, record.getScrapTotalPcs());
        return processed > 0 ? Norm.round(record.getScrapTotalPcs() * 100.0 / processed, 2) : 0;
    }

    private static String combineUnique(List<String> values, String separator) {
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            String clean = Norm.text(value);
            if (!clean.isBlank() && !clean.equals("-")) unique.add(clean);
        }
        return String.join(separator, unique);
    }

    private static String combineOrders(List<String> values) {
        Set<String> orders = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) continue;
            for (String order : value.trim().split("\\s*[/;|]\\s*")) {
                if (!order.isBlank()) orders.add(order.trim());
            }
        }
        return String.join("/", orders);
    }

    private static String combineObservations(List<LaunchRecord> rows) {
        Set<String> observations = new LinkedHashSet<>();
        for (LaunchRecord row : rows) {
            String value = Norm.text(row.getProblem());
            if (!value.isBlank() && !value.equals("-") && !value.equalsIgnoreCase("Nenhum")) observations.add(value);
        }
        return String.join(" / ", observations);
    }
}
