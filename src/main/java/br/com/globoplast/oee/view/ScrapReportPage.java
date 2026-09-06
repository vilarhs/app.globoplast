package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.RefugoRecord;
import br.com.globoplast.oee.service.RefugoService;
import br.com.globoplast.oee.util.DisplayFormat;
import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;

final class ScrapReportPage extends Div {
    private final Function<String, String> translate;
    private final Supplier<Locale> locale;
    private final RefugoService scraps;
    private final Div kpis = new Div();
    private final Div sectors = new Div();
    private final Div reasons = new Div();
    private final Div comparison = new Div();
    private H2 scrapReportTitle;
    private H2 scrapReportPrintTitle;
    private LocalDate scrapStart;
    private LocalDate scrapEnd;

    ScrapReportPage(Function<String, String> translate, Supplier<Locale> locale, RefugoService scraps,
                    String initialSearch, Button filter, Function<Button, Popover> filterDropdownFactory,
                    Consumer<String> searchChanged) {
        this.translate = translate;
        this.locale = locale;
        this.scraps = scraps;

        scrapReportTitle = new H2();
        scrapReportTitle.addClassName("gp-section-title");
        Div titleRow = new Div(scrapReportTitle);
        titleRow.addClassNames("gp-title-row", "gp-title-row-static");

        TextField search = new TextField(t("Pesquisar refugo"));
        search.setPlaceholder(t("Ordem, produto ou descrição"));
        search.setClearButtonVisible(true);
        search.setValue(initialSearch);
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.setValueChangeTimeout(100);

        filter.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        filter.addClassName("gp-filter-button");
        filter.setAriaLabel(t("Filtros"));
        filter.setTooltipText(t("Filtros")).withPosition(Tooltip.TooltipPosition.TOP);

        Button exportPdf = new Button(t("Exportar PDF"), VaadinIcon.PRINT.create());
        exportPdf.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        exportPdf.addClassName("gp-scrap-report-export-v117");
        exportPdf.setTooltipText(t("Abre a impressão para salvar o relatório como PDF"))
                .withPosition(Tooltip.TooltipPosition.TOP);
        exportPdf.addClickListener(e -> exportScrapReportPdf());

        Div toolbar = new Div(search, filter, exportPdf);
        toolbar.addClassNames("gp-toolbar", "gp-tab-controls", "gp-search-filter-toolbar-v044", "gp-scrap-report-toolbar-v116");
        Popover filterDropdown = filterDropdownFactory.apply(filter);

        Paragraph explanation = new Paragraph(t("Todos os setores de refugo aparecem no relatório, mesmo quando não possuem lançamentos no período."));
        explanation.addClassNames("gp-muted", "gp-scrap-report-explanation-v117");

        Image printLogo = new Image("/images/globoplast-logo.png", "Globoplast");
        printLogo.addClassName("gp-scrap-report-print-logo-v117");
        scrapReportPrintTitle = new H2();
        scrapReportPrintTitle.addClassName("gp-scrap-report-print-title-v139");
        Div printHeader = new Div(scrapReportPrintTitle, printLogo);
        printHeader.addClassName("gp-scrap-report-print-header-v117");

        kpis.setId("scrap-report-kpis");
        kpis.addClassNames("gp-kpis", "gp-scrap-report-kpis-v116");

        comparison.setId("scrap-report-five-month-comparison");
        comparison.addClassName("gp-scrap-report-five-month-comparison-v130");

        sectors.setId("scrap-report-sectors");
        sectors.addClassName("gp-scrap-report-sectors-v116");

        reasons.setId("scrap-report-reasons");
        reasons.addClassName("gp-scrap-report-reasons-v116");

        add(titleRow, toolbar, filterDropdown, explanation, printHeader, kpis, sectors, reasons, comparison);
        addClassName("gp-scrap-report-page-v116");

        search.addValueChangeListener(e -> searchChanged.accept(e.getValue()));
    }

    private void exportScrapReportPdf() {
        String fileName = "Relatorio-Refugo-" + scrapStart + "-a-" + scrapEnd;
        UI.getCurrent().getPage().executeJs(
                """
                (() => {
                    const previousTitle = document.title;
                    const root = document.documentElement;
                    document.title = String($0 || 'Relatorio-Refugo');
                    root.classList.add('gp-print-scrap-report-v117');
                    const restore = () => {
                        root.classList.remove('gp-print-scrap-report-v117');
                        document.title = previousTitle;
                    };
                    window.addEventListener('afterprint', restore, {once:true});
                    window.print();
                })();
                """, fileName);
    }

