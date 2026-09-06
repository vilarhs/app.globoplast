package br.com.globoplast.oee.view;

import br.com.globoplast.oee.config.AppConfig;
import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.data.renderer.ComponentRenderer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.LongFunction;
import java.util.function.Supplier;

final class ProductionSummaryPage {
    enum Period { DAY, MONTH }

    private final Function<String, String> translate;
    private final LongFunction<String> formatInteger;
    private final DoubleFunction<String> formatDecimal;
    private final DoubleFunction<String> formatOneDecimal;
    private final Supplier<Locale> locale;
    private final IntConsumer rememberEntryLimit;
    private final Div metrics = new Div();
    private final Div result = new Div();
    private final Grid<LaunchRecord> summaryGrid;
    private final Grid<LaunchRecord> entriesGrid;
    private final Button summaryMore;
    private final Button entriesMore;
    private final H3 tableTitle;
    private final H3 chartTitle;
    private final H3 allTitle;
    private final Div rankingHolder = new Div();
    private List<LaunchRecord> currentSummary = List.of();
    private List<LaunchRecord> currentEntries = List.of();
    private int summaryLimit = AppConfig.PAGE_SIZE;
    private int entryLimit;

    ProductionSummaryPage(Period period, Function<String, String> translate,
                          LongFunction<String> formatInteger, DoubleFunction<String> formatDecimal,
                          DoubleFunction<String> formatOneDecimal, Supplier<Locale> locale,
                          Function<LaunchRecord, Component> oeeCell,
                          Supplier<Grid<LaunchRecord>> entriesGridFactory,
                          int initialEntryLimit, IntConsumer rememberEntryLimit) {
        this.translate = translate;
        this.formatInteger = formatInteger;
        this.formatDecimal = formatDecimal;
        this.formatOneDecimal = formatOneDecimal;
        this.locale = locale;
        this.entryLimit = Math.max(AppConfig.PAGE_SIZE, initialEntryLimit);
        this.rememberEntryLimit = rememberEntryLimit;

        summaryGrid = period == Period.DAY ? dailyGrid(oeeCell) : monthlyGrid(oeeCell);
        entriesGrid = entriesGridFactory.get();
        tableTitle = subtitle(period == Period.DAY
                ? t("Tabela Consolidada por Máquina") : t("Tabela Consolidada por Equipamento"));
        chartTitle = subtitle(period == Period.DAY ? t("OEE por Máquina no Dia") : t("OEE por Máquina no Mês"));
        allTitle = subtitle(period == Period.DAY ? t("Todos os Apontamentos do Dia") : t("Todos os Apontamentos do Mês"));
        allTitle.addClassName("gp-summary-all-title");
        if (period == Period.MONTH) rankingHolder.addClassName("gp-month-ranking-holder-v054");

        summaryMore = moreButton(() -> {
            summaryLimit += AppConfig.PAGE_SIZE;
            refreshRows();
        });
        entriesMore = moreButton(() -> {
            entryLimit += AppConfig.PAGE_SIZE;
            rememberEntryLimit.accept(entryLimit);
            refreshRows();
        });
        result.addClassName("gp-summary-result");
        restoreStructure();
    }

    Div result() { return result; }
    Button entriesMore() { return entriesMore; }

    void resetLimits() {
        summaryLimit = AppConfig.PAGE_SIZE;
        entryLimit = AppConfig.PAGE_SIZE;
        rememberEntryLimit.accept(entryLimit);
    }

    void showEmpty(String message) {
        currentSummary = List.of();
        currentEntries = List.of();
        entriesMore.setVisible(false);
        result.removeAll();
        Div empty = new Div(new Span(message));
        empty.addClassName("gp-empty-state");
        result.add(empty);
    }

    void show(List<LaunchRecord> summary, List<LaunchRecord> entries) {
        if (result.getChildren().noneMatch(component -> component == tableTitle)) restoreStructure();
        currentSummary = summary == null ? List.of() : summary;
        currentEntries = entries == null ? List.of() : entries;
        renderMetrics(currentSummary, currentEntries);
        Map<String, Double> bars = new LinkedHashMap<>();
        currentSummary.stream()
                .sorted((left, right) -> Double.compare(right.getOeePct(), left.getOeePct()))
                .forEach(record -> bars.put(record.getMachine(), record.getOeePct()));
        rankingHolder.removeAll();
        rankingHolder.add(new OeeRankingChart(bars, locale.get()));
        refreshRows();
    }

