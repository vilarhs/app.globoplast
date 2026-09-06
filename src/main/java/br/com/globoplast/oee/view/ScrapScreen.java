package br.com.globoplast.oee.view;

import static br.com.globoplast.oee.view.FilterControls.multiSelect;
import static br.com.globoplast.oee.view.FilterControls.forceUppercaseSectorFilter;
import br.com.globoplast.oee.model.RefugoRecord;
import br.com.globoplast.oee.model.User;
import br.com.globoplast.oee.service.CatalogService;
import br.com.globoplast.oee.service.RefugoService;
import br.com.globoplast.oee.service.I18n;
import br.com.globoplast.oee.util.Norm;
import br.com.globoplast.oee.util.DisplayFormat;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Interações e filtros compartilhados entre a análise e o relatório de refugo. */
final class ScrapScreen {
    private final RefugoService scraps;
    private final CatalogService catalog;
    private final Supplier<User> currentUser;
    private final Supplier<String> language;
    private final Div content;
    private final Supplier<LocalDate[]> bounds;
    private final BiFunction<LocalDate, LocalDate, List<RefugoRecord>> data;
    private final Runnable invalidate;
    private final Consumer<String> notification;
    private record ScrapFilterFields(
            DateRangePicker period,
            MultiSelectComboBox<String> sector,
            MultiSelectComboBox<String> order,
            MultiSelectComboBox<String> machine,
            MultiSelectComboBox<String> shift,
            MultiSelectComboBox<String> product,
            MultiSelectComboBox<String> description,
            MultiSelectComboBox<String> client,
            MultiSelectComboBox<String> operator,
            MultiSelectComboBox<String> motive) { }

    private LocalDate scrapStart = Norm.productiveToday();
    private LocalDate scrapEnd = Norm.productiveToday();
    private final Set<String> scrapSectors = new LinkedHashSet<>();
    private final Set<String> scrapOrders = new LinkedHashSet<>();
    private final Set<String> scrapMachines = new LinkedHashSet<>();
    private final Set<String> scrapProducts = new LinkedHashSet<>();
    private final Set<String> scrapDescriptions = new LinkedHashSet<>();
    private final Set<String> scrapClients = new LinkedHashSet<>();
    private final Set<String> scrapShifts = new LinkedHashSet<>();
    private final Set<String> scrapOperators = new LinkedHashSet<>();
    private final Set<String> scrapMotives = new LinkedHashSet<>();
    private final Set<String> scrapExcludedIds = new LinkedHashSet<>();
    private final Map<String, Integer> scrapPages = new LinkedHashMap<>();
    private String scrapSelectedDimension = "";
    private String scrapSelectedKey = "";
    private boolean scrapShowLaunches = false;
    private String scrapActiveDimension = "Setor";
    private String scrapSearch = "";
    private ScrapReportPage scrapReportPage;
    private ScrapAnalysisPage scrapAnalysisPage;

    ScrapScreen(RefugoService scraps, CatalogService catalog, Supplier<User> currentUser,
                Supplier<String> language, Div content, Supplier<LocalDate[]> bounds,
                BiFunction<LocalDate, LocalDate, List<RefugoRecord>> data,
                Runnable invalidate, Consumer<String> notification) {
        this.scraps = scraps;
        this.catalog = catalog;
        this.currentUser = currentUser;
        this.language = language;
        this.content = content;
        this.bounds = bounds;
        this.data = data;
        this.invalidate = invalidate;
        this.notification = notification;
    }
    private String t(String value) { return I18n.tr(language.get(), value); }
    private Locale locale() { return Locale.forLanguageTag(language.get()); }
    private String formatInt(long value) { return DisplayFormat.integer(value, locale()); }
    private String format(double value) { return DisplayFormat.decimal(value, 2, locale()); }
    private String format1(double value) { return DisplayFormat.decimal(value, 1, locale()); }
    private LocalDate[] cachedScrapBounds() { return bounds.get(); }
    private List<RefugoRecord> cachedScrapData(LocalDate start, LocalDate end) { return data.apply(start, end); }
    private void invalidateDataCaches() { invalidate.run(); }
    private void notify(String message) { notification.accept(message); }
    private Button searchFilterButton() { return FilterControls.searchButton(); }
    private void updateFilterButton(Button button, boolean active) { FilterControls.updateButton(button, active); }

