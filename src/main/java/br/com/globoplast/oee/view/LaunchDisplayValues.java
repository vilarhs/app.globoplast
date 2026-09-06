package br.com.globoplast.oee.view;

final class LaunchDisplayValues {
    private LaunchDisplayValues() {}

    static String number(double value, String language) {
        if (Math.abs(value) < 1e-12) return "";
        if (Math.rint(value) == value) return String.valueOf((long) value);
        String text = String.valueOf(value);
        return "pt-BR".equals(language) ? text.replace('.', ',') : text;
    }

    static String integer(int value) {
        return value == 0 ? "" : String.valueOf(value);
    }
}