    void refresh(LocalDate start, LocalDate end, List<RefugoRecord> rows, List<RefugoRecord> comparisonRows) {
        scrapStart = start;
        scrapEnd = end;
        updateScrapReportTitle();
        double totalKg = rows.stream().mapToDouble(RefugoRecord::scrapKg).sum();

        List<ScrapSectorReportRow> sectorRows = new ArrayList<>();
        for (String sector : Norm.scrapSectors()) {
            List<RefugoRecord> scope = rows.stream()
                    .filter(row -> sector.equalsIgnoreCase(row.sector()))
                    .toList();
            double kg = scope.stream().mapToDouble(RefugoRecord::scrapKg).sum();
            double participation = totalKg > 0 ? kg * 100.0 / totalKg : 0.0;
            sectorRows.add(new ScrapSectorReportRow(sector, kg, scope.size(), participation));
        }

        {
            long sectorsWithScrap = sectorRows.stream().filter(row -> row.scrapKg() > 0).count();
            kpis.removeAll();
            kpis.add(
                    kpi(t("Total Refugo"), format(totalKg) + " kg"),
                    kpi(t("Setores com Refugo"), formatInt(sectorsWithScrap)),
                    kpi(t("Setores sem Refugo"), formatInt(sectorRows.size() - sectorsWithScrap)),
                    kpi(t("Total de Lançamentos"), formatInt(rows.size())),
                    kpi(t("Período"), reportPeriodLabel())
            );
        }

        {
            sectors.removeAll();
            H3 sectionTitle = new H3(t("Refugo por Setor"));
            sectionTitle.addClassName("gp-subsection-title");
            Grid<ScrapSectorReportRow> grid = new Grid<>(ScrapSectorReportRow.class, false);
            grid.addClassNames("gp-scrap-report-grid-v116", "gp-admin-grid");
            grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
            grid.addColumn(row -> t(row.sector())).setHeader(t("Setor")).setAutoWidth(true).setFlexGrow(1);
            grid.addColumn(row -> format(row.scrapKg())).setHeader(t("Refugo (kg)")).setAutoWidth(true).setFlexGrow(0);
            grid.addColumn(row -> formatInt(row.launches())).setHeader(t("Total de Lançamentos")).setAutoWidth(true).setFlexGrow(0);
            grid.addColumn(row -> format1(row.participation()) + "%").setHeader(t("Participação (%)")).setAutoWidth(true).setFlexGrow(0);
            grid.setItems(sectorRows);
            grid.setAllRowsVisible(true);
            sectors.add(sectionTitle, grid, scrapReportPrintSectorTableWrapper(sectorRows));
        }

        {
            reasons.removeAll();
            H3 sectionTitle = new H3(t("Top 5 Motivos por Setor (Causadores)"));
            sectionTitle.addClassName("gp-subsection-title");
            Span caption = new Span(t("Ranking independente para os sete setores produtivos definidos no relatório."));
            caption.addClassName("gp-caption");
            Div cards = new Div();
            cards.addClassName("gp-scrap-reason-cards-v116");
            for (String sector : Norm.scrapReasonReportSectors()) {
                List<RefugoRecord> scope = rows.stream()
                        .filter(row -> sector.equalsIgnoreCase(row.sector()))
                        .toList();
                H3 cardTitle = new H3(t(sector));
                cardTitle.addClassName("gp-scrap-reason-title-v116");
                Div card = new Div(cardTitle, new ScrapRankingTable(
                        scraps.aggregate(scope, "Motivo"), scraps.totalKg(scope), sector,
                        this::t, this::format1, locale.get()));
                card.addClassName("gp-scrap-reason-card-v116");
                cards.add(card);
            }
            reasons.add(sectionTitle, caption, cards);
        }

        renderScrapReportSixMonthComparison(comparison, comparisonRows);
    }

    private void renderScrapReportSixMonthComparison(Div host, List<RefugoRecord> comparisonRows) {
        host.removeAll();
        YearMonth endMonth = YearMonth.from(scrapEnd);
        YearMonth firstVisibleMonth = endMonth.minusMonths(5);
        YearMonth comparisonBaseMonth = firstVisibleMonth.minusMonths(1);

        Map<YearMonth, Double> totals = new LinkedHashMap<>();
        for (int index = 0; index < 7; index++) {
            totals.put(comparisonBaseMonth.plusMonths(index), 0.0);
        }
        for (RefugoRecord row : comparisonRows) {
            YearMonth month = YearMonth.from(row.productiveDate());
            if (totals.containsKey(month)) totals.merge(month, row.scrapKg(), Double::sum);
        }

        H3 title = new H3(t("Comparativo dos Últimos 6 Meses"));
        title.addClassName("gp-subsection-title");
        Span caption = new Span(t("Variação em relação ao mês anterior."));
        caption.addClassName("gp-caption");
        Div chart = new Div();
        chart.addClassName("gp-scrap-report-month-bars-v130");

        double maximum = totals.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(comparisonBaseMonth))
                .mapToDouble(Map.Entry::getValue)
                .max()
                .orElse(0.0);
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM/yy", locale());

