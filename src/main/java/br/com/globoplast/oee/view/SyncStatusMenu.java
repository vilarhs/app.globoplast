package br.com.globoplast.oee.view;

import br.com.globoplast.oee.config.AppConfig;
import com.vaadin.flow.component.contextmenu.MenuItem;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Function;

final class SyncStatusMenu {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private SyncStatusMenu() {}

    static void update(MenuItem item, Map<String, String> states, Function<String, String> translate) {
        if (item == null) return;
        ZonedDateTime latest = latest(states);
        boolean online = latest != null
                && Duration.between(latest, ZonedDateTime.now(AppConfig.ZONE)).abs().toMinutes() <= 10;
        String time = latest == null ? "—" : latest.format(TIME);
        item.getElement().setText(translate.apply("Última sincronização") + " " + time);
        item.getElement().setAttribute("data-gp-sync-state", online ? "online" : "offline");
        item.getElement().setAttribute("data-gp-sync-label", translate.apply(online ? "Online" : "Offline"));
    }

    private static ZonedDateTime latest(Map<String, String> states) {
        ZonedDateTime result = null;
        if (states == null) return null;
        for (String value : states.values()) {
            if (value == null || value.isBlank()) continue;
            try {
                ZonedDateTime parsed = ZonedDateTime.parse(value).withZoneSameInstant(AppConfig.ZONE);
                if (result == null || parsed.isAfter(result)) result = parsed;
            } catch (RuntimeException ignored) { }
        }
        return result;
    }
}
