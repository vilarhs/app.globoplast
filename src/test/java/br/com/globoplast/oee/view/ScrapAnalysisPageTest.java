package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.RefugoRecord;
import br.com.globoplast.oee.service.RefugoService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScrapAnalysisPageTest {
    @Test
    void unavailableComparisonFallsBackToSectorAndKeepsExpectedContainers() {
        List<String> dimensions = new ArrayList<>();
        AtomicInteger searches = new AtomicInteger();
        Button filter = new Button();
        ScrapAnalysisPage page = new ScrapAnalysisPage(text -> text, new RefugoService(null),
                Long::toString, Double::toString, Double::toString, () -> Locale.forLanguageTag("pt-BR"),
                "", "Comparativo Mensal",
                filter, () -> false, () -> false, value -> searches.incrementAndGet(),
                () -> { }, dimensions::add);
        Popover dropdown = new Popover();
        dropdown.setTarget(filter);
        page.setFilterDropdown(dropdown);

        page.refreshSelected();

        assertEquals("Setor", dimensions.get(dimensions.size() - 1));
        assertEquals(8, page.getComponentCount());
        assertEquals("scrap-kpis", page.getComponentAt(3).getId().orElseThrow());
        assertEquals("scrap-chart", page.getComponentAt(5).getId().orElseThrow());
        LocalDate today = br.com.globoplast.oee.util.Norm.productiveToday();
        RefugoRecord row = new RefugoRecord(1, "1", today, today, "59001", 1000,
                "MÁQUINA", "77001", "PRODUTO", "CLIENTE", "A", "OPERADOR",
                2, "FALHA", 10, 1, "Extrusão", "");
        page.renderKpis(List.of(row), "Setor", "Setor", "EXTRUSÃO", today, today);
        Div kpis = (Div) page.getComponentAt(3);
        assertEquals(5, kpis.getComponentCount());
        assertEquals("100.0%", ((H3) ((Div) kpis.getComponentAt(1)).getComponentAt(1)).getText());
        Div toolbar = (Div) page.getComponentAt(1);
        ((TextField) toolbar.getComponentAt(0)).setValue("59298");
        assertEquals(1, searches.get());
        assertEquals(7, ((Tabs) page.getComponentAt(4)).getComponentCount());

        Div ranking = new Div();
        page.renderTopReasonsBySector(ranking, List.of(row), "Setor", "EXTRUSÃO", List.of());
        assertEquals(5, descendants(ranking)
                .filter(component -> component.getElement().getClassList().contains("gp-ranking-row"))
                .count());

        LocalDate previousMonth = today.minusMonths(1);
        RefugoRecord previous = new RefugoRecord(2, "2", previousMonth, previousMonth, "59002", 1000,
                "MÁQUINA", "77002", "PRODUTO", "CLIENTE", "A", "OPERADOR",
                1, "FALHA", 5, 1, "Extrusão", "");
        Div comparison = new Div();
        page.renderComparison(comparison, List.of(previous, row), true, null,
                key -> { }, key -> { }, chart -> { });
        assertEquals(1, descendants(comparison).filter(InteractiveBarChart.class::isInstance).count());
        assertEquals(1, descendants(comparison).filter(component -> component.getElement().getClassList()
                .contains("gp-comparison-kpis")).count());

        page.renderRecent(List.of(row));
        assertEquals(1, descendants(page.recent()).filter(component -> component.getElement().getClassList()
                .contains("gp-refugo-recent-expander")).count());
    }

    private static java.util.stream.Stream<com.vaadin.flow.component.Component> descendants(
            com.vaadin.flow.component.Component component) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(component),
                component.getChildren().flatMap(ScrapAnalysisPageTest::descendants));
    }
}
