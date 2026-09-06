package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.LaunchRecord;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchesPageTest {
    @Test
    void buildsStandardLaunchTableAndConnectsPageActions() {
        Grid<LaunchRecord> grid = LaunchesPage.grid(text -> text, Long::toString, Double::toString,
                Span::new, record -> new Span(), record -> new Span(), record -> new Span(), record -> new Span());
        AtomicReference<String> search = new AtomicReference<>();
        AtomicBoolean added = new AtomicBoolean();
        AtomicInteger more = new AtomicInteger();
        LaunchesPage page = new LaunchesPage("Lançamentos", "OP 59001", true, text -> text,
                button -> { Popover popover = new Popover(); popover.setTarget(button); return popover; },
                search::set, () -> added.set(true), more::incrementAndGet, grid);

        assertEquals(13, grid.getColumns().size());
        assertEquals(5, page.components().length);
        Div toolbar = (Div) page.components()[1];
        assertEquals(3, toolbar.getComponentCount());
        ((TextField) toolbar.getComponentAt(0)).setValue("59298");
        ((Button) toolbar.getComponentAt(2)).click();
        ((Button) page.components()[4]).click();

        assertEquals("59298", search.get());
        assertTrue(added.get());
        assertEquals(1, more.get());
        Div gridWrapper = (Div) page.components()[3];
        assertEquals("launch-grid", gridWrapper.getComponentAt(1).getId().orElseThrow());
    }
}
