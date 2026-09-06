package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.LaunchRecord;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSummaryPageTest {
    @Test
    void keepsPageExpansionInsideTheExistingGrids() {
        AtomicInteger rememberedLimit = new AtomicInteger();
        ProductionSummaryPage page = new ProductionSummaryPage(
                ProductionSummaryPage.Period.DAY, text -> text,
                Long::toString, Double::toString, Double::toString, () -> Locale.US,
                record -> new Span(Double.toString(record.getOeePct())),
                () -> new Grid<>(LaunchRecord.class, false), 20, rememberedLimit::set);
        List<LaunchRecord> rows = IntStream.range(0, 21).mapToObj(this::record).toList();

        page.show(rows, rows);

        Div result = page.result();
        assertEquals(8, result.getComponentCount());
        assertEquals(10, ((Div) result.getComponentAt(0)).getComponentCount());
        Grid<?> summaryGrid = (Grid<?>) result.getComponentAt(2);
        Grid<?> entriesGrid = (Grid<?>) result.getComponentAt(7);
        assertEquals(20, summaryGrid.getListDataView().getItemCount());
        assertEquals(20, entriesGrid.getListDataView().getItemCount());
        assertTrue(page.entriesMore().isVisible());

        page.entriesMore().click();

        assertEquals(21, entriesGrid.getListDataView().getItemCount());
        assertEquals(40, rememberedLimit.get());
        assertFalse(page.entriesMore().isVisible());
    }

    private LaunchRecord record(int index) {
        LaunchRecord record = new LaunchRecord();
        record.setDate(LocalDate.of(2026, 8, 1).plusDays(index % 2));
        record.setMachine("MÁQUINA " + index);
        record.setSector("Impressão");
        record.setCapacity24h(100);
        record.setTotalProduced(80);
        record.setOeePct(80);
        return record;
    }
}
