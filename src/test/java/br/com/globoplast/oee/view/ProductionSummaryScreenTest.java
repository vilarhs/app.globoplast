package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.service.LaunchService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.select.Select;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionSummaryScreenTest {
    @Test
    @SuppressWarnings("unchecked")
    void preservesMonthSelectionAcrossDayNavigation() {
        LocalDate min = LocalDate.of(2026, 7, 1), max = LocalDate.of(2026, 8, 31);
        AtomicReference<LocalDate> requested = new AtomicReference<>();
        ProductionSummaryScreen screen = new ProductionSummaryScreen(new LaunchService(null, null, null, null),
                new LaunchCells(value -> value, Long::toString, Double::toString, Double::toString),
                () -> "pt-BR", () -> new LocalDate[]{min, max}, (start, end) -> {
                    requested.set(start);
                    return List.of();
                }, () -> new Grid<>(LaunchRecord.class, false));
        Div content = new Div();
        screen.renderDay(content);
        DateRangePicker date = (DateRangePicker) descendants(content)
                .filter(DateRangePicker.class::isInstance).findFirst().orElseThrow();
        // Alternar as telas recria os componentes, mas preserva seus filtros.
        screen.renderMonth(content);
        Select<YearMonth> month = (Select<YearMonth>) descendants(content)
                .filter(Select.class::isInstance).findFirst().orElseThrow();
        month.setValue(YearMonth.of(2026, 7));
        assertEquals(min, requested.get());
        screen.renderDay(content);
        DateRangePicker reopened = (DateRangePicker) descendants(content)
                .filter(DateRangePicker.class::isInstance).findFirst().orElseThrow();
        assertEquals(date.getValue(), reopened.getValue());
        screen.renderMonth(content);
        assertEquals(min, requested.get());
    }

    private Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::descendants));
    }
}
