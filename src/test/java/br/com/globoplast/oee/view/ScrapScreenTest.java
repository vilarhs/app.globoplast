package br.com.globoplast.oee.view;

import br.com.globoplast.oee.service.RefugoService;
import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ScrapScreenTest {
    @Test
    void sharesSearchBetweenAnalysisAndReportAfterNavigation() {
        Div content = new Div();
        LocalDate today = Norm.productiveToday();
        ScrapScreen screen = new ScrapScreen(new RefugoService(null), null,
                () -> null, () -> "pt-BR", content,
                () -> new LocalDate[]{today.minusMonths(1), today},
                (start, end) -> List.of(), () -> {}, ignored -> {});
        screen.renderScrap();
        search(content).setValue("59298");
        screen.renderScrapReport();
        assertEquals("59298", search(content).getValue());
        search(content).setValue("59501");
        screen.renderScrap();
        assertEquals("59501", search(content).getValue());
    }

    private TextField search(Component root) {
        return descendants(root).filter(TextField.class::isInstance).map(TextField.class::cast)
                .filter(field -> "Pesquisar refugo".equals(field.getLabel())).findFirst().orElseThrow();
    }
    private Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::descendants));
    }
}
