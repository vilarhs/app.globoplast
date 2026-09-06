package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.RefugoRecord;
import br.com.globoplast.oee.service.RefugoService;
import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
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

    private Div emptyState(String message) {
        Div state = new Div(new Span(message));
        state.addClassName("gp-empty-state");
        return state;
    }

    private String signed(double value) {
        return (value > 0 ? "+" : "") + formatOneDecimal.apply(value);
    }

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
