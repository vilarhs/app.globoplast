package br.com.globoplast.oee.view;
import static br.com.globoplast.oee.view.FilterControls.multiSelect;
import static br.com.globoplast.oee.view.FilterControls.forceUppercaseSectorFilter;
import br.com.globoplast.oee.config.AppConfig;
import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.service.LaunchService;
import br.com.globoplast.oee.service.I18n;
import br.com.globoplast.oee.util.DisplayFormat;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.shared.Tooltip;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Estado dos filtros e montagem dos resumos diário e mensal. */
final class ProductionSummaryScreen {
    private final LaunchService launches;
    private final LaunchCells launchCells;
    private final Supplier<String> language;
    private final Supplier<LocalDate[]> bounds;
    private final BiFunction<LocalDate, LocalDate, List<LaunchRecord>> data;
    private final Supplier<Grid<LaunchRecord>> grid;
    private int monthLimit = AppConfig.PAGE_SIZE;
    private LocalDate summaryDayDate = LocalDate.now(AppConfig.ZONE).minusDays(1);
    private final Set<String> summaryDaySectors = new LinkedHashSet<>();
    private final Set<String> summaryDayMachines = new LinkedHashSet<>();
    private final Set<String> summaryDayShifts = new LinkedHashSet<>();
    private YearMonth summaryMonth = YearMonth.now(AppConfig.ZONE);
    private final Set<String> summaryMonthSectors = new LinkedHashSet<>();
    private final Set<String> summaryMonthMachines = new LinkedHashSet<>();
    private final Set<String> summaryMonthShifts = new LinkedHashSet<>();

    ProductionSummaryScreen(LaunchService launches, LaunchCells launchCells, Supplier<String> language,
                            Supplier<LocalDate[]> bounds,
                            BiFunction<LocalDate, LocalDate, List<LaunchRecord>> data,
                            Supplier<Grid<LaunchRecord>> grid) {
        this.launches = launches;
        this.launchCells = launchCells;
        this.language = language;
        this.bounds = bounds;
        this.data = data;
        this.grid = grid;
    }

    private String t(String value) { return I18n.tr(language.get(), value); }
    private Locale locale() { return Locale.forLanguageTag(language.get()); }
    private String formatInt(long value) { return DisplayFormat.integer(value, locale()); }
    private String format(double value) { return DisplayFormat.decimal(value, 2, locale()); }
    private String format1(double value) { return DisplayFormat.decimal(value, 1, locale()); }
    private LocalDate[] cachedLaunchBounds() { return bounds.get(); }
    private List<LaunchRecord> cachedLaunchData(LocalDate start, LocalDate end) { return data.apply(start, end); }
    private Grid<LaunchRecord> launchGrid() { return grid.get(); }
    private Button searchFilterButton() { return FilterControls.searchButton(); }
    private void updateFilterButton(Button button, boolean active) { FilterControls.updateButton(button, active); }

    private Popover summaryFilterDropdown(Button target, Div fields, Runnable clearAction) {
        Popover p = new Popover();
        p.setTarget(target);
        p.setPosition(PopoverPosition.BOTTOM_END);
        p.setWidth("min(320px, calc(100vw - 24px))");
        p.setModal(false);
        p.setBackdropVisible(false);
        p.setCloseOnOutsideClick(true);
        p.setCloseOnEsc(true);
        p.setAriaLabel(t("Filtros"));
        p.addClassNames("gp-filter-popover", "gp-summary-filter-popover-v047");

        Button clear = new Button(t("Limpar filtros"), e -> {
            clearAction.run();
            p.setOpened(false);
        });
        clear.setWidthFull();
        clear.addClassName("gp-filter-clear");

        Div actions = new Div(clear);
        actions.addClassName("gp-filter-dropdown-actions");
        Div body = new Div(fields, actions);
        body.addClassNames("gp-filter-dropdown", "gp-summary-filter-dropdown-v047");
        p.add(body);
        return p;
    }

