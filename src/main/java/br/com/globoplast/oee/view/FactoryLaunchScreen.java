package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.model.User;
import br.com.globoplast.oee.service.CatalogService;
import br.com.globoplast.oee.service.I18n;
import br.com.globoplast.oee.service.LaunchService;
import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/** Entrada simplificada do chão de fábrica; os dados alimentam os lançamentos manuais. */
final class FactoryLaunchScreen {
    private final CatalogService catalog;
    private final LaunchService launches;
    private final Supplier<User> user;
    private final Supplier<String> language;
    private final Div content;
    private final LongFunction<String> formatInteger;
    private final Runnable afterChange;
    private final Consumer<String> notification;
    private LocalDate filterStart = Norm.productiveToday();
    private LocalDate filterEnd = Norm.productiveToday();
    private Grid<LaunchRecord> grid;

    FactoryLaunchScreen(CatalogService catalog, LaunchService launches, Supplier<User> user,
                        Supplier<String> language, Div content, LongFunction<String> formatInteger,
                        Runnable afterChange, Consumer<String> notification) {
        this.catalog = catalog;
        this.launches = launches;
        this.user = user;
        this.language = language;
        this.content = content;
        this.formatInteger = formatInteger;
        this.afterChange = afterChange;
        this.notification = notification;
    }

    private String t(String value) { return I18n.tr(language.get(), value); }