    void renderScrap() {
        content.removeAll();
        ScrapAnalysisPage page = new ScrapAnalysisPage(this::t, scraps, this::formatInt, this::format,
                this::format1, this::locale, scrapSearch, scrapActiveDimension,
                searchFilterButton(), this::scrapHasMonthlyComparison, this::scrapHasYearlyComparison,
                value -> { scrapSearch = value; resetScrapInteraction(); },
                () -> scrapShowLaunches = false,
                dimension -> { scrapActiveDimension = dimension; refreshScrap(dimension); });
        Popover filterDropdown = scrapFilterDropdown(page.filterButton(), page::refreshSelected, this::renderScrap);
        page.setFilterDropdown(filterDropdown);
        scrapAnalysisPage = page;
        content.add(page);
        page.refreshSelected();
    }

    void renderScrapReport() {
        content.removeAll();
        scrapReportPage = new ScrapReportPage(this::t, this::locale, scraps, scrapSearch, searchFilterButton(),
                filter -> scrapFilterDropdown(filter, this::refreshScrapReport, this::renderScrapReport),
                value -> {
                    scrapSearch = value;
                    refreshScrapReport();
                });
        content.add(scrapReportPage);
        refreshScrapReport();
    }

    private void refreshScrapReport() {
        if (scrapReportPage == null) return;
        List<RefugoRecord> rows = currentScrapReportRows();
        LocalDate comparisonStart = YearMonth.from(scrapEnd).minusMonths(6).atDay(1);
        List<RefugoRecord> comparisonRows = scraps.filter(
                cachedScrapData(comparisonStart, scrapEnd),
                scrapSearch, scrapSectors, scrapOrders, scrapMachines, scrapProducts,
                scrapDescriptions, scrapClients, scrapShifts, scrapOperators, scrapMotives);
        scrapReportPage.refresh(scrapStart, scrapEnd, rows, comparisonRows);
    }

    private List<RefugoRecord> currentScrapReportRows() {
        List<RefugoRecord> base = cachedScrapData(scrapStart, scrapEnd);
        return scraps.filter(base, scrapSearch, scrapSectors, scrapOrders, scrapMachines, scrapProducts,
                scrapDescriptions, scrapClients, scrapShifts, scrapOperators, scrapMotives);
    }

    private boolean scrapHasMonthlyComparison() {
        return currentScrapRows().stream()
                .map(r -> YearMonth.from(r.productiveDate()))
                .distinct()
                .limit(2)
                .count() >= 2;
    }

    private boolean scrapHasYearlyComparison() {
        return currentScrapRows().stream()
                .map(r -> r.productiveDate().getYear())
                .distinct()
                .limit(2)
                .count() >= 2;
    }

    private List<RefugoRecord> currentScrapRows() {
        List<RefugoRecord> base = cachedScrapData(scrapStart, scrapEnd);
        List<RefugoRecord> filtered = scraps.filter(base, scrapSearch, scrapSectors, scrapOrders, scrapMachines, scrapProducts, scrapDescriptions, scrapClients, scrapShifts, scrapOperators, scrapMotives);
        if (!scrapExcludedIds.isEmpty()) filtered = filtered.stream().filter(r -> !scrapExcludedIds.contains(r.analysisId())).toList();
        return filtered;
    }

    private void resetScrapInteraction() {
        scrapPages.clear();
        scrapSelectedDimension = "";
        scrapSelectedKey = "";
        scrapShowLaunches = false;
    }

