package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.RefugoRecord;
import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.textfield.TextField;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrapReportPageTest {
    @Test
    void preservesSectorsTotalsMonthsTitlesAndSearch() {
        AtomicReference<String> search = new AtomicReference<>();
        ScrapReportPage page = new ScrapReportPage(text -> text,
                () -> Locale.forLanguageTag("pt-BR"), "OP", new Button(), button -> new Popover(),
                search::set, (rows, sector) -> new Span(sector));
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);
        RefugoRecord row = new RefugoRecord(1, "1", start, start, "59394", 0,
                "Máquina", "770123", "Produto", "", "A", "", 2.75,
                "Motivo", 1, 0, Norm.scrapSectors().get(0), "");
        page.refresh(start, end, List.of(row), List.of(row));
        Grid<?> grid = (Grid<?>) descendants(page).filter(Grid.class::isInstance).findFirst().orElseThrow();
        assertEquals(Norm.scrapSectors().size(), grid.getListDataView().getItemCount());
        assertEquals(2, descendants(page).filter(H2.class::isInstance)
                .map(H2.class::cast).filter(h -> h.getText().equals("Relatório de Refugo — Agosto 2026")).count());
        Div kpis = (Div) descendants(page).filter(c -> c.getId().orElse("").equals("scrap-report-kpis")).findFirst().orElseThrow();
        assertTrue(kpis.getElement().getTextRecursively().contains("2,75 kg"));
        assertEquals(6, descendants(page).filter(c -> c.getElement().getClassList()
                .contains("gp-scrap-report-month-column-v130")).count());
        assertEquals(Norm.scrapReasonReportSectors().size(), descendants(page).filter(c -> c.getElement().getClassList()
                .contains("gp-scrap-reason-card-v116")).count());
        TextField field = (TextField) descendants(page).filter(TextField.class::isInstance).findFirst().orElseThrow();
        assertEquals("OP", field.getValue());
        field.setValue("59394");
        assertEquals("59394", search.get());
        page.refresh(LocalDate.of(2025, 5, 1), end, List.of(), List.of());
        assertEquals(2, descendants(page).filter(H2.class::isInstance).map(H2.class::cast)
                .filter(h -> h.getText().equals("Relatório de Refugo — Maio 2025 a Agosto 2026")).count());
        assertEquals(6, descendants(page).filter(c -> c.getElement().getClassList()
                .contains("gp-scrap-report-month-column-v130")).count());
        assertTrue(kpis.getElement().getTextRecursively().contains("0,00 kg"));
    }

    private static Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(ScrapReportPageTest::descendants));
    }
}
