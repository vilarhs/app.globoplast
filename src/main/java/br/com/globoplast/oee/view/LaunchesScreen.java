package br.com.globoplast.oee.view;
import br.com.globoplast.oee.config.AppConfig;
import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.service.CatalogService;
import br.com.globoplast.oee.service.LaunchService;
import br.com.globoplast.oee.service.I18n;
import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.server.VaadinSession;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Lista de lançamentos, filtros persistidos e expansão da tabela. */
final class LaunchesScreen {
    private final CatalogService catalog;
    private final LaunchService launches;
    private final Supplier<String> language;
    private final Supplier<Boolean> allowNew;
    private final Div content;
    private final Supplier<LocalDate[]> bounds;
    private final BiFunction<LocalDate, LocalDate, List<LaunchRecord>> data;
    private final Supplier<Grid<LaunchRecord>> grid;
    private final Runnable newLaunch;
    private final Runnable menuStatus;
    private LocalDate launchStart = Norm.productiveToday();
    private LocalDate launchEnd = Norm.productiveToday();
    private final Set<String> launchSectors = new LinkedHashSet<>();
    private final Set<String> launchMachines = new LinkedHashSet<>();
    private final Set<String> launchClients = new LinkedHashSet<>();
    private String launchSearch = "";
    private int launchLimit = AppConfig.PAGE_SIZE;


    LaunchesScreen(CatalogService catalog, LaunchService launches, Supplier<String> language,
                   Supplier<Boolean> allowNew, Div content, Supplier<LocalDate[]> bounds,
                   BiFunction<LocalDate, LocalDate, List<LaunchRecord>> data,
                   Supplier<Grid<LaunchRecord>> grid, Runnable newLaunch, Runnable menuStatus) {
        this.catalog = catalog;
        this.launches = launches;
        this.language = language;
        this.allowNew = allowNew;
        this.content = content;
        this.bounds = bounds;
        this.data = data;
        this.grid = grid;
        this.newLaunch = newLaunch;
        this.menuStatus = menuStatus;
    }
    private String t(String value) { return I18n.tr(language.get(), value); }
    private LocalDate[] cachedLaunchBounds() { return bounds.get(); }
    private List<LaunchRecord> cachedLaunchData(LocalDate start, LocalDate end) { return data.apply(start, end); }
    private Grid<LaunchRecord> launchGrid() { return grid.get(); }
    private void refreshMenuSyncStatus() { menuStatus.run(); }

    void renderLaunches() {
        content.removeAll();
        Grid<LaunchRecord> grid = launchGrid();
        LaunchesPage page = new LaunchesPage(t("Lançamentos"), launchSearch,
                allowNew.get(),
                this::t, this::launchFilterDropdown,
                value -> { launchSearch = value; launchLimit = AppConfig.PAGE_SIZE; refreshLaunchGrid(); },
                newLaunch,
                () -> { launchLimit += AppConfig.PAGE_SIZE; refreshLaunchGrid(); }, grid);
        content.add(page.components());
        refreshLaunchGrid();
        refreshMenuSyncStatus();
    }

    @SuppressWarnings("unchecked")
    void refreshLaunchGrid() {
        Component component = ComponentTree.findById(content, "launch-grid");
        if (!(component instanceof Grid<?> raw)) return;
        Grid<LaunchRecord> grid = (Grid<LaunchRecord>) raw;
        rememberLaunchFilterState();
        List<LaunchRecord> all = cachedLaunchData(launchStart, launchEnd);
        List<LaunchRecord> filtered = launches.filter(all, launchSearch, launchSectors, launchStart, launchEnd, launchMachines, launchClients);
        grid.setItems(filtered.stream().limit(launchLimit).toList());
        Component more = ComponentTree.findById(content, "launch-more");
        if (more != null) more.setVisible(filtered.size() > launchLimit);
    }

    private void rememberLaunchFilterState() {
        VaadinSession.getCurrent().setAttribute("gp_launch_filter_state", new LaunchFilterState(
                launchStart, launchEnd, launchSearch,
                FilterSelections.copy(launchSectors), FilterSelections.copy(launchMachines), FilterSelections.copy(launchClients), launchLimit));
    }

    void restoreLaunchFilterState() {
        Object saved = VaadinSession.getCurrent().getAttribute("gp_launch_filter_state");
        if (!(saved instanceof LaunchFilterState state)) return;
        if (state.start() != null) launchStart = state.start();
        if (state.end() != null) launchEnd = state.end();
        launchSearch = state.search() == null ? "" : state.search();
        FilterSelections.replace(launchSectors, state.sectors());
        FilterSelections.replace(launchMachines, state.machines());
        FilterSelections.replace(launchClients, state.clients());
        launchLimit = Math.max(AppConfig.PAGE_SIZE, state.limit());
    }