    private void refreshScrap(String dimension) {
        List<RefugoRecord> rows = currentScrapRows();
        Div chart = scrapAnalysisPage == null ? null : scrapAnalysisPage.chart();
        if (chart != null) {
            chart.removeAll();
            if (rows.isEmpty()) {
                chart.add(emptyState(t("Nenhum dado encontrado para os filtros selecionados.")));
            } else if ("Comparativo Mensal".equals(dimension)) {
                renderScrapComparison(chart, rows, true);
            } else if ("Comparativo Anual".equals(dimension)) {
                renderScrapComparison(chart, rows, false);
            } else {
                String selected = Objects.equals(scrapSelectedDimension, dimension) ? scrapSelectedKey : null;
                int page = scrapAnalysisPage.renderDimension(rows, dimension,
                        scrapPages.getOrDefault(dimension, 1), selected, scrapSectors,
                        key -> toggleScrapSelectionAndRefreshKpis(dimension, key),
                        key -> selectScrapForContextMenu(dimension, key),
                        bar -> attachScrapContextMenu(bar, rows, dimension),
                        nextPage -> {
                            scrapPages.put(dimension, nextPage);
                            scrapSelectedDimension = "";
                            scrapSelectedKey = "";
                            scrapShowLaunches = false;
                            refreshScrap(dimension);
                        });
                scrapPages.put(dimension, page);
            }
        }

        refreshScrapSelectionPanels(rows, dimension);

        Div recent = scrapAnalysisPage == null ? null : scrapAnalysisPage.recent();
        if (recent != null) {
            recent.removeAll();
            LocalDate productiveToday = Norm.productiveToday();
            if (Objects.equals(scrapStart, productiveToday) && Objects.equals(scrapEnd, productiveToday)) {
                scrapAnalysisPage.renderRecent(rows);
            }
        }
    }

    private void toggleScrapSelection(String dimension, String key) {
        if (Objects.equals(scrapSelectedDimension, dimension) && Objects.equals(scrapSelectedKey, key)) {
            scrapSelectedDimension = "";
            scrapSelectedKey = "";
            scrapShowLaunches = false;
        } else {
            scrapSelectedDimension = dimension;
            scrapSelectedKey = key == null ? "" : key;
            scrapShowLaunches = false;
        }
    }

    private void selectScrapForContextMenu(String dimension, String key) {
        scrapSelectedDimension = dimension;
        scrapSelectedKey = key == null ? "" : key;
        scrapShowLaunches = false;
        refreshScrapSelectionPanels(currentScrapRows(), dimension);
    }

    private void toggleScrapSelectionAndRefreshKpis(String dimension, String key) {
        toggleScrapSelection(dimension, key);
        refreshScrapSelectionPanels(currentScrapRows(), dimension);
    }

    private void refreshScrapSelectionPanels(List<RefugoRecord> rows, String dimension) {
        if (scrapAnalysisPage != null) {
            scrapAnalysisPage.renderKpis(rows, dimension, scrapSelectedDimension,
                    scrapSelectedKey, scrapStart, scrapEnd);
        }

        Div details = scrapAnalysisPage == null ? null : scrapAnalysisPage.details();
        if (details == null) return;
        details.removeAll();

        boolean selected = Objects.equals(scrapSelectedDimension, dimension)
                && !scrapSelectedKey.isBlank();
        if (selected && "Descrição".equals(dimension)) {
            scrapAnalysisPage.renderDescriptionDetails(rows, scrapSelectedKey, scrapStart, scrapEnd);
        }
        if (selected && scrapShowLaunches) {
            scrapAnalysisPage.renderSelectedLaunches(rows, dimension, scrapSelectedKey);
        }
    }

