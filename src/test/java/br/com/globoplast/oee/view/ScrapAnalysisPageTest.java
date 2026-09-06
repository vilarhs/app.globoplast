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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScrapAnalysisPageTest {
    @Test
    void unavailableComparisonFallsBackToSectorAndKeepsExpectedContainers() {
        List<String> dimensions = new ArrayList<>();
        AtomicInteger searches = new AtomicInteger();
        Button filter = new Button();
        ScrapAnalysisPage page = new ScrapAnalysisPage(text -> text, new RefugoService(null),
                Long::toString, Double::toString, Double::toString, "", "Comparativo Mensal",
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
    }
}