    private Popover launchFilterDropdown(Button target) {
        Popover p = new Popover();
        p.setTarget(target);
        p.setPosition(PopoverPosition.BOTTOM_END);
        p.setWidth("min(300px, calc(100vw - 24px))");
        p.setModal(false);
        p.setBackdropVisible(false);
        p.setCloseOnOutsideClick(true);
        p.setCloseOnEsc(true);
        p.setAriaLabel(t("Filtros"));
        p.addClassName("gp-filter-popover");

        LocalDate[] bounds = cachedLaunchBounds();
        DateRangePicker period = new DateRangePicker(
                t("Período"), launchStart, launchEnd,
                bounds[0], bounds[1], language.get(), this::t, null
        );
        MultiSelectComboBox<String> sector = multiSelect(
                t("Setor"), catalog.sectors(), launchSectors, t("Todos")
        );
        forceUppercaseSectorFilter(sector);
        List<Machine> launchFilterMachines = catalog.machines();
        MultiSelectComboBox<String> machine = multiSelect(
                t("Máquina"), LaunchFilterOptions.machines(launchFilterMachines, launchSectors),
                launchMachines, t("Todos")
        );
        MultiSelectComboBox<String> client = multiSelect(
                t("Cliente"), LaunchFilterOptions.clients(cachedLaunchData(launchStart, launchEnd)),
                launchClients, t("Todos")
        );

        Runnable apply = bindLaunchFilterInteractions(period, sector, machine, client,
                launchFilterMachines, target);

        Button clear = new Button(t("Limpar filtros"), e -> {
            LocalDate today = Norm.productiveToday();
            launchStart = today;
            launchEnd = today;
            launchSectors.clear();
            launchMachines.clear();
            launchClients.clear();
            launchSearch = "";
            launchLimit = AppConfig.PAGE_SIZE;
            updateFilterButton(target, launchFiltersActive());
            p.setOpened(false);
            renderLaunches();
        });
        clear.setWidthFull();
        clear.addClassName("gp-filter-clear");

        Div fields = new Div(period, sector, machine, client);
        fields.addClassName("gp-filter-dropdown-grid");
        Div actions = new Div();
        actions.add(clear);
        actions.addClassName("gp-filter-dropdown-actions");
        Div body = new Div(fields, actions);
        body.addClassName("gp-filter-dropdown");
        p.add(body);
        updateFilterButton(target, launchFiltersActive());
        return p;
    }

    private Runnable bindLaunchFilterInteractions(
            DateRangePicker period,
            MultiSelectComboBox<String> sector,
            MultiSelectComboBox<String> machine,
            MultiSelectComboBox<String> client,
            List<Machine> availableMachines,
            Button target) {
        Runnable apply = () -> {
            launchStart = period.getStart();
            launchEnd = period.getEnd();
            FilterSelections.replace(launchSectors, sector.getValue());
            FilterSelections.replace(launchMachines, machine.getValue());
            FilterSelections.replace(launchClients, client.getValue());
            launchLimit = AppConfig.PAGE_SIZE;
            updateFilterButton(target, launchFiltersActive());
            refreshLaunchGrid();
        };
        period.setChangeListener(() -> {
            launchStart = period.getStart();
            launchEnd = period.getEnd();
            List<String> options = LaunchFilterOptions.clients(cachedLaunchData(launchStart, launchEnd));
            Set<String> valid = new LinkedHashSet<>(client.getValue());
            valid.removeIf(v -> !options.contains(v));
            client.setItems(options);
            if (!Objects.equals(valid, client.getValue())) client.setValue(valid);
            apply.run();
        });
        sector.addValueChangeListener(e -> {
            List<String> options = LaunchFilterOptions.machines(availableMachines, e.getValue());
            Set<String> valid = new LinkedHashSet<>(machine.getValue());
            valid.removeIf(v -> !options.contains(v));
            machine.setItems(options);
            if (!Objects.equals(valid, machine.getValue())) machine.setValue(valid);
            apply.run();
        });
        machine.addValueChangeListener(e -> apply.run());
        client.addValueChangeListener(e -> apply.run());
        return apply;
    }


    private MultiSelectComboBox<String> multiSelect(String label, List<String> items, Set<String> selected, String placeholder) {
        return FilterControls.multiSelect(label, items, selected, placeholder);
    }

    private void forceUppercaseSectorFilter(MultiSelectComboBox<?> box) {
        FilterControls.forceUppercaseSectorFilter(box);
    }

    private boolean launchFiltersActive() {
        LocalDate[] bounds = cachedLaunchBounds();
        return FilterActivity.dateChanged(launchStart, launchEnd, bounds)
                || FilterActivity.any(launchSectors, launchMachines, launchClients);
    }

    private void updateFilterButton(Button button, boolean active) {
        FilterControls.updateButton(button, active);
    }

}
