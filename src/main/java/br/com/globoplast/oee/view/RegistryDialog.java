package br.com.globoplast.oee.view;

import static br.com.globoplast.oee.view.ViewComponents.actionIcon;
import static br.com.globoplast.oee.view.ViewComponents.actionIcons;

import br.com.globoplast.oee.model.Machine;
import br.com.globoplast.oee.model.Sector;
import br.com.globoplast.oee.service.CatalogService;
import br.com.globoplast.oee.service.LaunchService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;

final class RegistryDialog {
    private final CatalogService catalog;
    private final LaunchService launches;
    private final Function<String, String> translate;
    private final Function<String, Dialog> dialogs;
    private final Consumer<TextField> uppercase;
    private final LongFunction<String> formatInteger;
    private final Runnable invalidate;
    private final Consumer<String> notification;

    RegistryDialog(CatalogService catalog, LaunchService launches, Function<String, String> translate,
                   Function<String, Dialog> dialogs, Consumer<TextField> uppercase,
                   LongFunction<String> formatInteger, Runnable invalidate, Consumer<String> notification) {
        this.catalog = catalog;
        this.launches = launches;
        this.translate = translate;
        this.dialogs = dialogs;
        this.uppercase = uppercase;
        this.formatInteger = formatInteger;
        this.invalidate = invalidate;
        this.notification = notification;
    }

    private String t(String text) { return translate.apply(text); }

    void open() {
        Dialog d = dialogs.apply(t("Cadastro"));
        d.setWidth("min(920px,94vw)");
        d.addClassName("gp-admin-dialog");
        Tab sectorsTab = new Tab(t("Setores"));
        Tab machinesTab = new Tab(t("Máquinas"));
        Tabs tabs = new Tabs(sectorsTab, machinesTab);
        tabs.addClassName("gp-admin-tabs");
        Div body = new Div();
        body.addClassName("gp-admin-body");
        tabs.addSelectedChangeListener(e -> {
            if (e.getSelectedTab() == sectorsTab) renderSectors(body);
            else renderMachines(body);
        });
        d.add(tabs, body);
        d.getFooter().add(new Button(t("Fechar"), e -> d.close()));
        renderSectors(body);
        d.open();
    }

