package br.com.globoplast.oee.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;

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
        ScrapAnalysisPage page = new ScrapAnalysisPage(text -> text, "", "Comparativo Mensal",
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
        Div toolbar = (Div) page.getComponentAt(1);
        ((TextField) toolbar.getComponentAt(0)).setValue("59298");
        assertEquals(1, searches.get());
        assertEquals(7, ((Tabs) page.getComponentAt(4)).getComponentCount());
    }
}
