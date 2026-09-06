package br.com.globoplast.oee.view;

import br.com.globoplast.oee.config.AppConfig;
import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.model.User;
import br.com.globoplast.oee.service.CatalogService;
import br.com.globoplast.oee.service.LaunchService;
import br.com.globoplast.oee.service.I18n;
import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Formulário e ciclo de vida dos diálogos de lançamento. */
final class LaunchDialog {
    private final CatalogService catalog;
    private final LaunchService launches;
    private final User user;
    private final String language;
    private final LaunchCells launchCells;
    private final Consumer<LaunchRecord> afterSave;
    private final Runnable afterDelete;
    private final Consumer<String> notification;

    LaunchDialog(CatalogService catalog, LaunchService launches, User user, String language,
                 LaunchCells launchCells, Consumer<LaunchRecord> afterSave,
                 Runnable afterDelete, Consumer<String> notification) {
        this.catalog = catalog;
        this.launches = launches;
        this.user = user;
        this.language = language;
        this.launchCells = launchCells;
        this.afterSave = afterSave;
        this.afterDelete = afterDelete;
        this.notification = notification;
    }

    private String t(String value) { return I18n.tr(language, value); }
    private Locale locale() { return "en-US".equals(language) ? Locale.US : Locale.forLanguageTag("pt-BR"); }
    private String displayNumber(double value) { return LaunchDisplayValues.number(value, language); }
    private String displayInteger(int value) { return LaunchDisplayValues.integer(value); }
    private void forceUppercase(TextField field) { InputControls.forceUppercase(field); }
    private void notify(String message) { notification.accept(message); }
    private Dialog dialog(String title) { return ViewComponents.dialog(title, t("Fechar")); }

    void open(LaunchRecord original) {
        boolean edit = original != null;
        LaunchRecord record = edit ? original.copy() : new LaunchRecord();
        if (!edit) {
            record.setDate(LocalDate.now(AppConfig.ZONE).minusDays(1));
        }
        String title = edit ? t("Editar Lançamento") : t("Novo Lançamento");
        Dialog dialog = launchDialog(title);
        Div itemDescription = addLaunchItemDescription(dialog, record);
        LaunchFormFields fields = createLaunchForm(record, false, !edit, edit);
        ProductMetadataLookup.install(fields.product, fields.weight, record, itemDescription,
                launches, this::normalizeProduct, this::displayNumber, this::updateLaunchItemDescription);
        boolean[] resolvingOrder = {false};
        Runnable reloadScrap = !record.isErp() && !edit
                ? configureManualScrapLookup(fields, resolvingOrder)
                : () -> { };
        Runnable resolveOrder = resolveLaunchOrder(fields, resolvingOrder, reloadScrap);
        fields.order.addValueChangeListener(event -> resolveOrder.run());
        dialog.add(fields.root);

        Button save = new Button(edit ? t("Salvar Alterações") : t("Salvar Lançamento"));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.addClickListener(e -> saveLaunchFromDialog(record, fields, edit, dialog));
        Button cancel = new Button(t("Cancelar"), e -> dialog.close());
        HorizontalLayout footer = new HorizontalLayout(save, cancel);
        footer.addClassName("gp-launch-dialog-actions");
        footer.setWidthFull();
        footer.setFlexGrow(1, save, cancel);
        dialog.getFooter().add(footer);
        dialog.open();
        LaunchFormKeyboard.install(fields.root);
    }

