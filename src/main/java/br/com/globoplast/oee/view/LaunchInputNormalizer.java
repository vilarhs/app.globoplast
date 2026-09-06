package br.com.globoplast.oee.view;

import br.com.globoplast.oee.util.Norm;

import java.util.Locale;

final class LaunchInputNormalizer {
    private LaunchInputNormalizer() {}

    static String machineKey(String value) {
        String normalized = Norm.machine(value == null ? "" : value);
        String folded = Norm.fold(normalized).replaceAll("[^a-z0-9]+", " ").trim();
        if (folded.isBlank()) return "";
        StringBuilder key = new StringBuilder();
        for (String token : folded.split("\\s+")) {
            if (token.matches("\\d+")) {
                try { key.append(Integer.parseInt(token)); }
                catch (NumberFormatException ignored) { key.append(token); }
            } else key.append(token);
        }
        return key.toString();
    }

    static String product(String raw, Locale locale, String missingLabel) {
        String value = raw == null ? "" : raw.trim();
        return value.isBlank() ? missingLabel : value.toUpperCase(locale);
    }

    static String clean(String value) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.equalsIgnoreCase("Nenhum") || clean.equalsIgnoreCase("nan")
                || clean.equalsIgnoreCase("none") ? "" : clean;
    }
}
