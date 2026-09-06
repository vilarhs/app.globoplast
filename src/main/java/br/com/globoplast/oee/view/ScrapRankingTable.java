package br.com.globoplast.oee.view;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleFunction;
import java.util.function.Function;

final class ScrapRankingTable extends Div {
    ScrapRankingTable(Map<String, Double> ranking, double total, String sector,
                      Function<String, String> translate, DoubleFunction<String> format,
                      Locale locale) {
        addClassName("gp-ranking-table");
        int rank = 1;
        for (var entry : ranking.entrySet()) {
            if (rank > 5) break;
            String[] parts = entry.getKey().split("¦", -1);
            String code = parts.length > 0 ? parts[0] : "";
            String entrySector = parts.length > 1 ? parts[1] : "";
            String motive = parts.length > 2 ? parts[2] : entry.getKey();
            double percentage = total > 0 ? entry.getValue() / total * 100.0 : 0;
            Div row = new Div(new Span(rank + "º"), new Span(
                    translate.apply(motive).toUpperCase(locale) + " · "
                            + (sector == null ? entrySector + " · " : "")
                            + code + " · " + format.apply(entry.getValue()) + " kg ("
                            + format.apply(percentage) + "%)"));
            row.addClassName("gp-ranking-row");
            add(row);
            rank++;
        }
        while (rank <= 5) {
            Div row = new Div(new Span(rank + "º"), new Span("—"));
            row.addClassName("gp-ranking-row");
            add(row);
            rank++;
        }
    }
}