        for (int index = 1; index < 7; index++) {
            YearMonth month = comparisonBaseMonth.plusMonths(index);
            double current = totals.getOrDefault(month, 0.0);
            double previous = totals.getOrDefault(month.minusMonths(1), 0.0);
            double height = maximum > 0 ? Math.max(current > 0 ? 5.0 : 0.0, current * 100.0 / maximum) : 0.0;

            Span value = new Span(format(current) + " kg");
            value.addClassName("gp-scrap-report-month-value-v130");
            Div fill = new Div();
            fill.addClassName("gp-scrap-report-month-fill-v130");
            fill.getStyle().set("height", String.format(Locale.ROOT, "%.2f%%", height));
            Div track = new Div(fill);
            track.addClassName("gp-scrap-report-month-track-v130");
            Span monthLabel = new Span(month.format(monthFormatter).replace(".", ""));
            monthLabel.addClassName("gp-scrap-report-month-label-v130");

            Span difference = new Span();
            difference.addClassName("gp-scrap-report-month-difference-v130");
            if (previous == 0.0 && current > 0.0) {
                difference.setText(t("Novo"));
                difference.addClassName("gp-new");
            } else {
                double variation = previous == 0.0 ? 0.0 : (current - previous) * 100.0 / previous;
                String indicator = variation > 0.004 ? "▲ " : variation < -0.004 ? "▼ " : "• ";
                difference.setText(indicator + signed1(variation) + "%");
                difference.addClassName(variation > 0.004 ? "gp-up" : variation < -0.004 ? "gp-down" : "gp-neutral");
            }

            Div column = new Div(value, track, monthLabel, difference);
            column.addClassName("gp-scrap-report-month-column-v130");
            chart.add(column);
        }

        host.add(title, caption, chart);
    }

    private Div scrapReportPrintSectorTableWrapper(List<ScrapSectorReportRow> rows) {
        Div wrapper = new Div(scrapReportPrintSectorTable(rows));
        wrapper.addClassName("gp-scrap-report-print-sector-table-wrapper-v124");
        return wrapper;
    }

    private Div scrapReportPrintSectorTable(List<ScrapSectorReportRow> rows) {
        Div table = new Div();
        table.addClassName("gp-scrap-report-print-sector-table-v124");

        Div header = new Div(
                new Span(t("Setor")),
                new Span(t("Refugo (kg)")),
                new Span(t("Lançamentos")),
                new Span(t("Participação (%)"))
        );
        header.addClassNames("gp-scrap-report-print-sector-row-v124", "gp-header");
        table.add(header);

        for (int index = 0; index < rows.size(); index++) {
            ScrapSectorReportRow row = rows.get(index);
            Div line = new Div(
                    new Span(t(row.sector())),
                    new Span(format(row.scrapKg())),
                    new Span(formatInt(row.launches())),
                    new Span(format1(row.participation()) + "%")
            );
            line.addClassName("gp-scrap-report-print-sector-row-v124");
            if (index % 2 == 1) line.addClassName("gp-even");
            table.add(line);
        }
        return table;
    }

    private String reportPeriodLabel() {
        if (Objects.equals(scrapStart, scrapEnd)) return Norm.br(scrapStart);
        return Norm.br(scrapStart) + " – " + Norm.br(scrapEnd);
    }

    private void updateScrapReportTitle() {
        String value = t("Relatório de Refugo") + " — " + reportTitlePeriodLabel();
        if (scrapReportTitle != null) scrapReportTitle.setText(value);
        if (scrapReportPrintTitle != null) scrapReportPrintTitle.setText(value);
    }

    private String reportTitlePeriodLabel() {
        if (scrapStart == null || scrapEnd == null) return "";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale());
        String start = capitalizeMonth(YearMonth.from(scrapStart).format(formatter));
        String end = capitalizeMonth(YearMonth.from(scrapEnd).format(formatter));
        return Objects.equals(YearMonth.from(scrapStart), YearMonth.from(scrapEnd))
                ? start
                : start + " " + t("a") + " " + end;
    }

    private static String capitalizeMonth(String value) {
        if (value == null || value.isBlank()) return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }


    private String t(String value) { return translate.apply(value); }
    private Locale locale() { return locale.get(); }
    private String format(double value) { return DisplayFormat.decimal(value, 2, locale()); }
    private String format1(double value) { return DisplayFormat.decimal(value, 1, locale()); }
    private String formatInt(long value) { return DisplayFormat.integer(value, locale()); }
    private String signed1(double value) { return (value > 0 ? "+" : "") + format1(value); }

    private Div kpi(String label, String value) {
        Div result = new Div(new Span(label), new H3(value));
        result.addClassName("gp-kpi");
        return result;
    }

    private record ScrapSectorReportRow(String sector, double scrapKg, long launches, double participation) {}
}
