package br.com.globoplast.oee.view;

import br.com.globoplast.oee.config.AppConfig;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.time.LocalDate;
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
        add.addClassName("gp-new-button");
        Div title = new Div(heading, add);
        title.addClassNames("gp-title-row", "gp-title-row-static");

        grid = new Grid<>(LaunchRecord.class, false);
        grid.addClassNames("gp-launch-grid-v059", "gp-factory-launch-grid");
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(LaunchRecord::getOrderNumber).setHeader(t("Nº OP")).setAutoWidth(true);
        grid.addColumn(LaunchRecord::getProduct).setHeader(t("Código Produto")).setAutoWidth(true);
        grid.addColumn(LaunchRecord::getMachine).setHeader(t("Máquina")).setFlexGrow(1);
        grid.addColumn(row -> formatInteger.apply(row.getShiftA())).setHeader(t("Produção A (pçs)")).setAutoWidth(true);
        grid.addColumn(row -> formatInteger.apply(row.getShiftB())).setHeader(t("Produção B (pçs)")).setAutoWidth(true);
        grid.addColumn(row -> formatInteger.apply(row.getShiftC())).setHeader(t("Produção C (pçs)")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::actions)).setHeader(t("Ações"))
                .setWidth("116px").setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);
        grid.setAllRowsVisible(true);
        content.add(title, grid);
        refresh();
    }

    private void refresh() {
        if (grid != null) grid.setItems(launches.factoryLaunches(user.get()));
    }

    private HorizontalLayout actions(LaunchRecord record) {
        Button edit = ViewComponents.actionIcon(VaadinIcon.EDIT, t("Editar"));
        edit.addClickListener(event -> open(record));
        Button delete = ViewComponents.actionIcon(VaadinIcon.TRASH, t("Excluir"));
        delete.addClickListener(event -> confirmDelete(record));
        return ViewComponents.actionIcons(edit, delete);
    }

    private void open(LaunchRecord original) {
        boolean editing = original != null;
        LaunchRecord record = editing ? original.copy() : new LaunchRecord();
        if (!editing) record.setDate(Norm.productiveToday());
        Dialog dialog = ViewComponents.dialog(t(editing ? "Editar Lançamento Fábrica" : "Novo Lançamento Fábrica"), t("Fechar"));
        dialog.addClassName("gp-factory-launch-dialog");
        dialog.setWidth("min(820px, calc(100vw - 32px))");

        TextField order = field(t("Nº da OP"), record.getOrderNumber());
        order.setAllowedCharPattern("[0-9]");
        TextField product = field(t("Código Produto"), record.getProduct());
        product.setReadOnly(true);
        ComboBox<String> machine = new ComboBox<>(t("Máquina"));
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

        order.setValueChangeMode(ValueChangeMode.LAZY);
        order.setValueChangeTimeout(300);
        order.addValueChangeListener(event -> resolveOrder(order, product, machine, record.getDate(), allowed));

        Div form = new Div(order, product, machine, shiftA, shiftB, shiftC);
        form.addClassName("gp-factory-launch-form");
        dialog.add(form);

        Button save = new Button(t(editing ? "Salvar Alterações" : "Salvar Lançamento"));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.addClickListener(event -> {
            try {
                apply(record, order, product, machine, shiftA, shiftB, shiftC);
                if (editing) launches.updateManual(record, user.get());
                else launches.saveFactoryLaunch(record, user.get());
                dialog.close();
                afterChange.run();
                refresh();
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

    private void apply(LaunchRecord record, TextField order, TextField product, ComboBox<String> machine,
                       TextField shiftA, TextField shiftB, TextField shiftC) {
        String orderNumber = Norm.order(order.getValue());
        if (!orderNumber.matches("\\d+")) throw new IllegalArgumentException(t("Informe uma única OP usando apenas números."));
        String productCode = Norm.product(product.getValue());
        if (productCode.isBlank()) throw new IllegalArgumentException(t("OP não encontrada para o setor do usuário."));
        Machine selected = CatalogMachineResolver.find(catalog, machine.getValue());
        if (selected == null) throw new IllegalArgumentException(t("Selecione uma máquina."));

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
        record.setProblem("LANÇAMENTO FÁBRICA");
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
        field.setValue(value == null ? "" : value);
        field.setWidthFull();
        return field;
    }

    private static String integerValue(int value) { return value == 0 ? "" : String.valueOf(value); }
}