    private void restoreStructure() {
        result.removeAll();
        result.add(metrics, tableTitle, summaryGrid, summaryMore, chartTitle, rankingHolder, allTitle, entriesGrid);
    }

    private void refreshRows() {
        int visibleSummary = Math.min(summaryLimit, currentSummary.size());
        summaryGrid.setItems(currentSummary.stream().limit(visibleSummary).toList());
        summaryMore.setVisible(visibleSummary < currentSummary.size());
        int visibleEntries = Math.min(entryLimit, currentEntries.size());
        entriesGrid.setItems(currentEntries.stream().limit(visibleEntries).toList());
        entriesMore.setVisible(visibleEntries < currentEntries.size());
    }

    private void renderMetrics(List<LaunchRecord> summary, List<LaunchRecord> rows) {
        int produced = summary.stream().mapToInt(LaunchRecord::getTotalProduced).sum();
        int scrapPieces = summary.stream().mapToInt(LaunchRecord::getScrapTotalPcs).sum();
        long expected = ProductionSummary.capacityTargetByMachineDay(rows);
        double scrapPct = produced > 0 ? Norm.round(scrapPieces * 100.0 / produced, 2) : 0;
        double performancePct = expected > 0 ? Norm.round((produced + scrapPieces) * 100.0 / expected, 2) : 0;
        double achievedPct = expected > 0 ? Norm.round(produced * 100.0 / expected, 2) : 0;
        metrics.removeAll();
        metrics.add(
                kpi(t("🎯 OEE Geral"), format1(ProductionSummary.average(summary, ProductionSummary.Metric.OEE)) + "%"),
                kpi(t("⏱️ Disponibilidade"), format1(ProductionSummary.average(summary, ProductionSummary.Metric.AVAILABILITY)) + "%"),
                kpi(t("⚡ Desempenho"), format1(performancePct) + "%"),
                kpi(t("✨ Qualidade"), format1(ProductionSummary.average(summary, ProductionSummary.Metric.QUALITY)) + "%"),
                kpi(t("🔄 Trocas"), formatInt(summary.stream().mapToInt(LaunchRecord::getChangeovers).sum())),
                kpi(t("♻️ Refugo (pçs)"), formatInt(scrapPieces) + " " + t("pçs")),
                kpi(t("♻️ Refugo / Peças Boas"), format1(scrapPct) + "%"),
                kpi(t("📦 Peças Boas Produzidas"), formatInt(produced) + " " + t("pçs")),
                kpi(t("🎯 Produção Esperada (24h)"), formatInt(expected) + " " + t("pçs")),
                kpi(t("📊 Realizado / Esperado"), format1(achievedPct) + "%")
        );
        metrics.addClassNames("gp-original-metrics", "gp-original-metrics-10");
    }

    private Grid<LaunchRecord> monthlyGrid(Function<LaunchRecord, Component> oeeCell) {
        Grid<LaunchRecord> grid = baseGrid();
        grid.addColumn(LaunchRecord::getMachine).setHeader(t("Máquina"));
        grid.addColumn(LaunchRecord::getSector).setHeader(t("Setor"));
        grid.addColumn(record -> formatInt(record.getCapacity24h())).setHeader(t("Capacidade 24h"));
        grid.addColumn(record -> formatInt(record.getTotalProduced())).setHeader(t("Total Produzido (pçs)"));
        grid.addColumn(record -> format(record.getScrapTotalKg())).setHeader(t("Refugo Total (kg)"));
        grid.addColumn(record -> formatInt(record.getScrapTotalPcs())).setHeader(t("Refugo Total (pçs)"));
        grid.addColumn(record -> formatInt(record.getChangeovers())).setHeader(t("Qtd. Trocas"));
        grid.addColumn(record -> format1(record.getAvailabilityPct()) + "%").setHeader(t("Disponibilidade (%)"));
        grid.addColumn(record -> format1(record.getPerformancePct()) + "%").setHeader(t("Desempenho (%)"));
        grid.addColumn(record -> format1(record.getQualityPct()) + "%").setHeader(t("Qualidade (%)"));
        grid.addColumn(new ComponentRenderer<>(record -> oeeCell.apply(record))).setHeader("OEE (%)").setWidth("120px").setFlexGrow(0);
        return grid;
    }

