package br.com.globoplast.oee.view;

import br.com.globoplast.oee.util.Norm;

import java.util.Locale;

final class LaunchInputNormalizer {
    private LaunchInputNormalizer() {}

    static String machineKey(String value) {
        return Norm.machineKey(value);
    }

    static String legacyMachineKey(String value) {
        return Norm.legacyMachineKey(value);
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