    private void attachScrapContextMenu(Component target, List<RefugoRecord> rows, String dimension) {
        ContextMenu menu = new ContextMenu();
        menu.setTarget(target);
        menu.setOpenOnClick(false);
        menu.getElement().setProperty("selector", ".gp-refugo-bar-column");

        // O item permanece visível em todos os perfis para que o menu do
        // gráfico não mude de estrutura. A permissão continua sendo aplicada
        // na ação e o item fica desabilitado para quem não é administrador.
        MenuItem transfer = menu.addItem(t("Enviar para outro setor"));
        Set<String> sectorSet = new LinkedHashSet<>();
        catalog.sectors().stream()
                .filter(Objects::nonNull)
                .map(ScrapSector::canonical)
                .filter(v -> !v.isBlank())
                .forEach(sectorSet::add);
        // Mesmo durante uma sincronização incompleta, o menu continua útil:
        // os setores presentes nos próprios lançamentos formam um fallback.
        rows.stream()
                .map(RefugoRecord::sector)
                .filter(Objects::nonNull)
                .map(ScrapSector::canonical)
                .filter(v -> !v.isBlank())
                .forEach(sectorSet::add);
        List<String> sectors = sectorSet.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        for (String sector : sectors) {
            transfer.getSubMenu().addItem(sector, e -> transferSelectedScrapToSector(dimension, sector));
        }
        transfer.setEnabled(!sectors.isEmpty());

        MenuItem exclude = menu.addItem(t("Excluir item"), e -> {
            if (!Objects.equals(scrapSelectedDimension, dimension) || scrapSelectedKey.isBlank()) {
                notify(t("Selecione um item no gráfico"));
                return;
            }
            rows.stream().filter(r -> scraps.matches(r, dimension, scrapSelectedKey)).map(RefugoRecord::analysisId).forEach(scrapExcludedIds::add);
            scrapSelectedDimension = "";
            scrapSelectedKey = "";
            scrapShowLaunches = false;
            refreshScrap(dimension);
        });
        MenuItem view = menu.addItem(t("Ver lançamentos"), e -> {
            if (!Objects.equals(scrapSelectedDimension, dimension) || scrapSelectedKey.isBlank()) {
                notify(t("Selecione um item no gráfico"));
                return;
            }
            scrapShowLaunches = true;
            refreshScrap(dimension);
        });
        if (!scrapExcludedIds.isEmpty()) {
            menu.addItem(t("Restaurar excluídos") + " (" + scrapExcludedIds.size() + ")", e -> {
                scrapExcludedIds.clear();
                resetScrapInteraction();
                refreshScrap(dimension);
            });
        }
    }

    private void transferSelectedScrapToSector(String dimension, String destinationSector) {
        if (currentUser.get() == null || !currentUser.get().isAdmin()) {
            notify(t("Somente um Administrador pode enviar lançamentos para outro setor."));
            return;
        }
        if (!Objects.equals(scrapSelectedDimension, dimension) || scrapSelectedKey.isBlank()) {
            notify(t("Selecione um item no gráfico"));
            return;
        }

        List<RefugoRecord> selectedRows = currentScrapRows().stream()
                .filter(r -> scraps.matches(r, dimension, scrapSelectedKey))
                .toList();
        if (selectedRows.isEmpty()) {
            notify(t("Selecione um item no gráfico"));
            return;
        }

        try {
            int changed = scraps.reassignSector(selectedRows, destinationSector, currentUser.get());
            invalidateDataCaches();
            resetScrapInteraction();
            refreshScrap(dimension);
            notify(formatInt(changed) + " " + t(changed == 1 ? "lançamento enviado." : "lançamentos enviados."));
        } catch (RuntimeException ex) {
            notify(ex.getMessage() == null ? t("Não foi possível alterar o setor.") : ex.getMessage());
        }
    }

    private void renderScrapComparison(Div host, List<RefugoRecord> rows, boolean monthly) {
        String dimension = monthly ? "Comparativo Mensal" : "Comparativo Anual";
        String selected = monthly && Objects.equals(scrapSelectedDimension, dimension) ? scrapSelectedKey : null;
        scrapAnalysisPage.renderComparison(host, rows, monthly, selected,
                key -> toggleScrapSelectionAndRefreshKpis(dimension, key),
                key -> selectScrapForContextMenu(dimension, key),
                chart -> attachScrapContextMenu(chart, rows, dimension));
    }

