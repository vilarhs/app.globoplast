package br.com.globoplast.oee.view;

final class LaunchValueParser {
    private LaunchValueParser() {}

    static double decimal(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try { return Double.parseDouble(raw.trim().replace(',', '.')); }
        catch (RuntimeException ignored) { return fallback; }
    }

    static int sumInt(String raw) {
        return (int) sum(raw);
    }

    static double scrapKg(String raw) {
        return sum(raw);
    }

    private static double sum(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        double total = 0;
        try {
            for (String part : raw.split("\\+")) {
                String value = part.trim().replace(" ", "");
                if (!value.isBlank()) total += Double.parseDouble(value.replace(',', '.'));
            }
            return total;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    static double hours(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String value = raw.trim().replace(',', '.');
        try {
            if (value.contains(":")) {
                String[] parts = value.split(":");
                double hours = Double.parseDouble(parts[0]);
                double minutes = parts.length > 1 ? Double.parseDouble(parts[1]) : 0;
                return hours + minutes / 60.0;
            }
            if (value.contains(".")) {
                String[] parts = value.split("\\.");
                if (parts.length == 2) {
                    double hours = Double.parseDouble(parts[0]);
                    String minuteText = parts[1].length() == 1
                            ? parts[1] + "0" : parts[1].substring(0, Math.min(2, parts[1].length()));
                    double minutes = Double.parseDouble(minuteText);
                    if (minutes < 60) return hours + minutes / 60.0;
                }
            }
            return Double.parseDouble(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
