package br.com.globoplast.oee.view;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class LaunchDisplayValues {
    private LaunchDisplayValues() {}

    static String number(double value, String language) {
        if (Math.abs(value) < 1e-12) return "";
        if (Math.rint(value) == value) return String.valueOf((long) value);
        String text = BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return "pt-BR".equals(language) ? text.replace('.', ',') : text;
    }

    static String integer(int value) {
        return value == 0 ? "" : String.valueOf(value);
    }
}
