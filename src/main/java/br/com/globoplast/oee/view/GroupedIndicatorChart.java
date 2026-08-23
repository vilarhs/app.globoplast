package br.com.globoplast.oee.view;

import br.com.globoplast.oee.util.DisplayFormat;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Gráfico agrupado para os quatro indicadores do resumo diário. */
public final class GroupedIndicatorChart extends Div {
    public GroupedIndicatorChart(
            Map<String, Map<String, Double>> machines,
            List<String> indicators,
            Map<String, String> labels,
            Locale locale
    ) {
        addClassName("gp-indicator-chart");
        Locale displayLocale = locale == null ? Locale.forLanguageTag("pt-BR") : locale;
        Map<String, Map<String, Double>> safe = machines == null ? Map.of() : new LinkedHashMap<>(machines);

        Div legend = new Div();
        legend.addClassName("gp-indicator-legend");
        for (String indicator : indicators) {
            Span item = new Span(labels.getOrDefault(indicator, indicator));
            item.addClassName("gp-indicator-legend-item");
            item.getElement().setAttribute("data-indicator", indicator);
            legend.add(item);
        }

        Div plot = new Div();
        plot.addClassName("gp-indicator-plot");
        for (var machine : safe.entrySet()) {
            Div group = new Div();
            group.addClassName("gp-indicator-group");
            Div bars = new Div();
            bars.addClassName("gp-indicator-bars");
            for (String indicator : indicators) {
                double value = machine.getValue().getOrDefault(indicator, 0.0);
                Div holder = new Div();
                holder.addClassName("gp-indicator-bar-holder");
                Div bar = new Div();
                bar.addClassName("gp-indicator-bar");
                bar.getElement().setAttribute("data-indicator", indicator);
                bar.getStyle().set("height", Math.max(0, Math.min(130, value)) / 130.0 * 100.0 + "%");
                bar.setTitle(labels.getOrDefault(indicator, indicator) + ": " + DisplayFormat.decimal(value, 1, displayLocale) + "%");
                holder.add(bar);
                bars.add(holder);
            }
            Span name = new Span(machine.getKey());
            name.addClassName("gp-indicator-machine");
            group.add(bars, name);
            plot.add(group);
        }
        add(legend, plot);
    }
}
