package br.com.globoplast.oee.view;

import br.com.globoplast.oee.util.DisplayFormat;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Gráfico de barras de Refugo com geometria equivalente ao Plotly do app original:
 * faixa Y única para barras/grade/ticks, 18% de respiro no topo, valores externos,
 * rótulos a -35°, 15 itens por página, hover e seleção por clique.
 */
public final class InteractiveBarChart extends Div {
    public InteractiveBarChart(
            String title,
            Map<String, Double> values,
            Map<String, String> displayLabels,
            String selectedKey,
            Locale locale,
            Consumer<String> onClick
    ) {
        this(title, values, displayLabels, selectedKey, locale, onClick, null, "kg", 1);
    }

    public InteractiveBarChart(
            String title,
            Map<String, Double> values,
            Map<String, String> displayLabels,
            String selectedKey,
            Locale locale,
            Consumer<String> onClick,
            Consumer<String> onContextMenu
    ) {
        this(title, values, displayLabels, selectedKey, locale, onClick, onContextMenu, "kg", 1);
    }

    public InteractiveBarChart(
            String title,
            Map<String, Double> values,
            Map<String, String> displayLabels,
            String selectedKey,
            Locale locale,
            Consumer<String> onClick,
            String unit,
            int decimals
    ) {
        this(title, values, displayLabels, selectedKey, locale, onClick, null, unit, decimals);
    }

    public InteractiveBarChart(
            String title,
            Map<String, Double> values,
            Map<String, String> displayLabels,
            String selectedKey,
            Locale locale,
            Consumer<String> onClick,
            Consumer<String> onContextMenu,
            String unit,
            int decimals
    ) {
        addClassName("gp-refugo-chart");

        Locale displayLocale = locale == null ? Locale.forLanguageTag("pt-BR") : locale;
        Map<String, Double> safe = values == null ? Map.of() : new LinkedHashMap<>(values);
        double max = safe.values().stream().mapToDouble(v -> v == null ? 0.0 : v).max().orElse(0.0);
        double axisMax = max > 0 ? max * 1.18 : 1.0;
        String suffix = unit == null || unit.isBlank() ? "" : " " + unit;
        int places = Math.max(0, Math.min(3, decimals));

        Div frame = new Div();
        frame.addClassName("gp-refugo-chart-frame");

        Span chartTitle = new Span(title == null ? "" : title);
        chartTitle.addClassName("gp-refugo-chart-title");

        Span yTitle = new Span(
                displayLocale.getLanguage().equalsIgnoreCase("en")
                        ? "Scrap Quantity (Kg)"
                        : "Quantidade Refugada (Kg)"
        );
        yTitle.addClassName("gp-refugo-y-title");

        Div axis = new Div();
        axis.addClassName("gp-refugo-y-axis");

        Div plot = new Div();
        plot.addClassName("gp-refugo-chart-plot");
        if (selectedKey != null && safe.containsKey(selectedKey)) {
            plot.addClassName("gp-refugo-has-selection");
        }

        Div gridArea = new Div();
        gridArea.addClassName("gp-refugo-grid-area");
        plot.add(gridArea);

        for (Tick tick : ticks(axisMax)) {
            double pct = Math.max(0.0, Math.min(100.0, tick.value() / axisMax * 100.0));

            Span tickLabel = new Span(formatTick(displayLocale, tick.value()));
            tickLabel.addClassName("gp-refugo-y-tick");
            tickLabel.getStyle().set("--gp-tick-pos", "calc(" + pct + "% - 0.45em)");
            axis.add(tickLabel);

            Div grid = new Div();
            grid.addClassName("gp-refugo-grid-line");
            if (tick.value() == 0.0) grid.addClassName("gp-refugo-grid-baseline");
            grid.getStyle().set("bottom", pct + "%");
            gridArea.add(grid);
        }

        int itemCount = Math.max(1, safe.size());
        int labelLimit = 18; // fixo em todas as páginas: a última página não volta a exibir rótulos longos
        BarSizing sizing = barSizing(itemCount);
        plot.getStyle().set("--gp-refugo-bar-ratio", sizing.ratio());
        plot.getStyle().set("--gp-refugo-bar-max", sizing.maxWidthPx() + "px");
        plot.getStyle().set("--gp-refugo-amount-size", sizing.amountFontPx() + "px");

        for (var entry : safe.entrySet()) {
            String key = entry.getKey();
            double value = entry.getValue() == null ? 0.0 : entry.getValue();
            String label = displayLabels == null ? key : displayLabels.getOrDefault(key, key);
            double pct = axisMax <= 0 ? 0.0 : Math.max(0.0, Math.min(100.0, value / axisMax * 100.0));

            Div column = new Div();
            column.addClassName("gp-refugo-bar-column");
            if (key != null && key.equals(selectedKey)) column.addClassName("gp-refugo-bar-selected");

            Div barArea = new Div();
            barArea.addClassName("gp-refugo-bar-area");

            Div bar = new Div();
            bar.addClassName("gp-refugo-vbar");
            bar.getStyle().set("height", Math.max(value > 0 ? 0.7 : 0.0, pct) + "%");

            Span amount = new Span(DisplayFormat.decimal(value, places, displayLocale) + suffix);
            amount.addClassName("gp-refugo-bar-amount");
            amount.getStyle().set("bottom", "calc(" + pct + "% + 6px)");

            barArea.add(bar, amount);

            Span labelSpan = new Span(shortLabel(label, labelLimit));
            labelSpan.addClassName("gp-refugo-bar-label");

            String tooltip = label + "\n" +
                    (displayLocale.getLanguage().equalsIgnoreCase("en") ? "Scrap: " : "Refugo: ") +
                    DisplayFormat.decimal(value, places, displayLocale) + suffix;
            column.setTitle(tooltip);

            if (onClick != null) {
                column.getStyle().set("cursor", "pointer");
                column.addClickListener(e -> onClick.accept(key));
                column.addAttachListener(e -> column.getElement().executeJs(
                        "if(this.__gpSelectV061)return;" +
                        "this.__gpSelectV061=true;" +
                        "this.addEventListener('click',()=>{" +
                        "const plot=this.closest('.gp-refugo-chart-plot');" +
                        "if(!plot)return;" +
                        "const wasSelected=this.classList.contains('gp-refugo-bar-selected');" +
                        "plot.querySelectorAll('.gp-refugo-bar-column').forEach(el=>el.classList.remove('gp-refugo-bar-selected'));" +
                        "plot.classList.toggle('gp-refugo-has-selection',!wasSelected);" +
                        "if(!wasSelected)this.classList.add('gp-refugo-bar-selected');" +
                        "});"
                ));
            }
            if (onContextMenu != null) {
                column.getStyle().set("cursor", "pointer");
                column.getElement().addEventListener("contextmenu", e -> onContextMenu.accept(key));
                column.addAttachListener(e -> column.getElement().executeJs(
                        "if(this.__gpContextSelectV064)return;" +
                        "this.__gpContextSelectV064=true;" +
                        "this.addEventListener('contextmenu',()=>{" +
                        "const plot=this.closest('.gp-refugo-chart-plot');" +
                        "if(!plot)return;" +
                        "plot.classList.add('gp-refugo-has-selection');" +
                        "plot.querySelectorAll('.gp-refugo-bar-column').forEach(el=>el.classList.remove('gp-refugo-bar-selected'));" +
                        "this.classList.add('gp-refugo-bar-selected');" +
                        "});"
                ));
            }

            column.add(barArea, labelSpan);
            plot.add(column);
        }

        Div canvas = new Div(axis, plot);
        canvas.addClassName("gp-refugo-chart-canvas");
        frame.add(chartTitle, yTitle, canvas);
        add(frame);
    }

