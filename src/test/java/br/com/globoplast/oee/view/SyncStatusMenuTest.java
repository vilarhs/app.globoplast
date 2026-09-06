package br.com.globoplast.oee.view;

import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncStatusMenuTest {
    @Test
    void showsOfflineWhenThereIsNoRecentValidTimestamp() {
        MenuItem item = new ContextMenu().addItem("status");
        SyncStatusMenu.update(item, Map.of("erp", "invalid"), value -> value);

        assertEquals("Última sincronização —", item.getElement().getText());
        assertEquals("offline", item.getElement().getAttribute("data-gp-sync-state"));
        assertEquals("Offline", item.getElement().getAttribute("data-gp-sync-label"));
    }

    @Test
    void usesTheNewestTimestamp() {
        MenuItem item = new ContextMenu().addItem("status");
        String recent = ZonedDateTime.now().minusMinutes(1).toString();
        SyncStatusMenu.update(item, Map.of("old", "invalid", "new", recent), value -> value);

        assertEquals("online", item.getElement().getAttribute("data-gp-sync-state"));
    }
}