    private ScrapFilterFields createScrapFilterFields(List<RefugoRecord> base) {
        LocalDate[] bounds = cachedScrapBounds();
        DateRangePicker period = new DateRangePicker(
                t("Período"), scrapStart, scrapEnd,
                bounds[0], bounds[1], language.get(), this::t, null
        );
        MultiSelectComboBox<String> sector = multiSelect(t("Setor"),
                base.stream().map(RefugoRecord::sector).map(ScrapSector::canonical)
                        .filter(v -> !v.isBlank()).distinct().sorted().toList(), scrapSectors, t("Todos"));
        forceUppercaseSectorFilter(sector);
        MultiSelectComboBox<String> order = multiSelect(t("Ordem"),
                base.stream().map(RefugoRecord::orderNumber).filter(Objects::nonNull)
                        .filter(v -> !v.isBlank()).distinct().sorted().toList(), scrapOrders, t("Todos"));
        MultiSelectComboBox<String> machine = multiSelect(t("Máquina"),
                base.stream().map(RefugoRecord::machine).filter(Objects::nonNull)
                        .filter(v -> !v.isBlank()).distinct().sorted().toList(), scrapMachines, t("Todos"));
        MultiSelectComboBox<String> shift = multiSelect(t("Turno"), List.of("A", "B", "C"), scrapShifts, t("Todos"));
        MultiSelectComboBox<String> product = multiSelect(t("Produto"),
                base.stream().map(RefugoRecord::product).filter(Objects::nonNull)
                        .filter(v -> !v.isBlank()).distinct().sorted().toList(), scrapProducts, t("Todos"));
        MultiSelectComboBox<String> description = multiSelect(t("Descrição"),
                base.stream().map(RefugoRecord::description).filter(Objects::nonNull)
                        .filter(v -> !v.isBlank()).distinct().sorted().toList(), scrapDescriptions, t("Todos"));
        MultiSelectComboBox<String> client = multiSelect(t("Cliente"),
                base.stream().map(RefugoRecord::client).filter(Objects::nonNull)
                        .filter(v -> !v.isBlank()).distinct().sorted().toList(), scrapClients, t("Todos"));
        MultiSelectComboBox<String> operator = multiSelect(t("Operador"),
                base.stream().map(RefugoRecord::operator).filter(Objects::nonNull)
                        .filter(v -> !v.isBlank()).distinct().sorted().toList(), scrapOperators, t("Todos"));
        List<String> motiveUids = base.stream()
                .map(r -> Norm.scrapMotiveUid(r.product(), r.motive()))
                .filter(Objects::nonNull).filter(v -> !v.isBlank()).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        MultiSelectComboBox<String> motive = multiSelect(t("Motivo"), motiveUids, scrapMotives, t("Todos"));
        motive.setItemLabelGenerator(uid -> {
            String[] parts = uid == null ? new String[0] : uid.split("¦", -1);
            if (parts.length >= 3) return t(parts[2]) + " · " + t(parts[1]) + " · " + parts[0];
            return t(uid == null ? "" : uid);
        });
        return new ScrapFilterFields(period, sector, order, machine, shift, product,
                description, client, operator, motive);
    }

