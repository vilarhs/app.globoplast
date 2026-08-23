package br.com.globoplast.oee.util;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Formatação visual determinística do Globoplast.
 * pt-BR: ponto para milhares e vírgula para decimais.
 * en-US: vírgula para milhares e ponto para decimais.
 */
public final class DisplayFormat {
    private DisplayFormat() { }

    public static String decimal(double value, int decimals, Locale locale) {
        Locale safeLocale = locale == null ? Locale.forLanguageTag("pt-BR") : locale;
        int places = Math.max(0, Math.min(6, decimals));
        NumberFormat nf = NumberFormat.getNumberInstance(safeLocale);
        nf.setGroupingUsed(true);
        nf.setMinimumFractionDigits(places);
        nf.setMaximumFractionDigits(places);
        return nf.format(Double.isFinite(value) ? value : 0.0);
    }

    public static String integer(long value, Locale locale) {
        Locale safeLocale = locale == null ? Locale.forLanguageTag("pt-BR") : locale;
        NumberFormat nf = NumberFormat.getIntegerInstance(safeLocale);
        nf.setGroupingUsed(true);
        nf.setMaximumFractionDigits(0);
        return nf.format(value);
    }
}