    private void refreshDaySummary(
            DateRangePicker date,
            MultiSelectComboBox<String> sector,
            MultiSelectComboBox<String> machine,
            Button filter,
            LocalDate defaultDate,
            ProductionSummaryPage summaryPage,
            boolean[] adjusting) {
        if (adjusting[0]) return;
        LocalDate selected = date.getValue();
        if (selected == null) return;
        summaryDayDate = selected;
        List<LaunchRecord> source = cachedLaunchData(selected, selected);
        List<LaunchRecord> sourceForShift = ProductionSummary.rowsForShifts(source, summaryDayShifts);
        List<String> sectors = sourceForShift.stream().map(LaunchRecord::getSector)
                .filter(Objects::nonNull).filter(v -> !v.isBlank())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        adjusting[0] = true;
        try {
            Set<String> validSectors = new LinkedHashSet<>(summaryDaySectors);
            validSectors.removeIf(v -> !sectors.contains(v));
            sector.setItems(sectors);
            sector.setValue(validSectors);
            FilterSelections.replace(summaryDaySectors, validSectors);

            List<LaunchRecord> forMachines = sourceForShift;
            if (!summaryDaySectors.isEmpty()) {
                forMachines = forMachines.stream()
                        .filter(r -> summaryDaySectors.contains(r.getSector())).toList();
            }
            List<String> machines = forMachines.stream().map(LaunchRecord::getMachine)
                    .filter(Objects::nonNull).filter(v -> !v.isBlank())
                    .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
            Set<String> validMachines = new LinkedHashSet<>(summaryDayMachines);
            validMachines.removeIf(v -> !machines.contains(v));
            machine.setItems(machines);
            machine.setValue(validMachines);
            FilterSelections.replace(summaryDayMachines, validMachines);
        } finally {
            adjusting[0] = false;
        }

        updateFilterButton(filter, !Objects.equals(summaryDayDate, defaultDate)
                || !summaryDaySectors.isEmpty() || !summaryDayMachines.isEmpty() || !summaryDayShifts.isEmpty());
        List<LaunchRecord> rows = sourceForShift;
        if (!summaryDaySectors.isEmpty()) {
            rows = rows.stream().filter(r -> summaryDaySectors.contains(r.getSector())).toList();
        }
        if (!summaryDayMachines.isEmpty()) {
            rows = rows.stream().filter(r -> summaryDayMachines.contains(r.getMachine())).toList();
        }
        List<LaunchRecord> summary = ProductionSummary.daily(rows);
        if (summary.isEmpty()) {
            summaryPage.showEmpty(t("Nenhum lançamento encontrado para os filtros selecionados."));
            return;
        }
        summaryPage.show(summary, launches.newestFirst(rows));
    }

    void renderDay(Div content) {
        content.removeAll();
        Div page = new Div();
        page.addClassNames("gp-summary-page", "gp-summary-day-page", "gp-original-summary");

        H2 title = new H2(t("Resumo Diário da Produção"));
        title.addClassName("gp-section-title");
        Button filter = searchFilterButton();
        filter.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        filter.addClassNames("gp-filter-button", "gp-summary-filter-trigger-v047");
        filter.setAriaLabel(t("Filtros"));
        filter.setTooltipText(t("Filtros")).withPosition(Tooltip.TooltipPosition.TOP);
        Div titleRow = new Div(title, filter);
        titleRow.addClassNames("gp-title-row", "gp-summary-title-row-v047");

        LocalDate[] bounds = cachedLaunchBounds();
        LocalDate yesterday = LocalDate.now(AppConfig.ZONE).minusDays(1);
        LocalDate defaultDate = yesterday.isBefore(bounds[0]) ? bounds[0] : (yesterday.isAfter(bounds[1]) ? bounds[1] : yesterday);
        if (summaryDayDate == null || summaryDayDate.isBefore(bounds[0]) || summaryDayDate.isAfter(bounds[1])) summaryDayDate = defaultDate;

        DateRangePicker date = new DateRangePicker(
                t("Escolha a Data"), summaryDayDate, summaryDayDate,
                bounds[0], bounds[1], language.get(), this::t, null, true
        );
        MultiSelectComboBox<String> sector = multiSelect(
                t("Filtrar por Setor"), List.of(), summaryDaySectors, t("Todos")
        );
        forceUppercaseSectorFilter(sector);
        MultiSelectComboBox<String> machine = multiSelect(
                t("Filtrar por Máquina"), List.of(), summaryDayMachines, t("Todas")
        );
        MultiSelectComboBox<String> shift = multiSelect(
                t("Filtrar por Turno"), List.of("A", "B", "C"), summaryDayShifts, t("Todos")
        );
        Div filterFields = new Div(date, sector, machine, shift);
        filterFields.addClassNames("gp-filter-dropdown-grid", "gp-summary-filter-fields-v047");

        boolean[] adjusting={false};
        ProductionSummaryPage summaryPage = new ProductionSummaryPage(
                ProductionSummaryPage.Period.DAY, this::t, this::formatInt, this::format,
                this::format1, this::locale, record -> launchCells.oee(record, 1), this::launchGrid,
                AppConfig.PAGE_SIZE, ignored -> { });

        Runnable refresh = () -> refreshDaySummary(date, sector, machine, filter, defaultDate, summaryPage, adjusting);
        date.setChangeListener(() -> {
            if (!adjusting[0]) {
                summaryDayDate = date.getValue();
                summaryPage.resetLimits();
                refresh.run();
            }
        });
        sector.addValueChangeListener(e->{if(!adjusting[0]){FilterSelections.replace(summaryDaySectors,e.getValue());summaryPage.resetLimits();refresh.run();}});
        machine.addValueChangeListener(e->{if(!adjusting[0]){FilterSelections.replace(summaryDayMachines,e.getValue());summaryPage.resetLimits();refresh.run();}});
        shift.addValueChangeListener(e->{if(!adjusting[0]){FilterSelections.replace(summaryDayShifts,e.getValue());summaryPage.resetLimits();refresh.run();}});

        Popover filterDropdown = summaryFilterDropdown(filter, filterFields, () -> {
            adjusting[0]=true;
            try {
                summaryDayDate=defaultDate;
                summaryDaySectors.clear();
                summaryDayMachines.clear();
                summaryDayShifts.clear();
                summaryPage.resetLimits();
                date.setValue(defaultDate);
                sector.clear();
                machine.clear();
                shift.clear();
            } finally { adjusting[0]=false; }
            refresh.run();
        });

        page.add(titleRow,filterDropdown,summaryPage.result());
        content.add(page, summaryPage.entriesMore());
        refresh.run();
    }