    private Grid<LaunchRecord> dailyGrid(Function<LaunchRecord, Component> oeeCell) {
        Grid<LaunchRecord> grid = baseGrid();
        grid.addColumn(record -> Norm.br(record.getDate())).setHeader(t("Data")).setWidth("108px").setFlexGrow(0);
        grid.addColumn(LaunchRecord::getMachine).setHeader(t("Máquina")).setFlexGrow(2);
        grid.addColumn(new ComponentRenderer<>(record -> compactCell(record.getProduct()))).setHeader(t("Código Produto")).setWidth("140px").setFlexGrow(0);
        grid.addColumn(new ComponentRenderer<>(record -> compactCell(record.getOrderNumber()))).setHeader(t("Nº OP")).setWidth("84px").setFlexGrow(0);
        grid.addColumn(record -> formatInt(record.getTotalProduced())).setHeader(t("Total Produzido")).setAutoWidth(true);
        grid.addColumn(record -> format(record.getScrapTotalKg())).setHeader(t("Refugo (kg)")).setAutoWidth(true);
        grid.addColumn(record -> formatInt(record.getScrapTotalPcs())).setHeader(t("Refugo (pçs)")).setAutoWidth(true);
        grid.addColumn(record -> format1(record.getScrapPct()) + "%").setHeader(t("Refugo (%)")).setAutoWidth(true);
        grid.addColumn(record -> formatInt(record.getChangeovers())).setHeader(t("Trocas")).setAutoWidth(true);
        grid.addColumn(record -> formatInt(record.getLaunchCount())).setHeader(t("Lançamentos")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(record -> oeeCell.apply(record))).setHeader("OEE").setWidth("120px").setFlexGrow(0);
        grid.addClassName("gp-summary-day-grid-v083");
        return grid;
    }

    private Grid<LaunchRecord> baseGrid() {
        Grid<LaunchRecord> grid = new Grid<>(LaunchRecord.class, false);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.setAllRowsVisible(true);
        grid.addClassName("gp-summary-grid");
        return grid;
    }

    private Component compactCell(String consolidated) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (consolidated != null) {
            for (String item : consolidated.split("\\s*/\\s*")) {
                String clean = item == null ? "" : item.trim();
                if (!clean.isBlank()) unique.add(clean);
            }
        }
        if (unique.isEmpty()) return new Span("—");
        List<String> items = new ArrayList<>(unique);
        Span value = new Span(items.get(0) + (items.size() > 1 ? "..." : ""));
        value.addClassName("gp-summary-compact-value-v082");
        if (items.size() > 1) {
            String all = String.join("\n", items);
            value.getElement().setAttribute("tabindex", "0");
            value.getElement().setAttribute("aria-label", all);
            value.getElement().setAttribute("data-gp-tooltip", "true");
            Tooltip.forComponent(value).withText(all).withPosition(Tooltip.TooltipPosition.TOP).withHoverDelay(150);
        }
        return value;
    }

    private Button moreButton(Runnable action) {
        Button button = new Button(t("Mostrar mais"), event -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.addClassName("gp-show-more");
        return button;
    }

    private H3 subtitle(String text) {
        H3 title = new H3(text);
        title.addClassName("gp-original-subtitle");
        return title;
    }

    private Div kpi(String label, String value) {
        Div item = new Div(new Span(label), new H3(value));
        item.addClassName("gp-kpi");
        return item;
    }

    private String t(String text) { return translate.apply(text); }
    private String formatInt(long value) { return formatInteger.apply(value); }
    private String format(double value) { return formatDecimal.apply(value); }
    private String format1(double value) { return formatOneDecimal.apply(value); }
}