    private Runnable resolveLaunchOrder(LaunchFormFields fields, boolean[] resolvingOrder, Runnable reloadScrap) {
        return () -> {
            if (resolvingOrder[0]) return;
            resolvingOrder[0] = true;
            try {
                String order = fields.order.getValue();
                if (order == null || order.isBlank()) {
                    fields.machine.clear();
                    fields.product.clear();
                    fields.weight.clear();
                    return;
                }
                Machine selected = CatalogMachineResolver.find(catalog, fields.machine.getValue());
                String sector = user != null && !user.isAdmin() ? user.sector() : selected == null ? "" : selected.sector();
                LaunchService.OrderLaunchDefaults defaults = launches.orderLaunchDefaults(order, sector, fields.date.getValue());
                Machine linked = CatalogMachineResolver.find(catalog, defaults.machine());
                boolean allowed = linked != null && (user.isAdmin() || catalog.allowedMachines(user).stream()
                        .anyMatch(machine -> machine.name().equals(linked.name())));
                if (allowed && !linked.name().equals(fields.machine.getValue())) fields.machine.setValue(linked.name());
                else if (!allowed) fields.machine.clear();
                if (defaults.product().isBlank()) {
                    fields.product.clear();
                    fields.weight.clear();
                } else if (!defaults.product().equals(normalizeProduct(fields.product.getValue()))) {
                    fields.product.setValue(defaults.product());
                }
            } finally {
                resolvingOrder[0] = false;
                reloadScrap.run();
            }
        };
    }

    private void saveLaunchFromDialog(LaunchRecord record, LaunchFormFields fields, boolean edit, Dialog dialog) {
            try {
                applyLaunchForm(record, fields);
                if (edit) {
                    if (record.isErp()) launches.saveErpOverride(record, user);
                    else launches.updateManual(record, user);
                } else {
                    launches.saveManual(record, user);
                }
                dialog.close();
                afterSave.accept(record);
                notify(t(edit
                        ? "Lançamento atualizado no Banco de Dados!"
                        : "Lançamento salvo no Banco de Dados!"));
            } catch (Exception ex) {
                String message = ex.getMessage();
                notify(message == null || message.isBlank() ? t("Não foi possível salvar o lançamento.") : t(message));
            }
    }

    Dialog view(LaunchRecord record) {
        Dialog dialog = launchDialog(t("Visualizar lançamento"));
        addLaunchItemDescription(dialog, record);
        LaunchFormFields fields = createLaunchForm(record.copy(), true, false, true);
        dialog.add(fields.root);
        dialog.open();
        return dialog;
    }

    private Dialog launchDialog(String title) {
        Dialog d = dialog(title);
        d.addClassName("gp-launch-dialog");
        d.setWidth("min(820px, calc(100vw - 32px))");
        d.setTop("37px");
        return d;
    }

    private Div addLaunchItemDescription(Dialog dialog, LaunchRecord record) {
        Div itemDescription = new Div();
        itemDescription.addClassName("gp-launch-item-description-v094");
        updateLaunchItemDescription(itemDescription, record);
        if (dialog != null) dialog.add(itemDescription);
        return itemDescription;
    }

    private void updateLaunchItemDescription(Div itemDescription, LaunchRecord record) {
        if (itemDescription == null) return;
        String value = launchCells.productMetadataText(record);
        itemDescription.setText(value);
    }

    private Runnable configureManualScrapLookup(LaunchFormFields fields, boolean[] resolvingOrder) {
        Runnable load = () -> {
            if (resolvingOrder[0]) return;
            Machine machine = CatalogMachineResolver.find(catalog, fields.machine.getValue());
            String product = normalizeProduct(fields.product.getValue());
            String sector = ScrapSector.resolve(machine, user, product);
            LaunchService.ScrapByShift scrap = launches.remainingManualScrapByShift(fields.date.getValue(), fields.order.getValue(), sector, fields.machine.getValue(), fields.product.getValue());
            fields.scrapA.setValue(displayNumber(scrap.shiftA()));
            fields.scrapB.setValue(displayNumber(scrap.shiftB()));
            fields.scrapC.setValue(displayNumber(scrap.shiftC()));
        };
        fields.order.setValueChangeMode(ValueChangeMode.LAZY);
        fields.order.setValueChangeTimeout(300);
        fields.product.setValueChangeMode(ValueChangeMode.LAZY);
        fields.product.setValueChangeTimeout(300);
        fields.product.addValueChangeListener(event -> load.run());
        fields.date.setChangeListener(load);
        fields.machine.addValueChangeListener(event -> load.run());
        return load;
    }


