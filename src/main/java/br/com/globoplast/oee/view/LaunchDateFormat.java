package br.com.globoplast.oee.view;

import br.com.globoplast.oee.config.AppConfig;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

final class LaunchDateFormat {
    private static final DateTimeFormatter TRASH = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private LaunchDateFormat() {}

    static String trash(String value) {
        if (value == null || value.isBlank()) return "—";
        try {
            return ZonedDateTime.parse(value).withZoneSameInstant(AppConfig.ZONE).format(TRASH);
        } catch (RuntimeException ignored) {
            return value;
        }
    }
}
