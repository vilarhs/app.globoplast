package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.RefugoRecord;
import br.com.globoplast.oee.service.RefugoService;
import br.com.globoplast.oee.util.DisplayFormat;
import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class ScrapAnalysisPage extends Div {
    private final Function<String, String> translate;
    private final RefugoService scraps;
    private final LongFunction<String> formatInteger;
    private final DoubleFunction<String> formatDecimal;
    private final DoubleFunction<String> formatOneDecimal;
    private final Supplier<Locale> locale;
    private final BooleanSupplier hasMonthlyComparison;
    private final BooleanSupplier hasYearlyComparison;
    private final Consumer<String> dimensionSelected;
    private final Div titleRow;
    private final Div toolbar;
    private final Div kpis = new Div();
    private final Div chart = new Div();
    private final Div details = new Div();
    private final Div recent = new Div();
    private final Map<Tab, String> dimensions = new LinkedHashMap<>();
    private final Tab yearlyComparisonTab;
    private final Tab monthlyComparisonTab;
    private final Tabs tabs;
    private final Button filter;

    ScrapAnalysisPage(Function<String, String> translate, RefugoService scraps,
                      LongFunction<String> formatInteger, DoubleFunction<String> formatDecimal,
                      DoubleFunction<String> formatOneDecimal, Supplier<Locale> locale,
                      String initialSearch, String initialDimension,
                      Button filter, BooleanSupplier hasMonthlyComparison,
                      BooleanSupplier hasYearlyComparison, Consumer<String> searchChanged,
                      Runnable tabChanged, Consumer<String> dimensionSelected) {
        this.translate = translate;
        this.scraps = scraps;
        this.formatInteger = formatInteger;
        this.formatDecimal = formatDecimal;
        this.formatOneDecimal = formatOneDecimal;
        this.locale = locale;
        this.filter = filter;
        this.hasMonthlyComparison = hasMonthlyComparison;
        this.hasYearlyComparison = hasYearlyComparison;
        this.dimensionSelected = dimensionSelected;

        H2 title = new H2(t("Análise de Refugo"));
        title.addClassName("gp-section-title");
        titleRow = new Div(title);
        titleRow.addClassNames("gp-title-row", "gp-title-row-static");

        TextField search = new TextField(t("Pesquisar refugo"));
        search.setPlaceholder(t("Ordem, produto ou descrição"));
        search.setClearButtonVisible(true);
        search.setValue(initialSearch);
        configureFilterButton();
        toolbar = new Div(search, filter);
        toolbar.addClassNames("gp-toolbar", "gp-tab-controls", "gp-search-filter-toolbar-v044",
                "gp-refugo-search-toolbar-v071", "gp-refugo-search-toolbar-v072",
                "gp-refugo-search-toolbar-v073", "gp-refugo-search-toolbar-v074");

        kpis.setId("scrap-kpis");
        kpis.addClassNames("gp-kpis", "gp-refugo-kpis-v056");
        chart.setId("scrap-chart");
        chart.addClassName("gp-refugo-analysis");
        details.setId("scrap-details");
        details.addClassName("gp-refugo-details");
        recent.setId("scrap-recent");
        recent.addClassName("gp-refugo-recent");

        yearlyComparisonTab = tab("Anual", "Comparativo Anual");
        monthlyComparisonTab = tab("Mensal", "Comparativo Mensal");
        tabs = new Tabs(yearlyComparisonTab, monthlyComparisonTab,
                tab("Setor", "Setor"), tab("Máquina", "Máquina"), tab("Turno", "Turno"),
                tab("Descrição", "Descrição"), tab("Motivo", "Motivo"));
        tabs.addClassName("gp-inner-tabs");
        installScrollPreservation();
        dimensions.entrySet().stream()
                .filter(entry -> Objects.equals(entry.getValue(), initialDimension))
                .findFirst().ifPresent(entry -> tabs.setSelectedTab(entry.getKey()));

        search.addValueChangeListener(event -> {
            searchChanged.accept(event.getValue());
            refreshSelected();
        });
        tabs.addSelectedChangeListener(event -> {
            tabChanged.run();
            refreshSelected();
            restoreScroll();
        });
        addClassNames("gp-refugo-page", "gp-refugo-page-v065", "gp-refugo-page-v066",
                "gp-refugo-page-v067", "gp-refugo-page-v068", "gp-refugo-page-v069", "gp-refugo-page-v070");
        rebuild(null);
    }

    Button filterButton() { return filter; }
    Div chart() { return chart; }
    Div details() { return details; }
    Div recent() { return recent; }

    void setFilterDropdown(Popover dropdown) { rebuild(dropdown); }

    void refreshSelected() {
        monthlyComparisonTab.setVisible(hasMonthlyComparison.getAsBoolean());
        yearlyComparisonTab.setVisible(hasYearlyComparison.getAsBoolean());
        String selected = selectedDimension();
        if (("Comparativo Mensal".equals(selected) && !monthlyComparisonTab.isVisible())
                || ("Comparativo Anual".equals(selected) && !yearlyComparisonTab.isVisible())) {
            dimensions.entrySet().stream().filter(entry -> "Setor".equals(entry.getValue()))
                    .findFirst().ifPresent(entry -> tabs.setSelectedTab(entry.getKey()));
            selected = "Setor";
        }
        dimensionSelected.accept(selected);
    }

    void renderKpis(List<RefugoRecord> rows, String dimension,
                    String selectedDimension, String selectedKey,
                    LocalDate start, LocalDate end) {
        kpis.removeAll();
        double total = scraps.totalKg(rows);
        String selectedPct = "—";
        if (Objects.equals(selectedDimension, dimension) && selectedKey != null && !selectedKey.isBlank() && total > 0) {
            double selected = rows.stream().filter(row -> scraps.matches(row, dimension, selectedKey))
                    .mapToDouble(RefugoRecord::scrapKg).sum();
            if (selected > 0) selectedPct = formatOneDecimal.apply(selected * 100.0 / total) + "%";
        }
        Div totalKpi = kpiWithCaption(t("Total Refugo"), formatDecimal.apply(total) + " kg",
                t("{percentual}% do total").replace("{percentual}", formatOneDecimal.apply(total > 0 ? 100.0 : 0.0)));

        LocalDate today = Norm.productiveToday();
        String periodValue;
        String periodCaption = null;
        if (Objects.equals(start, today) && Objects.equals(end, today)) {
            periodValue = t("Hoje");
            periodCaption = Norm.br(today);
        } else if (Objects.equals(start, end)) periodValue = Norm.br(start);
        else periodValue = Norm.br(start) + " – " + Norm.br(end);

        kpis.add(totalKpi,
                kpi(t("Item Selecionado (%)"), selectedPct),
                kpi(t("Ordens Afetadas"), formatInteger.apply(scraps.orders(rows))),
                kpi(t("Total de Lançamentos"), formatInteger.apply(rows.size())),
                periodCaption == null ? kpi(t("Período"), periodValue)
                        : kpiWithCaption(t("Período"), periodValue, periodCaption));
    }

    int renderDimension(List<RefugoRecord> rows, String dimension, int requestedPage,
                        String selectedKey, Collection<String> filteredSectors,
                        Consumer<String> selectionChanged, Consumer<String> contextSelected,
                        Consumer<InteractiveBarChart> contextMenuInstaller, IntConsumer pageChanged) {
        chart.removeAll();
        Map<String, Double> aggregate = scraps.aggregate(rows, dimension);
        if (aggregate.isEmpty()) {
            chart.add(emptyState(t("Nenhum dado encontrado para os filtros selecionados.")));
            return 1;
        }

        List<Map.Entry<String, Double>> all = new ArrayList<>(aggregate.entrySet());
        int pageSize = 15;
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) pageSize));
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, all.size());
        Map<String, Double> pageValues = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (int index = start; index < end; index++) {
            var entry = all.get(index);
            pageValues.put(entry.getKey(), entry.getValue());
            labels.put(entry.getKey(), displayLabel(entry.getKey(), dimension));
        }

        Div chartLine = new Div();
        chartLine.addClassName("gp-refugo-chart-line");
        InteractiveBarChart barChart = new InteractiveBarChart(t("Análise por " + dimension),
                pageValues, labels, selectedKey, locale.get(), selectionChanged, contextSelected);
        if ("Setor".equals(dimension)) {
            barChart.addClassNames("gp-refugo-sector-chart-v064", "gp-refugo-sector-chart-v068",
                    "gp-refugo-sector-chart-v069");
        }
        alignChartTitle(barChart);
        contextMenuInstaller.accept(barChart);
        chartLine.add(barChart);
        chart.add(chartLine, pagination(page, totalPages, pageChanged));
        if ("Setor".equals(dimension)) {
            renderTopReasonsBySector(chart, rows, "Setor", selectedKey, filteredSectors);
        }
        return page;
    }

    void renderTopReasonsBySector(Div host, List<RefugoRecord> rows,
                                  String selectedDimension, String selectedKey,
                                  Collection<String> filteredSectors) {
        String selectedSector = null;
        if (Objects.equals(selectedDimension, "Setor") && selectedKey != null && !selectedKey.isBlank()) {
            selectedSector = selectedKey;
        } else if (filteredSectors.size() == 1) {
            selectedSector = filteredSectors.iterator().next();
        }
        final String sector = selectedSector;
        List<RefugoRecord> scope = sector == null ? rows : rows.stream()
                .filter(row -> sector.equalsIgnoreCase(row.sector())).toList();
        H3 title = new H3(t("Top 5 Motivos"));
        title.addClassName("gp-subsection-title");
        Span caption = new Span(sector == null
                ? t("Ranking geral • valor em Kg e participação no total do recorte atual")
                : t("Ranking do setor") + " " + sector + " • "
                        + t("valor em Kg e participação no total do setor"));
        caption.addClassName("gp-caption");
        host.add(title, caption, ranking(scope, sector));
    }

    void renderTopReasonsByPeriod(Div host, List<RefugoRecord> rows, boolean monthly) {
        String dimension = monthly ? "Comparativo Mensal" : "Comparativo Anual";
        List<String> periods = rows.stream().map(row -> scraps.analysisKey(row, dimension))
                .distinct().sorted().toList();
        H3 title = new H3(t("Top 5 motivos por " + (monthly ? "mês" : "ano")));
        title.addClassName("gp-subsection-title");
        Span caption = new Span(t("Ranking independente em cada período • valor em Kg e participação no total do período"));
        caption.addClassName("gp-caption");
        Div table = new Div();
        table.addClassName("gp-period-ranking");
        table.getStyle().set("--gp-period-count", String.valueOf(periods.size()));
        Div header = new Div(new Span(t("Ranking")));
        header.addClassName("gp-period-ranking-row");
        for (String period : periods) header.add(new Span(displayLabel(period, dimension)));
        table.add(header);

        Map<String, Map<String, Double>> byPeriod = new LinkedHashMap<>();
        Map<String, Double> totals = new LinkedHashMap<>();
        for (String period : periods) {
            List<RefugoRecord> scope = rows.stream()
                    .filter(row -> Objects.equals(scraps.analysisKey(row, dimension), period)).toList();
            byPeriod.put(period, scraps.aggregate(scope, "Motivo"));
            totals.put(period, scraps.totalKg(scope));
        }
        for (int rank = 1; rank <= 5; rank++) {
            Div row = new Div(new Span(rank + "º"));
            row.addClassName("gp-period-ranking-row");
            for (String period : periods) {
                List<Map.Entry<String, Double>> entries = new ArrayList<>(byPeriod.get(period).entrySet());
                if (entries.size() < rank) {
                    row.add(new Span("—"));
                    continue;
                }
                var entry = entries.get(rank - 1);
                String[] parts = entry.getKey().split("¦", -1);
                String code = parts.length > 0 ? parts[0] : "";
                String sector = parts.length > 1 ? parts[1] : "";
                String motive = parts.length > 2 ? parts[2] : entry.getKey();
                double percentage = totals.get(period) > 0 ? entry.getValue() / totals.get(period) * 100.0 : 0;
                row.add(new Span(t(motive).toUpperCase(locale.get()) + " · " + sector + " · " + code
                        + " · " + formatOneDecimal.apply(entry.getValue()) + " kg ("
                        + formatOneDecimal.apply(percentage) + "%)"));
            }
            table.add(row);
        }
        host.add(title, caption, table);
    }

    void renderComparison(Div host, List<RefugoRecord> rows, boolean monthly, String selectedKey,
                          Consumer<String> selectionChanged, Consumer<String> contextSelected,
                          Consumer<InteractiveBarChart> contextMenuInstaller) {
        String dimension = monthly ? "Comparativo Mensal" : "Comparativo Anual";
        Map<String, Double> totals = rows.stream().collect(Collectors.groupingBy(
                row -> scraps.analysisKey(row, dimension), LinkedHashMap::new,
                Collectors.summingDouble(RefugoRecord::scrapKg)));
        totals = totals.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> Norm.round(entry.getValue(), 3), (left, right) -> left, LinkedHashMap::new));
        if (totals.size() < 2) {
            host.add(emptyState(t("O comparativo requer pelo menos 2 períodos nos dados filtrados.")));
            return;
        }

        Map<String, String> labels = new LinkedHashMap<>();
        totals.keySet().forEach(key -> labels.put(key, displayLabel(key, dimension)));
        Div line = new Div();
        line.addClassName("gp-refugo-chart-line");
        InteractiveBarChart comparison = new InteractiveBarChart(
                t(monthly ? "Análise por mês" : "Análise por ano"), totals, labels,
                monthly ? selectedKey : null, locale.get(),
                monthly ? selectionChanged : null, monthly ? contextSelected : null);
        alignChartTitle(comparison);
        if (monthly) contextMenuInstaller.accept(comparison);
        line.add(comparison);
        host.add(line);

        if (monthly) {
            List<Map.Entry<String, Double>> ordered = new ArrayList<>(totals.entrySet());
            var last = ordered.get(ordered.size() - 1);
            var previous = ordered.get(ordered.size() - 2);
            var min = ordered.stream().min(Map.Entry.comparingByValue()).orElse(last);
            var max = ordered.stream().max(Map.Entry.comparingByValue()).orElse(last);
            double variation = previous.getValue() == 0 ? 0
                    : (last.getValue() - previous.getValue()) / previous.getValue() * 100.0;
            Div metrics = new Div(
                    kpi(t("Último mês"), formatDecimal.apply(last.getValue()) + " kg · "
                            + signed(variation) + "%"),
                    kpi(t("Menor refugo"), labels.get(min.getKey()) + " · "
                            + formatDecimal.apply(min.getValue()) + " kg"),
                    kpi(t("Maior refugo"), labels.get(max.getKey()) + " · "
                            + formatDecimal.apply(max.getValue()) + " kg"));
            metrics.addClassNames("gp-kpis", "gp-comparison-kpis");
            host.add(metrics);
        }
        renderTopReasonsByPeriod(host, rows, monthly);
    }

    void renderRecent(List<RefugoRecord> rows) {
        recent.removeAll();
        List<RefugoRecord> recentRows = rows.stream()
                .sorted(Comparator.comparingLong(RefugoRecord::erpId).reversed())
                .limit(20)
                .toList();

        Grid<RefugoRecord> grid = new Grid<>(RefugoRecord.class, false);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(row -> Norm.br(row.productiveDate())).setHeader(t("Data"))
                .setWidth("108px").setFlexGrow(0);
        grid.addColumn(ScrapAnalysisPage::loadTime).setHeader(t("Hora")).setAutoWidth(true);
        grid.addColumn(RefugoRecord::orderNumber).setHeader(t("Nº OP"))
                .setWidth("84px").setFlexGrow(0);
        grid.addColumn(RefugoRecord::machine).setHeader(t("Máquina")).setAutoWidth(true);
        grid.addColumn(RefugoRecord::product).setHeader(t("Código Produto"))
                .setWidth("140px").setFlexGrow(0);
        grid.addColumn(RefugoRecord::shift).setHeader(t("Turno")).setAutoWidth(true);
        grid.addColumn(row -> t(nonBlank(row.motive()))).setHeader(t("Motivo")).setAutoWidth(true);
        grid.addColumn(RefugoRecord::operator).setHeader(t("Lançado por")).setAutoWidth(true);
        grid.addColumn(row -> formatDecimal.apply(row.scrapKg())).setHeader(t("Refugo (Kg)"))
                .setAutoWidth(true);
        grid.setItems(recentRows);
        grid.setAllRowsVisible(true);
        grid.addClassName("gp-refugo-recent-grid");

        Component body = recentRows.isEmpty() ? new Span(t("Nenhum lançamento encontrado.")) : grid;
        if (body instanceof Span span) span.addClassName("gp-caption");
        Details expander = new Details(t("Lançamentos recentes"), body);
        expander.setOpened(false);
        expander.addClassName("gp-refugo-recent-expander");
        recent.add(expander);
    }

    void renderSelectedLaunches(List<RefugoRecord> rows, String dimension, String key) {
        List<RefugoRecord> selected = rows.stream()
                .filter(row -> scraps.matches(row, dimension, key)).toList();
        if (selected.isEmpty()) return;
        H3 title = new H3(t("Lançamentos") + " · " + displayLabel(key, dimension));
        title.addClassName("gp-subsection-title");
        Grid<RefugoRecord> grid = new Grid<>(RefugoRecord.class, false);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(row -> Norm.br(row.productiveDate())).setHeader(t("Data"))
                .setWidth("108px").setFlexGrow(0);
        grid.addColumn(ScrapAnalysisPage::loadTime).setHeader(t("Hora"));
        grid.addColumn(RefugoRecord::orderNumber).setHeader(t("Nº OP"))
                .setWidth("84px").setFlexGrow(0);
        grid.addColumn(RefugoRecord::sector).setHeader(t("Setor"));
        grid.addColumn(RefugoRecord::machine).setHeader(t("Máquina"));
        grid.addColumn(RefugoRecord::product).setHeader(t("Código Produto"))
                .setWidth("140px").setFlexGrow(0);
        grid.addColumn(RefugoRecord::description).setHeader(t("Descrição"));
        grid.addColumn(RefugoRecord::shift).setHeader(t("Turno"));
        grid.addColumn(RefugoRecord::motive).setHeader(t("Motivo"));
        grid.addColumn(RefugoRecord::operator).setHeader(t("Lançado por"));
        grid.addColumn(row -> formatDecimal.apply(row.scrapKg())).setHeader(t("Refugo (Kg)"));
        grid.setItems(selected);
        configureAdaptiveGridHeight(grid, selected.size(), 18, 560);
        details.add(title, grid);
    }

    void renderDescriptionDetails(List<RefugoRecord> rows, String key, LocalDate start, LocalDate end) {
        List<RefugoRecord> selected = rows.stream()
                .filter(row -> Objects.equals(scraps.analysisKey(row, "Descrição"), key))
                .toList();
        if (selected.isEmpty()) return;

        H3 title = new H3(t("Detalhes do item"));
        title.addClassName("gp-subsection-title");
        Span description = new Span(nonBlank(selected.get(0).description()));
        description.addClassName("gp-detail-main");
        List<String> products = selected.stream().map(RefugoRecord::product)
                .filter(value -> value != null && !value.isBlank()).distinct().toList();
        List<String> clients = selected.stream().map(RefugoRecord::client)
                .filter(value -> value != null && !value.isBlank()).distinct().toList();
        Div captions = new Div();
        captions.addClassName("gp-detail-captions");
        if (!products.isEmpty()) captions.add(new Span(t("Produto(s)") + ": " + String.join(", ", products)));
        if (!clients.isEmpty()) captions.add(new Span(t("Cliente(s)") + ": " + String.join(", ", clients)));
        details.add(title, description, captions);

        boolean multiDay = start != null && end != null && !start.equals(end);
        boolean multiProducts = products.size() > 1;
        List<String> sectors = selected.stream().map(RefugoRecord::sector)
                .filter(Objects::nonNull).distinct().sorted().toList();
        for (String sector : sectors) {
            List<RefugoRecord> sectorRows = selected.stream()
                    .filter(row -> Objects.equals(row.sector(), sector)).toList();
            Span sectorTitle = new Span(t("SETOR") + ": " + sector + " ("
                    + formatDecimal.apply(scraps.totalKg(sectorRows)) + " " + t("Kg") + ")");
            sectorTitle.addClassName("gp-refugo-sector-detail-title");
            details.add(sectorTitle);

            Map<String, List<RefugoRecord>> grouped = new LinkedHashMap<>();
            for (RefugoRecord row : sectorRows) {
                StringBuilder groupKey = new StringBuilder();
                if (multiDay) groupKey.append(row.productiveDate()).append('¦');
                groupKey.append(row.orderNumber());
                if (multiProducts) groupKey.append('¦').append(row.product());
                grouped.computeIfAbsent(groupKey.toString(), ignored -> new ArrayList<>()).add(row);
            }

            List<ScrapDetailRow> detailRows = new ArrayList<>();
            for (List<RefugoRecord> group : grouped.values()) {
                RefugoRecord first = group.get(0);
                double kg = group.stream().mapToDouble(RefugoRecord::scrapKg).sum();
                int units = (int) Math.round(group.stream().mapToDouble(RefugoRecord::itemCount).sum());
                double planned = group.stream().mapToDouble(RefugoRecord::plannedQty).max().orElse(0.0);
                detailRows.add(new ScrapDetailRow(multiDay ? Norm.br(first.productiveDate()) : "",
                        first.orderNumber(), multiProducts ? first.product() : "", planned, kg, units,
                        planned > 0 ? units / planned * 100.0 : null));
            }
            if (multiDay) {
                detailRows.sort(Comparator.comparing((ScrapDetailRow row) -> Norm.isoDate(row.date()),
                                Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                        .thenComparing(ScrapDetailRow::order, Comparator.nullsLast(String::compareTo)));
            }

            Grid<ScrapDetailRow> grid = new Grid<>(ScrapDetailRow.class, false);
            grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
            if (multiDay) grid.addColumn(ScrapDetailRow::date).setHeader(t("Data"))
                    .setWidth("108px").setFlexGrow(0);
            grid.addColumn(ScrapDetailRow::order).setHeader(t("Nº OP"))
                    .setWidth("84px").setFlexGrow(0);
            if (multiProducts) grid.addColumn(ScrapDetailRow::product).setHeader(t("Código Produto"))
                    .setWidth("140px").setFlexGrow(0);
            grid.addColumn(row -> formatInteger.apply((int) Math.round(row.planned())))
                    .setHeader(t("Planejado (un)"));
            grid.addColumn(row -> formatDecimal.apply(row.scrapKg())).setHeader(t("Refugo (Kg)"));
            grid.addColumn(row -> formatInteger.apply(row.items())).setHeader(t("Refugo (un)"));
            grid.addColumn(row -> row.lossPct() == null ? "-" : formatDecimal.apply(row.lossPct()) + "%")
                    .setHeader(t("Perda (%)"));
            grid.setItems(detailRows);
            configureAdaptiveGridHeight(grid, detailRows.size(), 14, 500);
            details.add(grid);
        }

        double totalKg = scraps.totalKg(selected);
        int totalItems = (int) Math.round(selected.stream().mapToDouble(RefugoRecord::itemCount).sum());
        Map<String, Double> plannedByOrder = new LinkedHashMap<>();
        for (RefugoRecord row : selected) {
            plannedByOrder.merge(row.orderNumber(), row.plannedQty(), Math::max);
        }
        double totalPlanned = plannedByOrder.values().stream().mapToDouble(Double::doubleValue).sum();
        Double loss = totalPlanned > 0 ? totalItems / totalPlanned * 100.0 : null;
        H3 summaryTitle = new H3(t("Resumo total do item"));
        summaryTitle.addClassName("gp-subsection-title");
        Div summary = new Div(
                kpi(t("Refugo total"), formatDecimal.apply(totalKg) + " " + t("Kg")),
                kpi(t("Unidades refugadas"), formatInteger.apply(totalItems)),
                kpi(t("Planejado das OPs"), formatInteger.apply((int) Math.round(totalPlanned))),
                kpi(t("Perda total"), loss == null ? "-" : formatDecimal.apply(loss) + "%"));
        summary.addClassNames("gp-kpis", "gp-detail-kpis");
        details.add(summaryTitle, summary);

        List<Double> weights = selected.stream().map(RefugoRecord::unitWeightG)
                .filter(value -> value != null && value > 0).distinct().toList();
        String weightText = weights.size() == 1
                ? DisplayFormat.decimal(weights.get(0), 3, locale.get()) + " g"
                : weights.size() > 1 ? t("múltiplos pesos no agrupamento") : "-";
        Span weight = new Span(t("Peso unitário") + ": " + weightText);
        weight.addClassName("gp-caption");
        details.add(weight);
    }

    static String loadTime(RefugoRecord record) {
        String time = Norm.syncTime(record == null ? null : record.firstDetectedAt());
        return time == null || time.isBlank() ? "—" : time;
    }

    static void configureAdaptiveGridHeight(Grid<?> grid, int rowCount, int inlineLimit, int maxHeightPx) {
        if (rowCount <= inlineLimit) {
            grid.setAllRowsVisible(true);
            grid.getStyle().remove("height");
            grid.removeClassName("gp-virtual-grid");
            return;
        }
        grid.setAllRowsVisible(false);
        grid.setHeight(maxHeightPx + "px");
        grid.addClassName("gp-virtual-grid");
    }

    void alignChartTitle(InteractiveBarChart chart) {
        chart.addClassName("gp-refugo-chart-title-aligned-v070");
        chart.addAttachListener(event -> chart.getElement().executeJs("""
            const apply=()=>{
              const title=this.querySelector('.gp-refugo-chart-title');
              if(title){
                title.style.setProperty('left','48px','important');
                title.style.setProperty('top','0px','important');
              }
            };
            apply();
            requestAnimationFrame(apply);
        """));
    }

    String displayLabel(String key, String dimension) {
        if (key == null) return t("NÃO INFORMADO");
        if ("Motivo".equals(dimension)) {
            String[] parts = key.split("¦", -1);
            return parts.length >= 3 ? t(parts[2]) : t(key);
        }
        if ("Comparativo Mensal".equals(dimension)) {
            try {
                return YearMonth.parse(key).format(DateTimeFormatter.ofPattern("MMM/yyyy", locale.get()));
            } catch (RuntimeException ignored) {
                // Chaves externas continuam visíveis caso não estejam no formato mensal esperado.
            }
        }
        return t(key);
    }

    private ScrapRankingTable ranking(List<RefugoRecord> rows, String sector) {
        return new ScrapRankingTable(scraps.aggregate(rows, "Motivo"), scraps.totalKg(rows), sector,
                this::t, formatOneDecimal, locale.get());
    }

    private Div pagination(int page, int totalPages, IntConsumer pageChanged) {
        Div holder = new Div();
        holder.addClassName("gp-refugo-pagination");
        if (totalPages <= 1) return holder;
        Button previous = new Button(t("← Anterior"));
        previous.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        previous.setEnabled(page > 1);
        previous.addClickListener(event -> pageChanged.accept(page - 1));
        Span label = new Span(t("Página") + " " + page + " " + t("de") + " " + totalPages);
        Button next = new Button(t("Próximo →"));
        next.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        next.setEnabled(page < totalPages);
        next.addClickListener(event -> pageChanged.accept(page + 1));
        holder.add(previous, label, next);
        return holder;
    }

    private Div emptyState(String message) {
        Div state = new Div(new Span(message));
        state.addClassName("gp-empty-state");
        return state;
    }

    private String signed(double value) {
        return (value > 0 ? "+" : "") + formatOneDecimal.apply(value);
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? "NÃO INFORMADO" : value;
    }

    private record ScrapDetailRow(String date, String order, String product, double planned,
                                  double scrapKg, int items, Double lossPct) { }

    private void rebuild(Popover dropdown) {
        removeAll();
        if (dropdown == null) add(titleRow, toolbar, kpis, tabs, chart, details, recent);
        else add(titleRow, toolbar, dropdown, kpis, tabs, chart, details, recent);
    }

    private Tab tab(String label, String dimension) {
        Tab tab = new Tab(t(label));
        dimensions.put(tab, dimension);
        return tab;
    }

    private String selectedDimension() {
        return dimensions.getOrDefault(tabs.getSelectedTab(), "Setor");
    }

    private void configureFilterButton() {
        filter.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        filter.addClassName("gp-filter-button");
        filter.setAriaLabel(t("Filtros"));
        filter.setTooltipText(t("Filtros")).withPosition(Tooltip.TooltipPosition.TOP);
    }

    private void installScrollPreservation() {
        tabs.addAttachListener(event -> tabs.getElement().executeJs("""
            if(!this.__gpKeepPageV065){
              this.__gpKeepPageV065=true;
              const capture=()=>{
                window.__gpScrapScrollV065=window.scrollY;
                window.__gpScrapTabsTopV065=this.getBoundingClientRect().top;
              };
              this.addEventListener('pointerdown',capture,{capture:true});
              this.addEventListener('selected-changed',capture,{capture:true});
            }
        """));
    }

    private void restoreScroll() {
        tabs.getElement().executeJs("""
            const restore=()=>{
              const y=Number(window.__gpScrapScrollV065);
              if(Number.isFinite(y)) window.scrollTo(window.scrollX,y);
            };
            restore();
            requestAnimationFrame(()=>{restore();requestAnimationFrame(restore);});
            setTimeout(restore,60);
        """);
    }

    private Div kpi(String label, String value) {
        Div item = new Div(new Span(label), new H3(value));
        item.addClassName("gp-kpi");
        return item;
    }

    private Div kpiWithCaption(String label, String value, String caption) {
        Div item = kpi(label, value);
        if (caption != null && !caption.isBlank()) {
            Span note = new Span(caption);
            note.addClassName("gp-kpi-caption");
            item.add(note);
        }
        return item;
    }

    private String t(String text) { return translate.apply(text); }
}