    private Popover scrapFilterDropdown(Button target, Runnable refresh, Runnable clearView) {
        Popover p = new Popover();
        p.setTarget(target);
        p.setPosition(PopoverPosition.BOTTOM_END);
        p.setWidth("min(320px, calc(100vw - 24px))");
        p.setModal(false);
        p.setBackdropVisible(false);
        p.setCloseOnOutsideClick(true);
        p.setCloseOnEsc(true);
        p.setAriaLabel(t("Filtros"));
        p.addClassNames("gp-filter-popover", "gp-filter-popover-refugo");

        @SuppressWarnings("unchecked")
        List<RefugoRecord>[] optionBase = new List[]{cachedScrapData(scrapStart, scrapEnd)};
        List<RefugoRecord> base = optionBase[0];
        ScrapFilterFields filterFields = createScrapFilterFields(base);
        DateRangePicker period = filterFields.period();
        MultiSelectComboBox<String> sector = filterFields.sector();
        MultiSelectComboBox<String> order = filterFields.order();
        MultiSelectComboBox<String> machine = filterFields.machine();
        MultiSelectComboBox<String> shift = filterFields.shift();
        MultiSelectComboBox<String> product = filterFields.product();
        MultiSelectComboBox<String> description = filterFields.description();
        MultiSelectComboBox<String> client = filterFields.client();
        MultiSelectComboBox<String> operator = filterFields.operator();
        MultiSelectComboBox<String> motive = filterFields.motive();

        Runnable apply = bindScrapFilterInteractions(filterFields, optionBase, target, refresh);

        Button clear = new Button(t("Limpar filtros"), e -> clearScrapFilters(p, target, clearView));
        clear.setWidthFull();
        clear.addClassName("gp-filter-clear");

        Div fields = new Div(period, sector, machine, order, product, description, client, shift, operator, motive);
        fields.addClassName("gp-filter-dropdown-grid");
        Div actions = new Div(clear);
        actions.addClassName("gp-filter-dropdown-actions");
        Div body = new Div(fields, actions);
        body.addClassNames("gp-filter-dropdown", "gp-filter-dropdown-refugo");
        p.add(body);
        updateFilterButton(target, scrapFiltersActive());
        return p;
    }

    private void clearScrapFilters(Popover popover, Button target, Runnable clearView) {
        try {
            if (currentUser.get() != null && currentUser.get().isAdmin()) scraps.clearSectorReassignments(currentUser.get());
        } catch (RuntimeException ex) {
            notify(ex.getMessage() == null
                    ? t("Não foi possível restaurar os setores originais do Refugo.") : ex.getMessage());
            return;
        }
        LocalDate today = Norm.productiveToday();
        scrapStart = today;
        scrapEnd = today;
        scrapSectors.clear();
        scrapOrders.clear();
        scrapMachines.clear();
        scrapProducts.clear();
        scrapDescriptions.clear();
        scrapClients.clear();
        scrapShifts.clear();
        scrapOperators.clear();
        scrapMotives.clear();
        scrapSearch = "";
        scrapExcludedIds.clear();
        resetScrapInteraction();
        invalidateDataCaches();
        updateFilterButton(target, scrapFiltersActive());
        popover.setOpened(false);
        clearView.run();
    }