    private void renderSectors(Div body) {
        body.removeAll();
        TextField name = new TextField(t("Nome do Setor"));
        name.setWidthFull();
        uppercase.accept(name);
        Button add = new Button(t("Cadastrar Setor"), VaadinIcon.PLUS.create());
        add.addThemeVariants(ButtonVariant.PRIMARY);
        add.addClickListener(e -> {
            try {
                catalog.saveSector(null, name.getValue());
                invalidate.run();
                renderSectors(body);
            } catch (Exception ex) { notification.accept(t(ex.getMessage())); }
        });
        Div form = new Div(name, add);
        form.addClassNames("gp-admin-toolbar", "gp-admin-toolbar-sector");

        Grid<Sector> grid = new Grid<>(Sector.class, false);
        grid.addClassName("gp-admin-grid");
        grid.addColumn(Sector::name).setHeader(t("Setor")).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(new ComponentRenderer<>(sector -> {
            Button edit = actionIcon(VaadinIcon.EDIT, t("Editar"));
            edit.addClickListener(e -> showSectorEdit(sector, body));
            Button delete = actionIcon(VaadinIcon.TRASH, t("Excluir"));
            delete.addClickListener(e -> confirmDeleteSector(sector, body));
            return actionIcons(edit, delete);
        })).setHeader(t("Ações")).setWidth("90px").setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);
        grid.setItems(catalog.sectorEntries());
        grid.setAllRowsVisible(true);
        body.add(form, grid);
    }

    private void showSectorEdit(Sector sector, Div body) {
        Dialog d = dialogs.apply(t("Editar Setor"));
        d.setWidth("min(430px,92vw)");
        d.addClassName("gp-admin-form-dialog");
        TextField name = new TextField(t("Nome do Setor"));
        name.setValue(sector.name()); name.setWidthFull();
        uppercase.accept(name);
        Div form = new Div(name); form.addClassName("gp-admin-form");
        Button save = new Button(t("Salvar"), e -> {
            try { catalog.saveSector(sector.id(), name.getValue()); invalidate.run(); d.close(); renderSectors(body); }
            catch (Exception ex) { notification.accept(t(ex.getMessage())); }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        d.add(form);
        d.getFooter().add(new Button(t("Cancelar"), e -> d.close()), save);
        d.open();
    }

    private void confirmDeleteSector(Sector sector, Div body) {
        Dialog d = dialogs.apply(t("Excluir Setor"));
        d.setWidth("min(430px,92vw)");
        d.add(new Paragraph(t("Confirma a exclusão de") + " " + sector.name() + "?"));
        Button delete = new Button(t("Excluir"), e -> {
            try { catalog.deleteSector(sector.id()); d.close(); renderSectors(body); }
            catch (Exception ex) { notification.accept(t(ex.getMessage())); }
        });
        delete.addThemeVariants(ButtonVariant.ERROR);
        d.getFooter().add(new Button(t("Cancelar"), e -> d.close()), delete);
        d.open();
    }

    private void renderMachines(Div body) {
        body.removeAll();
        TextField name = new TextField(t("Nome da Máquina"));
        uppercase.accept(name);
        IntegerField cap = new IntegerField(t("Capacidade 24h"));
        ComboBox<String> sector = new ComboBox<>();
        sector.setLabel(t("Atribuir ao Setor"));
        sector.setItems(catalog.sectors());
        sector.setAllowCustomValue(false);
        sector.setClearButtonVisible(false);
        sector.addClassNames("gp-admin-standard-field-v057", "gp-machine-sector-field-v060");
        Button add = new Button(t("Salvar Máquina"), VaadinIcon.PLUS.create());
        add.addThemeVariants(ButtonVariant.PRIMARY);
        add.addClickListener(e -> {
            try {
                catalog.saveMachine(null, name.getValue(), cap.getValue() == null ? 0 : cap.getValue(), sector.getValue());
                launches.refreshAllMachineCapacities();
                invalidate.run();
                renderMachines(body);
            } catch (Exception ex) { notification.accept(t(ex.getMessage())); }
        });
        Div form = new Div(name, cap, sector, add);
        form.addClassNames("gp-admin-toolbar", "gp-admin-toolbar-machine");

        Grid<Machine> grid = new Grid<>(Machine.class, false);
        grid.addClassName("gp-admin-grid");
        grid.addColumn(Machine::name).setHeader(t("Máquina")).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(m -> formatInteger.apply(m.capacity())).setHeader(t("Capacidade 24h")).setAutoWidth(true);
        grid.addColumn(Machine::sector).setHeader(t("Setor")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(machine -> {
            Button edit = actionIcon(VaadinIcon.EDIT, t("Editar"));
            edit.addClickListener(e -> showMachineEdit(machine, body));
            Button delete = actionIcon(VaadinIcon.TRASH, t("Excluir"));
            delete.addClickListener(e -> confirmDeleteMachine(machine, body));
            return actionIcons(edit, delete);
        })).setHeader(t("Ações")).setWidth("90px").setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);
        grid.setItems(catalog.machines());
        grid.setAllRowsVisible(true);
        body.add(form, grid);
    }

    private void showMachineEdit(Machine machine, Div body) {
        Dialog d = dialogs.apply(t("Editar Máquina"));
        d.setWidth("min(520px,92vw)");
        d.addClassNames("gp-admin-form-dialog", "gp-machine-edit-dialog-v060");
        TextField name = new TextField(t("Nome da Máquina")); name.setValue(machine.name());
        uppercase.accept(name);
        IntegerField cap = new IntegerField(t("Capacidade 24h")); cap.setValue(machine.capacity());
        ComboBox<String> sector = new ComboBox<>();
        sector.setLabel(t("Atribuir ao Setor"));
        sector.setItems(catalog.sectors());
        sector.setAllowCustomValue(false);
        sector.setClearButtonVisible(false);
        sector.addClassNames("gp-admin-standard-field-v057", "gp-machine-sector-field-v060");
        if (machine.sector() != null && catalog.sectors().contains(machine.sector())) sector.setValue(machine.sector());
        name.setWidthFull();
        cap.setWidthFull();
        sector.setWidthFull();
        Div form = new Div(name, cap, sector);
        form.addClassNames("gp-admin-form", "gp-machine-edit-form-v060");
        Button save = new Button(t("Salvar"), e -> {
            try { catalog.saveMachine(machine.id(), name.getValue(), cap.getValue() == null ? 0 : cap.getValue(), sector.getValue()); launches.refreshAllMachineCapacities(); invalidate.run(); d.close(); renderMachines(body); }
            catch (Exception ex) { notification.accept(t(ex.getMessage())); }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        d.add(form);
        d.getFooter().add(new Button(t("Cancelar"), e -> d.close()), save);
        d.open();
    }

    private void confirmDeleteMachine(Machine machine, Div body) {
        Dialog d = dialogs.apply(t("Excluir Máquina"));
        d.setWidth("min(430px,92vw)");
        d.add(new Paragraph(t("Confirma a exclusão de") + " " + machine.name() + "?"));
        Button delete = new Button(t("Excluir"), e -> {
            try { catalog.deleteMachine(machine.id()); d.close(); renderMachines(body); }
            catch (Exception ex) { notification.accept(t(ex.getMessage())); }
        });
        delete.addThemeVariants(ButtonVariant.ERROR);
        d.getFooter().add(new Button(t("Cancelar"), e -> d.close()), delete);
        d.open();
    }

}