    private void refreshMonthSummary(
            Select<YearMonth> month,
            MultiSelectComboBox<String> sector,
            MultiSelectComboBox<String> machine,
            Button filter,
            YearMonth maxMonth,
            ProductionSummaryPage summaryPage,
            boolean[] adjusting) {
        if (adjusting[0]) return;
        YearMonth selected = month.getValue();
        if (selected == null) return;
        summaryMonth = selected;
        List<LaunchRecord> source = cachedLaunchData(selected.atDay(1), selected.atEndOfMonth());
        List<LaunchRecord> sourceForShift = ProductionSummary.rowsForShifts(source, summaryMonthShifts);
        List<String> sectors = sourceForShift.stream().map(LaunchRecord::getSector)
                .filter(Objects::nonNull).filter(v -> !v.isBlank())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        adjusting[0] = true;
        try {
            Set<String> validSectors = new LinkedHashSet<>(summaryMonthSectors);
            validSectors.retainAll(sectors);
            sector.setItems(sectors);
            sector.setValue(validSectors);
            FilterSelections.replace(summaryMonthSectors, validSectors);
            List<String> machines = sourceForShift.stream()
                    .filter(r -> summaryMonthSectors.isEmpty() || summaryMonthSectors.contains(r.getSector()))
                    .map(LaunchRecord::getMachine).filter(Objects::nonNull).filter(v -> !v.isBlank())
                    .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
            Set<String> validMachines = new LinkedHashSet<>(summaryMonthMachines);
            validMachines.retainAll(machines);
            machine.setItems(machines);
            machine.setValue(validMachines);
            FilterSelections.replace(summaryMonthMachines, validMachines);
        } finally {
            adjusting[0] = false;
        }
        updateFilterButton(filter, !Objects.equals(summaryMonth, maxMonth)
                || !summaryMonthSectors.isEmpty() || !summaryMonthMachines.isEmpty() || !summaryMonthShifts.isEmpty());
        List<LaunchRecord> filtered = sourceForShift;
        if (!summaryMonthSectors.isEmpty()) {
            filtered = filtered.stream().filter(r -> summaryMonthSectors.contains(r.getSector())).toList();
        }
        if (!summaryMonthMachines.isEmpty()) {
            filtered = filtered.stream().filter(r -> summaryMonthMachines.contains(r.getMachine())).toList();
        }
        filtered = launches.newestFirst(filtered);
        List<LaunchRecord> summary = ProductionSummary.monthly(filtered);
        if (filtered.isEmpty()) {
            summaryPage.showEmpty(t("Nenhum lançamento encontrado para o mês com os filtros selecionados."));
            return;
        }
        summaryPage.show(summary, filtered);
    }