    private Runnable bindScrapFilterInteractions(
            ScrapFilterFields fields,
            List<RefugoRecord>[] optionBase,
            Button target,
            Runnable refresh) {
        DateRangePicker period = fields.period();
        MultiSelectComboBox<String> sector = fields.sector();
        MultiSelectComboBox<String> machine = fields.machine();
        MultiSelectComboBox<String> order = fields.order();
        MultiSelectComboBox<String> product = fields.product();
        MultiSelectComboBox<String> description = fields.description();
        MultiSelectComboBox<String> client = fields.client();
        MultiSelectComboBox<String> shift = fields.shift();
        MultiSelectComboBox<String> operator = fields.operator();
        MultiSelectComboBox<String> motive = fields.motive();
        boolean[] adjustingOptions = {false};
        Runnable[] updateOptionsRef = new Runnable[1];
        updateOptionsRef[0] = () -> {
            LocalDate start = period.getStart();
            LocalDate end = period.getEnd();
            if (start != null && end != null) optionBase[0] = cachedScrapData(start, end);
            List<RefugoRecord> available = optionBase[0];
            updateMultiSelectOptions(sector, scrapFacetValues(available, r -> ScrapSector.canonical(r.sector())));
            Set<String> selectedSectors = new LinkedHashSet<>(sector.getValue());
            List<RefugoRecord> sectorRows = selectedSectors.isEmpty() ? available : available.stream()
                    .filter(r -> selectedSectors.stream().anyMatch(s -> s.equalsIgnoreCase(r.sector()))).toList();
            updateMultiSelectOptions(machine, scrapFacetValues(sectorRows, RefugoRecord::machine));
            Set<String> selectedMachines = new LinkedHashSet<>(machine.getValue());
            List<RefugoRecord> contextual = selectedMachines.isEmpty() ? sectorRows : sectorRows.stream()
                    .filter(r -> selectedMachines.contains(r.machine())).toList();
            updateMultiSelectOptions(order, scrapFacetValues(contextual, RefugoRecord::orderNumber));
            updateMultiSelectOptions(product, scrapFacetValues(contextual, RefugoRecord::product));
            updateMultiSelectOptions(description, scrapFacetValues(contextual, RefugoRecord::description));
            updateMultiSelectOptions(client, scrapFacetValues(contextual, RefugoRecord::client));
            updateMultiSelectOptions(operator, scrapFacetValues(contextual, RefugoRecord::operator));
            updateMultiSelectOptions(shift, List.of("A", "B", "C").stream()
                    .filter(value -> contextual.stream().anyMatch(r -> value.equalsIgnoreCase(r.shift()))).toList());
            updateMultiSelectOptions(motive, contextual.stream()
                    .map(r -> Norm.scrapMotiveUid(r.product(), r.motive()))
                    .filter(Objects::nonNull).filter(v -> !v.isBlank()).distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList());
        };
        adjustingOptions[0] = true;
        try { updateOptionsRef[0].run(); } finally { adjustingOptions[0] = false; }
        Runnable apply = () -> {
            scrapStart = period.getStart();
            scrapEnd = period.getEnd();
            FilterSelections.replace(scrapSectors, sector.getValue());
            FilterSelections.replace(scrapOrders, order.getValue());
            FilterSelections.replace(scrapMachines, machine.getValue());
            FilterSelections.replace(scrapProducts, product.getValue());
            FilterSelections.replace(scrapDescriptions, description.getValue());
            FilterSelections.replace(scrapClients, client.getValue());
            FilterSelections.replace(scrapShifts, shift.getValue());
            FilterSelections.replace(scrapOperators, operator.getValue());
            FilterSelections.replace(scrapMotives, motive.getValue());
            resetScrapInteraction();
            updateFilterButton(target, scrapFiltersActive());
            refresh.run();
        };
        Runnable cascadeAndApply = () -> {
            if (adjustingOptions[0]) return;
            adjustingOptions[0] = true;
            try { updateOptionsRef[0].run(); } finally { adjustingOptions[0] = false; }
            apply.run();
        };
        period.setChangeListener(cascadeAndApply);
        sector.addValueChangeListener(e -> cascadeAndApply.run());
        machine.addValueChangeListener(e -> cascadeAndApply.run());
        order.addValueChangeListener(e -> { if (!adjustingOptions[0]) apply.run(); });
        product.addValueChangeListener(e -> { if (!adjustingOptions[0]) apply.run(); });
        description.addValueChangeListener(e -> { if (!adjustingOptions[0]) apply.run(); });
        client.addValueChangeListener(e -> { if (!adjustingOptions[0]) apply.run(); });
        shift.addValueChangeListener(e -> { if (!adjustingOptions[0]) apply.run(); });
        operator.addValueChangeListener(e -> { if (!adjustingOptions[0]) apply.run(); });
        motive.addValueChangeListener(e -> { if (!adjustingOptions[0]) apply.run(); });
        return apply;
    }

    private List<String> scrapFacetValues(List<RefugoRecord> rows,
                                          java.util.function.Function<RefugoRecord, String> getter) {
        return ScrapFilterOptions.values(rows, getter);
    }

    private void updateMultiSelectOptions(MultiSelectComboBox<String> field, List<String> options) {
        FilterControls.updateOptions(field, options);
    }

    private boolean scrapFiltersActive() {
        LocalDate[] bounds = cachedScrapBounds();
        return FilterActivity.dateChanged(scrapStart, scrapEnd, bounds)
                || FilterActivity.any(scrapSectors, scrapOrders, scrapMachines, scrapProducts,
                scrapDescriptions, scrapClients, scrapShifts, scrapOperators, scrapMotives);
    }

    private Div emptyState(String message) {
        Div d = new Div(new Span(message));
        d.addClassName("gp-empty-state");
        return d;
    }

}
