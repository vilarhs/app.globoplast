package br.com.globoplast.oee.view;

import br.com.globoplast.oee.service.LaunchService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class OrderStockPageTest {
    @Test
    void searchClearAndReopeningPreserveTheSelectedOrder() {
        List<String> queries = new ArrayList<>();
        LaunchService service = new LaunchService(null, null, null, null) {
            @Override public List<OrderProcessProgress> orderProcessProgress(String order) {
                queries.add(order);
                return List.of(new OrderProcessProgress(order, "776", "Colocação de Tampa",
                        "7761234567", "Teste", 24000, 10000, 14000, null, null));
            }
        };
        AtomicReference<String> selected = new AtomicReference<>("");
        OrderStockPage page = new OrderStockPage(service, "", selected::set,
                text -> text, Long::toString, Span::new);
        assertTrue(queries.isEmpty());
        Div controls = (Div) page.components()[2];
        TextField field = (TextField) controls.getComponentAt(0);
        Button button = (Button) controls.getComponentAt(1);
        field.setValue(" 990001 ");
        button.click();
        assertEquals("990001", selected.get());
        assertEquals(List.of("990001"), queries);
        Grid<?> grid = (Grid<?>) page.components()[4];
        assertEquals(1, grid.getListDataView().getItemCount());
        new OrderStockPage(service, selected.get(), selected::set,
                text -> text, Long::toString, Span::new);
        assertEquals(List.of("990001", "990001"), queries);
        field.clear();
        assertEquals("", selected.get());
        assertEquals(0, grid.getListDataView().getItemCount());
        assertEquals(2, queries.size());
    }
}