    void renderMonth(Div content) {
        content.removeAll();
        Div page=new Div();
        page.addClassNames("gp-summary-page","gp-summary-month-page","gp-original-summary");
        H2 title=new H2(t("Resumo Mensal de Eficiência")); title.addClassName("gp-section-title");
        Button filter = searchFilterButton();
        filter.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        filter.addClassNames("gp-filter-button", "gp-summary-filter-trigger-v047");
        filter.setAriaLabel(t("Filtros"));
        filter.setTooltipText(t("Filtros")).withPosition(Tooltip.TooltipPosition.TOP);
        Div titleRow = new Div(title, filter);
        titleRow.addClassNames("gp-title-row", "gp-summary-title-row-v047");

        LocalDate[] bounds=cachedLaunchBounds();
        YearMonth minMonth=YearMonth.from(bounds[0]), maxMonth=YearMonth.from(bounds[1]);
        List<YearMonth> months=new ArrayList<>();
        for(YearMonth cursor=maxMonth;!cursor.isBefore(minMonth);cursor=cursor.minusMonths(1))months.add(cursor);
        if(summaryMonth==null||summaryMonth.isBefore(minMonth)||summaryMonth.isAfter(maxMonth))summaryMonth=maxMonth;

        Select<YearMonth> month=new Select<>();
        month.setLabel(t("Selecione o Mês de Análise")); month.setItems(months);
        month.setItemLabelGenerator(m->m.format(DateTimeFormatter.ofPattern("MMMM/yyyy",locale()))); month.setValue(summaryMonth);
        MultiSelectComboBox<String> sector=multiSelect(t("Filtrar por Setor"),List.of(),summaryMonthSectors,t("Todos"));
        forceUppercaseSectorFilter(sector);
        MultiSelectComboBox<String> machine=multiSelect(t("Filtrar por Máquina"),List.of(),summaryMonthMachines,t("Todas"));
        MultiSelectComboBox<String> shift=multiSelect(t("Filtrar por Turno"),List.of("A","B","C"),summaryMonthShifts,t("Todos"));
        Div filterFields=new Div(month,sector,machine,shift);
        filterFields.addClassNames("gp-filter-dropdown-grid", "gp-summary-filter-fields-v047");
        boolean[] adjusting={false};
        ProductionSummaryPage summaryPage = new ProductionSummaryPage(
                ProductionSummaryPage.Period.MONTH, this::t, this::formatInt, this::format,
                this::format1, this::locale, record -> launchCells.oee(record, 1), this::launchGrid,
                monthLimit, value -> monthLimit = value);
        Runnable refresh = () -> refreshMonthSummary(month, sector, machine, filter, maxMonth, summaryPage, adjusting);
        month.addValueChangeListener(e->{if(!adjusting[0]){summaryMonth=e.getValue();summaryPage.resetLimits();refresh.run();}});
        sector.addValueChangeListener(e->{if(!adjusting[0]){FilterSelections.replace(summaryMonthSectors,e.getValue());summaryPage.resetLimits();refresh.run();}});
        machine.addValueChangeListener(e->{if(!adjusting[0]){FilterSelections.replace(summaryMonthMachines,e.getValue());summaryPage.resetLimits();refresh.run();}});
        shift.addValueChangeListener(e->{if(!adjusting[0]){FilterSelections.replace(summaryMonthShifts,e.getValue());summaryPage.resetLimits();refresh.run();}});

        Popover filterDropdown = summaryFilterDropdown(filter, filterFields, () -> {
            adjusting[0]=true;
            try {
                summaryMonth=maxMonth;
                summaryMonthSectors.clear();
                summaryMonthMachines.clear();
                summaryMonthShifts.clear();
                summaryPage.resetLimits();
                month.setValue(maxMonth);
                sector.clear();
                machine.clear();
                shift.clear();
            } finally { adjusting[0]=false; }
            refresh.run();
        });

        page.add(titleRow,filterDropdown,summaryPage.result());
        content.add(page,summaryPage.entriesMore());
        refresh.run();
    }

}
