package br.com.globoplast.oee.view;

import static br.com.globoplast.oee.view.ViewComponents.actionIcon;
import static br.com.globoplast.oee.view.ViewComponents.actionIcons;

import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.model.User;
import br.com.globoplast.oee.service.LaunchService;
import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.util.function.*;

final class LaunchTrashDialog {
    private final LaunchService launches;
    private final User user;
    private final String type;
    private final Function<String, String> translate;
    private final Function<String, Dialog> dialogs;
    private final Function<LaunchRecord, Component> productCell;
    private final Function<LaunchRecord, Component> orderCell;
    private final Function<String, String> formatDate;
    private final Runnable restored;
    private final Consumer<String> notification;

    LaunchTrashDialog(LaunchService launches, User user, String type,
                      Function<String, String> translate, Function<String, Dialog> dialogs,
                      Function<LaunchRecord, Component> productCell, Function<LaunchRecord, Component> orderCell,
                      Function<String, String> formatDate, Runnable restored, Consumer<String> message) {
        this.launches = launches;
        this.user = user;
        this.type = type;
        this.translate = translate;
        this.dialogs = dialogs;
        this.productCell = productCell;
        this.orderCell = orderCell;
        this.formatDate = formatDate;
        this.restored = restored;
        this.notification = message;
    }

    private String t(String text) { return translate.apply(text); }

    void open() {
        Dialog d = dialogs.apply(t("Lixeira de Lançamentos"));
        d.setWidth("min(1120px, calc(100vw - 32px))");
        Span retention = new Span(t("Os lançamentos excluídos permanecem na lixeira por 30 dias e depois são excluídos definitivamente."));
        retention.addClassName("gp-muted");

        Grid<LaunchService.TrashItem> grid = new Grid<>(LaunchService.TrashItem.class, false);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(item -> Norm.br(item.record().getDate())).setHeader(t("Data")).setWidth("108px").setFlexGrow(0);
        grid.addColumn(item -> item.record().getMachine()).setHeader(t("Máquina")).setWidth("190px").setFlexGrow(1);
        grid.addColumn(new ComponentRenderer<>(item -> productCell.apply(item.record()))).setHeader(t("Código Produto")).setWidth("140px").setFlexGrow(0);
        grid.addColumn(new ComponentRenderer<>(item -> orderCell.apply(item.record()))).setHeader(t("Nº OP")).setWidth("84px").setFlexGrow(0);
        grid.addColumn(item -> formatDate.apply(item.deletedAt())).setHeader(t("Excluído em")).setWidth("155px").setFlexGrow(1);
        grid.addColumn(item -> formatDate.apply(item.expiresAt())).setHeader(t("Exclusão definitiva")).setWidth("155px").setFlexGrow(1);
        grid.addColumn(new ComponentRenderer<>(item -> {
            Button restore = actionIcon(VaadinIcon.ROTATE_LEFT, t("Recuperar"));
            restore.addClickListener(e -> {
                try {
                    launches.restoreTrash(item.id(), user);
                    grid.setItems(launches.trash(user, type));
                    restored.run();
                    notification.accept(t("Lançamento restaurado com sucesso!"));
                } catch (Exception ex) {
                    String message = ex.getMessage();
                    notification.accept(message == null || message.isBlank() ? t("Não foi possível restaurar o lançamento.") : t(message));
                }
            });
            Button remove = actionIcon(VaadinIcon.TRASH, t("Apagar"));
            remove.addClickListener(e -> confirmTrashDeletion(item, grid, type));
            return actionIcons(restore, remove);
        })).setHeader(t("Ações")).setWidth("90px").setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);
        grid.setHeight("420px");
        grid.setItems(launches.trash(user, type));

        Div body = new Div(retention, grid);
        body.setWidthFull();
        d.add(body);
        d.getFooter().add(new Button(t("Fechar"), e -> d.close()));
        d.open();
    }

    private void confirmTrashDeletion(LaunchService.TrashItem item, Grid<LaunchService.TrashItem> grid, String type) {
        Dialog confirmation = dialogs.apply(t("Apagar permanentemente"));
        confirmation.add(new Span(t("Este lançamento será apagado definitivamente e não poderá ser restaurado.")));
        Button remove = new Button(t("Apagar definitivamente"));
        remove.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.PRIMARY);
        remove.addClickListener(e -> {
            try {
                launches.deleteTrash(item.id(), user);
                confirmation.close();
                grid.setItems(launches.trash(user, type));
                notification.accept(t("Lançamento apagado permanentemente."));
            } catch (Exception ex) {
                String message = ex.getMessage();
                notification.accept(message == null || message.isBlank() ? t("Não foi possível apagar o lançamento.") : t(message));
            }
        });
        confirmation.getFooter().add(new Button(t("Cancelar"), e -> confirmation.close()), remove);
        confirmation.open();
    }

}