    private LaunchFormFields createLaunchForm(LaunchRecord record, boolean readOnly, boolean isNew, boolean showTime) {
        LaunchFormFields f = new LaunchFormFields();
        f.date = datePicker(t("Data da Produção"), record.getDate() == null ? Norm.productiveToday() : record.getDate());
        f.date.setReadOnly(readOnly);
        f.date.setWidthFull();
        f.date.addClassName("gp-launch-standard-field-v054");

        List<Machine> allowed = readOnly
                ? catalog.machines().stream().filter(m -> Objects.equals(m.name(), record.getMachine())).toList()
                : (user.isAdmin() ? catalog.machines() : catalog.allowedMachines(user));
        // ComboBox compartilha a mesma base visual dos TextField. O Select
        // possui um value-button próprio e continuava visualmente diferente.
        f.machine = new ComboBox<>();
        f.machine.setLabel(t("Máquina"));
        f.machine.setAllowCustomValue(false);
        f.machine.setClearButtonVisible(false);
        f.machine.addClassNames("gp-launch-standard-field-v054", "gp-launch-machine-field-v055");
        // Edição/visualização ERP não pode perder o valor original só porque a
        // máquina ainda não foi cadastrada no Java. O valor atual entra como
        // opção preservada, seguido das máquinas permitidas/cadastradas.
        LinkedHashSet<String> machineOptions = new LinkedHashSet<>();
        String originalMachine = cleanInput(record.getMachine());
        if ((record.isErp() || readOnly) && !originalMachine.isBlank()) machineOptions.add(originalMachine);
        allowed.stream().map(Machine::name).filter(Objects::nonNull).filter(v -> !v.isBlank()).forEach(machineOptions::add);
        f.machine.setItems(machineOptions);
        if (!originalMachine.isBlank() && machineOptions.contains(originalMachine)) {
            f.machine.setValue(originalMachine);
        } else if (!isNew && !machineOptions.isEmpty()) {
            f.machine.setValue(machineOptions.iterator().next());
        }
        f.machine.setReadOnly(readOnly);
        f.machine.setWidthFull();

        Machine catalogMachineForRecord = CatalogMachineResolver.find(catalog, originalMachine);
        boolean capacityReadOnly = readOnly || !record.isErp() || catalogMachineForRecord != null;
        f.capacity = textField(t("Capacidade (pçs/24h)"), displayInteger(record.getCapacity24h()), capacityReadOnly);
        f.product = textField(t("Cód. Produto"), cleanInput(record.getProduct()), readOnly);
        f.order = textField(t("Nº da OP"), cleanInput(record.getOrderNumber()), readOnly);
        if (!readOnly) f.order.setAllowedCharPattern("[0-9]");
        f.hours = textField(t("Hrs Program."), "24", true);
        f.weight = textField(t("Peso da Bis. (g)"), displayNumber(record.getUnitWeightG()), readOnly);
        f.shiftA = textField(t("Turno A (pçs)"), displayInteger(record.getShiftA()), readOnly);
        f.scrapA = textField(t("Refugo A (kg)"), displayNumber(record.getScrapAKg()), readOnly);
        f.shiftB = textField(t("Turno B (pçs)"), displayInteger(record.getShiftB()), readOnly);
        f.scrapB = textField(t("Refugo B (kg)"), displayNumber(record.getScrapBKg()), readOnly);
        f.shiftC = textField(t("Turno C (pçs)"), displayInteger(record.getShiftC()), readOnly);
        f.scrapC = textField(t("Refugo C (kg)"), displayNumber(record.getScrapCKg()), readOnly);
        f.changeovers = textField(t("Qtd. Trocas"), displayInteger(record.getChangeovers()), readOnly);
        f.setup = textField(t("Setup (hrs)"), displayNumber(record.getSetupHours()), readOnly);
        f.breakdown = textField(t("Paradas (hrs)"), displayNumber(record.getBreakdownHours()), readOnly);
        f.observations = textField(t("Observações"), cleanInput(record.getProblem()), readOnly);
        if (!readOnly) forceUppercase(f.observations);

        configureLaunchInputModes(f);

        configureMachineCapacity(f, record, originalMachine);

        f.root = LaunchFormLayout.build(f, record, showTime, this::t, LaunchDateFormat::trash);
        configureLaunchTabOrder(f, readOnly);
        return f;
    }