    private static BarSizing barSizing(int itemCount) {
        // Poucos itens usam melhor o espaço disponível; conforme entram categorias,
        // as barras afinam progressivamente sem alterar a largura total do gráfico.
        if (itemCount <= 2) return new BarSizing("92%", 320, 13);
        if (itemCount <= 4) return new BarSizing("88%", 280, 13);
        if (itemCount <= 6) return new BarSizing("84%", 230, 13);
        if (itemCount <= 9) return new BarSizing("78%", 180, 12);
        if (itemCount <= 12) return new BarSizing("72%", 140, 12);
        return new BarSizing("68%", 110, 11);
    }

    private static String shortLabel(String label, int maxChars) {
        if (label == null) return "";
        String clean = label.strip().replaceAll("\\s+", " ");
        if (clean.length() <= maxChars) return clean;
        int cut = Math.max(1, maxChars - 1);
        return clean.substring(0, cut).stripTrailing() + "…";
    }

    private static List<Tick> ticks(double axisMax) {
        List<Tick> out = new ArrayList<>();
        if (!(axisMax > 0)) {
            out.add(new Tick(0.0));
            out.add(new Tick(1.0));
            return out;
        }
        double step = niceStep(axisMax / 4.0);
        out.add(new Tick(0.0));
        for (double v = step; v < axisMax * 0.999999; v += step) {
            out.add(new Tick(v));
            if (out.size() >= 7) break;
        }
        return out;
    }

    private static double niceStep(double raw) {
        if (!(raw > 0)) return 1.0;
        double power = Math.pow(10.0, Math.floor(Math.log10(raw)));
        double fraction = raw / power;
        double nice;
        if (fraction <= 1.0) nice = 1.0;
        else if (fraction <= 2.0) nice = 2.0;
        else if (fraction <= 2.5) nice = 2.5;
        else if (fraction <= 5.0) nice = 5.0;
        else nice = 10.0;
        return nice * power;
    }

    private static String formatTick(Locale locale, double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000) return DisplayFormat.decimal(value / 1_000_000.0, 1, locale) + "M";
        if (abs >= 1_000) {
            double k = value / 1_000.0;
            return Math.abs(k - Math.rint(k)) < 0.000001
                    ? DisplayFormat.decimal(k, 0, locale) + "k"
                    : DisplayFormat.decimal(k, 1, locale) + "k";
        }
        if (abs >= 100) return DisplayFormat.decimal(value, 0, locale);
        if (abs >= 10) return DisplayFormat.decimal(value, 1, locale);
        return DisplayFormat.decimal(value, 2, locale);
    }

    private record Tick(double value) {}
    private record BarSizing(String ratio, int maxWidthPx, int amountFontPx) {}
}