    void render() {
        content.removeAll();
        H2 heading = new H2(t("Lançamentos Fábrica"));
        heading.addClassName("gp-section-title");
        Button add = new Button(t("Novo Lançamento"), VaadinIcon.PLUS.create(), event -> open(null));
        add.addThemeVariants(ButtonVariant.PRIMARY);
        add.addClassNames("gp-new-button", "gp-launch-new-inline-v045");
        Button summary = new Button(t("Resumo Dia"), VaadinIcon.CLIPBOARD_TEXT.create(), event -> openDaySummary());
        summary.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        summary.addClassName("gp-factory-day-summary-button");
        Button filter = LaunchesPage.filterButton(this::t);
        Div title = new Div(heading, filter);
        title.addClassNames("gp-title-row", "gp-title-row-static", "gp-factory-title-row");

        Popover filterDropdown = filterDropdown(filter);
        Div toolbar = new Div(summary, add);
        toolbar.addClassNames("gp-toolbar", "gp-tab-controls", "gp-launch-toolbar-v045", "gp-factory-launch-toolbar");

        grid = new Grid<>(LaunchRecord.class, false);
        grid.addClassNames("gp-launch-grid-v059", "gp-factory-launch-grid");
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(row -> Norm.br(row.getDate())).setHeader(t("Data")).setWidth("108px").setFlexGrow(0);
        grid.addColumn(LaunchRecord::getOrderNumber).setHeader(t("Nº OP")).setAutoWidth(true);
        grid.addColumn(LaunchRecord::getProduct).setHeader(t("Código Produto")).setAutoWidth(true);
        grid.addColumn(LaunchRecord::getMachine).setHeader(t("Máquina")).setFlexGrow(1);
        grid.addColumn(row -> formatInteger.apply(row.getShiftA())).setHeader(t("Produção A (pçs)")).setAutoWidth(true);
        grid.addColumn(row -> formatInteger.apply(row.getShiftB())).setHeader(t("Produção B (pçs)")).setAutoWidth(true);
        grid.addColumn(row -> formatInteger.apply(row.getShiftC())).setHeader(t("Produção C (pçs)")).setAutoWidth(true);
        grid.addColumn(row -> formatInteger.apply(row.getTotalProduced())).setHeader(t("Total Lançamento")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::actions)).setHeader(t("Ações"))
                .setWidth("116px").setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);
        grid.setAllRowsVisible(true);
        content.add(title, toolbar, filterDropdown, grid);
        refresh();
    }

    private void refresh() {
        if (grid != null) grid.setItems(launches.factoryLaunches(user.get(), filterStart, filterEnd).stream()
                .sorted(Comparator.comparing(LaunchRecord::getMachine, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(LaunchRecord::getOrderNumber, String.CASE_INSENSITIVE_ORDER)).toList());
    }

    private void openDaySummary() {
        LocalDate date = filterEnd;
        List<LaunchRecord> records = launches.factoryLaunches(user.get(), date, date);
        List<FactoryDayLine> rows = daySummaryRows(records);
        int totalA = records.stream().mapToInt(LaunchRecord::getShiftA).sum();
        int totalB = records.stream().mapToInt(LaunchRecord::getShiftB).sum();
        int totalC = records.stream().mapToInt(LaunchRecord::getShiftC).sum();

        Dialog dialog = ViewComponents.dialog(t("Resumo Dia"), t("Fechar"));
        dialog.addClassNames("gp-factory-day-summary-dialog", "gp-launch-dialog");
        dialog.setWidth("min(760px, calc(100vw - 32px))");
        H2 dateTitle = new H2(Norm.br(date));
        dateTitle.addClassName("gp-factory-day-summary-date");

        Grid<FactoryDayLine> summaryGrid = new Grid<>(FactoryDayLine.class, false);
        summaryGrid.addClassNames("gp-launch-grid-v059", "gp-factory-day-summary-grid");
        summaryGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        summaryGrid.addColumn(FactoryDayLine::order).setHeader(t("Nº OP")).setAutoWidth(true);
        summaryGrid.addColumn(FactoryDayLine::product).setHeader(t("Código Produto")).setAutoWidth(true);
        summaryGrid.addColumn(FactoryDayLine::shift).setHeader(t("Turno")).setAutoWidth(true);
        summaryGrid.addColumn(row -> formatInteger.apply((long) row.quantity())).setHeader(t("Quantidade"))
                .setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        summaryGrid.setItems(rows);
        summaryGrid.setHeight("min(52vh, 430px)");

        Span grandTotal = total(t("Total Geral"), totalA + totalB + totalC);
        grandTotal.addClassName("gp-factory-day-summary-grand-total");
        Div totals = new Div(total(t("Total Turno A"), totalA), total(t("Total Turno B"), totalB),
                total(t("Total Turno C"), totalC), grandTotal);
        totals.addClassName("gp-factory-day-summary-totals");
        dialog.add(dateTitle, summaryGrid, totals);
        dialog.getFooter().add(new Button(t("Fechar"), event -> dialog.close()));
        dialog.open();
    }

    private Span total(String label, int value) {
        Span total = new Span(label + ": " + formatInteger.apply((long) value));
        total.addClassName("gp-factory-day-summary-total");
        return total;
    }

    static List<FactoryDayLine> daySummaryRows(List<LaunchRecord> records) {
        List<FactoryDayLine> rows = new ArrayList<>();
        for (LaunchRecord record : records) {
            addDaySummaryRow(rows, record, "A", record.getShiftA());
            addDaySummaryRow(rows, record, "B", record.getShiftB());
            addDaySummaryRow(rows, record, "C", record.getShiftC());
        }
        return rows;
    }

    private static void addDaySummaryRow(List<FactoryDayLine> rows, LaunchRecord record, String shift, int quantity) {
        if (quantity > 0) rows.add(new FactoryDayLine(record.getOrderNumber(), record.getProduct(), shift, quantity));
    }

    record FactoryDayLine(String order, String product, String shift, int quantity) {}

    void showRecordDate(LocalDate date) {
        if (date == null) return;
        filterStart = date;
        filterEnd = date;
        render();
    }

    private Popover filterDropdown(Button target) {
        Popover popover = new Popover();
        popover.setTarget(target);
        popover.setPosition(PopoverPosition.BOTTOM_END);
        popover.setWidth("min(300px, calc(100vw - 24px))");
        popover.setModal(false);
        popover.setBackdropVisible(false);
        popover.setCloseOnOutsideClick(true);
        popover.setCloseOnEsc(true);
        popover.setAriaLabel(t("Filtros"));
        popover.addClassName("gp-filter-popover");

        LocalDate[] bounds = launches.factoryDateBounds();
        DateRangePicker period = new DateRangePicker(
                t("Período"), filterStart, filterEnd,
                bounds[0], bounds[1], language.get(), this::t, null
        );
        period.setChangeListener(() -> {
            filterStart = period.getStart();
            filterEnd = period.getEnd();
            updateFilterButton(target);
            refresh();
        });

        Button clear = new Button(t("Limpar filtros"), event -> {
            filterStart = Norm.productiveToday();
            filterEnd = filterStart;
            period.setValue(filterStart, filterEnd);
            updateFilterButton(target);
            popover.setOpened(false);
            refresh();
        });
        clear.setWidthFull();
        clear.addClassName("gp-filter-clear");
        Div fields = new Div(period);
        fields.addClassName("gp-filter-dropdown-grid");
        Div actions = new Div(clear);
        actions.addClassName("gp-filter-dropdown-actions");
        Div body = new Div(fields, actions);
        body.addClassName("gp-filter-dropdown");
        popover.add(body);
        updateFilterButton(target);
        return popover;
    }

    private void updateFilterButton(Button target) {
        LocalDate today = Norm.productiveToday();
        FilterControls.updateButton(target, !today.equals(filterStart) || !today.equals(filterEnd));
    }

    private HorizontalLayout actions(LaunchRecord record) {
        Button edit = ViewComponents.actionIcon(VaadinIcon.EDIT, t("Editar"));
        edit.addClickListener(event -> open(record));
        Button delete = ViewComponents.actionIcon(VaadinIcon.TRASH, t("Excluir"));
        if (launches.factoryDeleteLocked(user.get(), record)) {
            delete.setEnabled(false);
            delete.setTooltipText(t("Exclusão bloqueada após 1 hora."));
        }
        delete.addClickListener(event -> confirmDelete(record));
        return ViewComponents.actionIcons(edit, delete);
    }

    private void open(LaunchRecord original) {
        boolean editing = original != null;
        LaunchRecord record = editing ? original.copy() : new LaunchRecord();
        if (!editing) record.setDate(Norm.productiveToday());
        Dialog dialog = ViewComponents.dialog(t(editing ? "Editar Lançamento Fábrica" : "Novo Lançamento Fábrica"), t("Fechar"));
        dialog.addClassNames("gp-factory-launch-dialog", "gp-launch-dialog");
        dialog.setWidth("min(820px, calc(100vw - 32px))");

        DateRangePicker date = new DateRangePicker(
                t("Data da Produção"), record.getDate(), record.getDate(),
                record.getDate().minusYears(20), record.getDate().plusYears(20), language.get(), this::t, null, true
        );
        date.addClassNames("gp-date-picker", "gp-unified-date-picker-v081", "gp-launch-standard-field-v054");
        TextField order = field(t("Nº da OP"), record.getOrderNumber());
        order.setAllowedCharPattern("[0-9]");
        TextField product = field(t("Código Produto"), record.getProduct());
        ComboBox<String> machine = new ComboBox<>(t("Máquina"));
        machine.addClassNames("gp-launch-standard-field-v054", "gp-launch-machine-field-v055");
        List<Machine> allowed = user.get().isAdmin() ? catalog.machines() : catalog.allowedMachines(user.get());
        machine.setItems(allowed.stream().map(Machine::name).toList());
        machine.setWidthFull();
        if (!record.getMachine().isBlank()) machine.setValue(record.getMachine());
        TextField shiftA = field(t("Produção A (pçs)"), integerValue(record.getShiftA()));
        TextField shiftB = field(t("Produção B (pçs)"), integerValue(record.getShiftB()));
        TextField shiftC = field(t("Produção C (pçs)"), integerValue(record.getShiftC()));
        shiftA.setAllowedCharPattern("[0-9+ .]");
        shiftB.setAllowedCharPattern("[0-9+ .]");
        shiftC.setAllowedCharPattern("[0-9+ .]");
        if (editing) {
            shiftA.setReadOnly(launches.factoryShiftLocked(user.get(), record, "A"));
            shiftB.setReadOnly(launches.factoryShiftLocked(user.get(), record, "B"));
            shiftC.setReadOnly(launches.factoryShiftLocked(user.get(), record, "C"));
        }

        order.setValueChangeMode(ValueChangeMode.LAZY);
        order.setValueChangeTimeout(300);
        Runnable resolve = () -> resolveOrder(order, product, machine, date.getValue(), allowed);
        order.addValueChangeListener(event -> resolve.run());
        date.setChangeListener(resolve);

        Div dateRow = new Div(date);
        dateRow.addClassNames("gp-launch-row", "gp-launch-row-date");
        Div basicRow = new Div(order, product, machine);
        basicRow.addClassNames("gp-launch-row", "gp-launch-row-3");
        Div shiftsRow = new Div(shiftA, shiftB, shiftC);
        shiftsRow.addClassNames("gp-launch-row", "gp-launch-row-3");
        Div form = new Div(dateRow, basicRow, shiftsRow);
        form.addClassName("gp-launch-form-python");
        dialog.add(form);

        Button save = new Button(t(editing ? "Salvar Alterações" : "Salvar Lançamento"));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.addClickListener(event -> {
            try {
                apply(record, date, order, product, machine, shiftA, shiftB, shiftC);
                if (editing) launches.updateFactoryLaunch(record, user.get());
                else launches.saveFactoryLaunch(record, user.get());
                dialog.close();
                afterChange.run();
                showRecordDate(record.getDate());
                notification.accept(t(editing ? "Lançamento atualizado no Banco de Dados!" : "Lançamento salvo no Banco de Dados!"));
            } catch (Exception ex) {
                notification.accept(ex.getMessage() == null ? t("Não foi possível salvar o lançamento.") : t(ex.getMessage()));
            }
        });
        Button cancel = new Button(t("Cancelar"), event -> dialog.close());
        HorizontalLayout footer = new HorizontalLayout(save, cancel);
        footer.addClassName("gp-launch-dialog-actions");
        footer.setWidthFull();
        footer.setFlexGrow(1, save, cancel);
        dialog.getFooter().add(footer);
        dialog.open();
    }

    private void resolveOrder(TextField order, TextField product, ComboBox<String> machine,
                              LocalDate date, List<Machine> allowed) {
        String value = Norm.order(order.getValue());
        if (value.isBlank()) {
            product.clear();
            machine.clear();
            return;
        }
        User current = user.get();
        String sector = current.isAdmin() ? "" : current.sector();
        LaunchService.OrderLaunchDefaults defaults = launches.orderLaunchDefaults(value, sector, date);
        product.setValue(defaults.product());
        if (allowed.stream().anyMatch(item -> item.name().equals(defaults.machine()))) machine.setValue(defaults.machine());
        else machine.clear();
    }

    private void apply(LaunchRecord record, DateRangePicker date, TextField order, TextField product, ComboBox<String> machine,
                       TextField shiftA, TextField shiftB, TextField shiftC) {
        LocalDate productionDate = date.getValue();
        if (productionDate == null) throw new IllegalArgumentException(t("Informe a data da produção."));
        String orderNumber = Norm.order(order.getValue());
        if (!orderNumber.matches("\\d+")) throw new IllegalArgumentException(t("Informe uma única OP usando apenas números."));
        String productCode = Norm.product(product.getValue());
        if (productCode.isBlank()) throw new IllegalArgumentException(t("OP não encontrada para o setor do usuário."));
        Machine selected = CatalogMachineResolver.find(catalog, machine.getValue());
        if (selected == null) throw new IllegalArgumentException(t("Selecione uma máquina."));

        record.setDate(productionDate);
        record.setOrderNumber(orderNumber);
        record.setProduct(productCode);
        record.setMachine(selected.name());
        record.setSector(selected.sector());
        record.setCapacity24h(selected.capacity());
        record.setScheduledHours(24);
        record.setShiftA(Math.max(0, LaunchValueParser.sumInt(shiftA.getValue())));
        record.setShiftB(Math.max(0, LaunchValueParser.sumInt(shiftB.getValue())));
        record.setShiftC(Math.max(0, LaunchValueParser.sumInt(shiftC.getValue())));
        if (record.getShiftA() + record.getShiftB() + record.getShiftC() == 0)
            throw new IllegalArgumentException(t("Informe a produção de pelo menos um turno."));
        record.setUnitWeightG(launches.productUnitWeightG(productCode));
        LaunchService.ProductMetadata metadata = launches.productMetadata(productCode);
        record.setDescriptionErp(metadata.description());
        record.setClientErp(metadata.client());
        record.setOrigin("FABRICA");
    }

    private void confirmDelete(LaunchRecord record) {
        Dialog dialog = ViewComponents.dialog(t("Confirmar Exclusão de Lançamento"), t("Fechar"));
        dialog.add(t("Deseja excluir este lançamento?"));
        Button confirm = new Button(t("Excluir"), event -> {
            try {
                launches.deleteManual(record.getId(), user.get());
                dialog.close();
                afterChange.run();
                refresh();
                notification.accept(t("Lançamento movido para a lixeira."));
            } catch (Exception ex) {
                notification.accept(ex.getMessage());
            }
        });
        confirm.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.ERROR);
        dialog.getFooter().add(confirm, new Button(t("Cancelar"), event -> dialog.close()));
        dialog.open();
    }

    private static TextField field(String label, String value) {
        TextField field = new TextField(label);
        field.addClassName("gp-launch-standard-field-v054");
        field.setValue(value == null ? "" : value);
        field.setWidthFull();
        return field;
    }

    private static String integerValue(int value) { return value == 0 ? "" : String.valueOf(value); }
}