    private void configureLaunchTabOrder(LaunchFormFields fields, boolean readOnly) {
        if (readOnly) return;
        List<Component> order = List.of(
                fields.date, fields.order, fields.product, fields.weight, fields.machine,
                fields.shiftA, fields.shiftB, fields.shiftC,
                fields.scrapA, fields.scrapB, fields.scrapC,
                fields.changeovers, fields.setup, fields.breakdown, fields.observations
        );
        for (int i = 0; i < order.size(); i++) {
            order.get(i).getElement().setAttribute("data-gp-launch-order", String.valueOf(i));
        }
    }

    private void configureMachineCapacity(LaunchFormFields fields, LaunchRecord record, String originalMachine) {
        Machine initialMachine = CatalogMachineResolver.find(catalog, fields.machine.getValue());
        if (initialMachine != null) fields.capacity.setValue(String.valueOf(initialMachine.capacity()));
        else if (record.getCapacity24h() > 0) fields.capacity.setValue(String.valueOf(record.getCapacity24h()));
        fields.machine.addValueChangeListener(e -> {
            Machine machine = CatalogMachineResolver.find(catalog, e.getValue());
            if (machine != null) fields.capacity.setValue(String.valueOf(machine.capacity()));
            else if (record.isErp() && Objects.equals(cleanInput(e.getValue()), originalMachine)
                    && record.getCapacity24h() > 0) fields.capacity.setValue(String.valueOf(record.getCapacity24h()));
            else fields.capacity.setValue("");
        });
    }

    private void configureLaunchInputModes(LaunchFormFields f) {
        configureInputMode(f.product, "text");
        configureInputMode(f.order, "numeric");
        configureInputMode(f.hours, "decimal");
        configureInputMode(f.weight, "decimal");
        configureInputMode(f.setup, "decimal");
        configureInputMode(f.breakdown, "decimal");
        configureInputMode(f.shiftA, "tel");
        configureInputMode(f.shiftB, "tel");
        configureInputMode(f.shiftC, "tel");
        configureInputMode(f.scrapA, "tel");
        configureInputMode(f.scrapB, "tel");
        configureInputMode(f.scrapC, "tel");
        configureInputMode(f.changeovers, "numeric");
        configureInputMode(f.observations, "text");
    }

