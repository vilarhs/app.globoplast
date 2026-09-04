package br.com.globoplast.oee.view;

import br.com.globoplast.oee.util.DisplayFormat;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Ranking mensal de OEE em barras verticais, equivalente ao px.bar da v723. */
public final class OeeRankingChart extends Div {
    public OeeRankingChart(Map<String, Double> values, Locale locale) {
        addClassName("gp-oee-ranking-chart");
        Locale displayLocale = locale == null ? Locale.forLanguageTag("pt-BR") : locale;
        Map<String, Double> safe = values == null ? Map.of() : new LinkedHashMap<>(values);
        double max = safe.values().stream().mapToDouble(v -> v == null ? 0.0 : v).max().orElse(0.0);

        Div frame = new Div();
        frame.addClassName("gp-oee-ranking-frame");
        Span yTitle = new Span("OEE (%)");
        yTitle.addClassName("gp-oee-ranking-y-title");
        Div plot = new Div();
        plot.addClassName("gp-oee-ranking-plot");

        safe.forEach((machine, raw) -> {
            double value = raw == null ? 0.0 : raw;
            Div column = new Div();
            column.addClassName("gp-oee-ranking-column");
            Span valueLabel = new Span(DisplayFormat.decimal(value, 1, displayLocale) + "%");
            valueLabel.addClassName("gp-oee-ranking-value");
            Div area = new Div();
            area.addClassName("gp-oee-ranking-area");
            Div bar = new Div();
            bar.addClassName("gp-oee-ranking-bar");
            double pct = max <= 0 ? 0 : Math.max(1.5, value / max * 84.75);
            bar.getStyle().set("height", pct + "%");
            // Aproxima a escala contínua RdYlGn do gráfico Plotly original.
            double normalized = Math.max(0.0, Math.min(1.0, value / 100.0));
            double hue = normalized * 120.0;
            bar.getStyle().set("background", String.format(Locale.ROOT, "hsl(%.1f 70%% 45%%)", hue));
            column.setTitle(machine + "\nOEE: " + DisplayFormat.decimal(value, 1, displayLocale) + "%");
            bar.add(valueLabel);
            area.add(bar);
            Span label = new Span(machine);
            label.addClassName("gp-oee-ranking-label");
            column.add(area, label);
            plot.add(column);
        });

        frame.add(yTitle, plot);
        add(frame);
    }
}
