package br.com.globoplast.oee.view;

import br.com.globoplast.oee.util.DisplayFormat;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class BarChart extends Div {
    public BarChart(Map<String, Double> values, Consumer<String> onClick) {
        this(values, onClick, Locale.forLanguageTag("pt-BR"));
    }

    public BarChart(Map<String, Double> values, Consumer<String> onClick, Locale locale) {
        addClassName("gp-bar-chart");
        Locale displayLocale = locale == null ? Locale.forLanguageTag("pt-BR") : locale;
        double max = values.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        values.forEach((label, value) -> {
            Div row = new Div();
            row.addClassName("gp-bar-row");
            Span l = new Span(label);
            l.addClassName("gp-bar-label");
            Div track = new Div();
            track.addClassName("gp-bar-track");
            Div bar = new Div();
            bar.addClassName("gp-bar");
            bar.getStyle().set("width", max <= 0 ? "0%" : Math.max(2, value / max * 100) + "%");
            bar.setTitle(label + " · " + DisplayFormat.decimal(value, 3, displayLocale) + " kg");
            if (onClick != null) {
                bar.getStyle().set("cursor", "pointer");
                bar.addClickListener(e -> onClick.accept(label));
            }
            track.add(bar);
            Span v = new Span(DisplayFormat.decimal(value, 3, displayLocale) + " kg");
            v.addClassName("gp-bar-value");
            row.add(l, track, v);
            add(row);
        });
    }
}