    private void applyLaunchForm(LaunchRecord record, LaunchFormFields f) {
        LocalDate date = f.date.getValue();
        if (date == null) throw new IllegalArgumentException(t("Informe a Data da Produção."));
        String machineName = f.machine.getValue();
        if (machineName == null || machineName.isBlank()) throw new IllegalArgumentException(t("Selecione uma máquina."));

        record.setDate(date);
        record.setMachine(machineName);
        record.setProduct(normalizeProduct(f.product.getValue()));
        LaunchService.ProductMetadata productMetadata = launches.productMetadata(record.getProduct());
        record.setDescriptionErp(productMetadata.description());
        record.setClientErp(productMetadata.client());
        String orderNumber = f.order.getValue() == null ? "" : f.order.getValue().trim();
        if (!orderNumber.isBlank() && !orderNumber.matches("\\d+"))
            throw new IllegalArgumentException(t("Informe uma única OP usando apenas números."));
        record.setOrderNumber(orderNumber);

        record.setScheduledHours(24.0);

        Machine machine = CatalogMachineResolver.find(catalog, machineName);
        if (machine != null) {
            record.setCapacity24h(machine.capacity());
            record.setSector(machine.sector());
        } else if (!record.isErp()) {
            throw new IllegalArgumentException(t("Máquina inválida."));
        } else {
            // Para ERP sem máquina cadastrada, a capacidade original continua
            // preservada. Se ainda for zero, o Administrador/Conferente pode
            // informar a Capacidade 24h no próprio override; esse valor passa
            // ao snapshot e é reutilizado em todos os apontamentos da máquina.
            int typedCapacity = (int)Math.round(LaunchValueParser.decimal(f.capacity.getValue(), record.getCapacity24h()));
            if (typedCapacity > 0) record.setCapacity24h(typedCapacity);
        }
        // ERP editado preserva Máquina/Capacidade/Setor originais mesmo que o
        // equipamento ainda não exista no cadastro local. Isso evita apagar
        // metadados válidos ao simplesmente abrir e salvar o item.

        record.setUnitWeightG(LaunchValueParser.decimal(f.weight.getValue(), 0));
        record.setProductionDetail("");
        record.setShiftA(LaunchValueParser.sumInt(f.shiftA.getValue()));
        record.setShiftB(LaunchValueParser.sumInt(f.shiftB.getValue()));
        record.setShiftC(LaunchValueParser.sumInt(f.shiftC.getValue()));
        record.setScrapAKg(LaunchValueParser.scrapKg(f.scrapA.getValue()));
        record.setScrapBKg(LaunchValueParser.scrapKg(f.scrapB.getValue()));
        record.setScrapCKg(LaunchValueParser.scrapKg(f.scrapC.getValue()));
        record.setChangeovers((int) LaunchValueParser.decimal(f.changeovers.getValue(), 0));
        record.setSetupHours(Norm.round(LaunchValueParser.hours(f.setup.getValue(), 0), 2));
        record.setBreakdownHours(Norm.round(LaunchValueParser.hours(f.breakdown.getValue(), 0), 2));

        String obs = f.observations.getValue() == null ? "" : f.observations.getValue().trim();
        record.setProblem(obs.isBlank() ? "Nenhum" : obs.toUpperCase(locale()));
    }

    private void configureInputMode(TextField field, String mode) {
        if (field == null) return;
        field.getElement().setAttribute("data-gp-inputmode", mode == null ? "text" : mode);
    }

    private String normalizeProduct(String raw) {
        return LaunchInputNormalizer.product(raw, locale(), t("Não informado"));
    }

    private static String cleanInput(String value) {
        return LaunchInputNormalizer.clean(value);
    }

    private TextField textField(String label, String value, boolean readOnly) {
        TextField field = new TextField(label);
        field.addClassName("gp-launch-standard-field-v054");
        field.setValue(value == null ? "" : value);
        field.setReadOnly(readOnly);
        field.setWidthFull();
        return field;
    }

    void confirmDelete(LaunchRecord record) {
        Dialog d = dialog(t("Confirmar Exclusão de Lançamento"));
        d.add(new Span(t("Deseja mover este lançamento para a lixeira? Ele poderá ser restaurado por 30 dias.")));
        Button delete = new Button(t("Sim, Excluir Registro"));
        delete.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.PRIMARY);
        delete.addClickListener(e -> {
            try {
                if (record.isErp()) launches.hideErp(record, user);
                else launches.deleteManual(record.getId(), user);
                d.close();
                afterDelete.run();
                notify(t("Lançamento movido para a lixeira."));
            } catch (Exception ex) {
                notify(t(ex.getMessage()));
            }
        });
        d.getFooter().add(new Button(t("Cancelar"), e -> d.close()), delete);
        d.open();
    }

    private DateRangePicker datePicker(String label, LocalDate value) {
        LocalDate selected = value == null ? Norm.productiveToday() : value;
        LocalDate min = selected.minusYears(20);
        LocalDate max = selected.plusYears(20);
        DateRangePicker d = new DateRangePicker(
                label, selected, selected, min, max,
                language, this::t, null, true
        );
        d.addClassNames("gp-date-picker", "gp-unified-date-picker-v081");
        return d;
    }

}
