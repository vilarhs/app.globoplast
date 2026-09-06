package br.com.globoplast.oee.view;

import static br.com.globoplast.oee.view.ViewComponents.actionIcon;
import static br.com.globoplast.oee.view.ViewComponents.actionIcons;

import br.com.globoplast.oee.model.User;
import br.com.globoplast.oee.service.AuthService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class UsersDialog {
    private final AuthService auth;
    private final User user;
    private final Supplier<List<String>> sectors;
    private final Function<String, String> translate;
    private final Function<String, Dialog> dialogs;
    private final Consumer<TextField> uppercase;
    private final Consumer<String> notification;

    UsersDialog(AuthService auth, User user, Supplier<List<String>> sectors,
                Function<String, String> translate, Function<String, Dialog> dialogs,
                Consumer<TextField> uppercase, Consumer<String> notification) {
        this.auth = auth;
        this.user = user;
        this.sectors = sectors;
        this.translate = translate;
        this.dialogs = dialogs;
        this.uppercase = uppercase;
        this.notification = notification;
    }

    private String t(String text) { return translate.apply(text); }

    void open() {
        Dialog d = dialogs.apply(t("Usuários"));
        d.setWidth("min(880px,94vw)");
        d.addClassName("gp-admin-dialog");
        Grid<User> grid = new Grid<>(User.class, false);
        grid.addClassName("gp-admin-grid");
        grid.addColumn(User::username).setHeader(t("Usuário")).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(u -> profileLabel(u.profile())).setHeader(t("Perfil")).setAutoWidth(true);
        grid.addColumn(u -> u.sector() == null ? "—" : u.sector()).setHeader(t("Setor")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(u -> {
            Button edit = actionIcon(VaadinIcon.EDIT, t("Editar"));
            edit.addClickListener(e -> showUserEdit(u, d));
            Button delete = actionIcon(VaadinIcon.TRASH, t("Excluir"));
            delete.setEnabled(user == null || user.id() != u.id());
            delete.addClickListener(e -> confirmDeleteUser(u, d));
            return actionIcons(edit, delete);
        })).setHeader(t("Ações")).setWidth("90px").setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);
        grid.setItems(auth.users()); grid.setAllRowsVisible(true);
        Button add = new Button(t("Novo Usuário"), VaadinIcon.PLUS.create());
        add.addThemeVariants(ButtonVariant.PRIMARY);
        add.addClickListener(e -> showUserEdit(null, d));
        Div toolbar = new Div(add); toolbar.addClassName("gp-admin-list-toolbar");
        d.add(toolbar, grid);
        d.getFooter().add(new Button(t("Fechar"), e -> d.close()));
        d.open();
    }

    private void showUserEdit(User existing, Dialog parent) {
        Dialog d = dialogs.apply(existing == null ? t("Novo Usuário") : t("Editar Usuário"));
        d.setWidth("min(600px,92vw)");
        d.addClassName("gp-admin-form-dialog");
        TextField username = new TextField(t("Usuário"));
        uppercase.accept(username);
        ComboBox<String> profile = new ComboBox<>();
        profile.setLabel(t("Perfil"));
        profile.setAllowCustomValue(false);
        profile.setClearButtonVisible(false);
        profile.addClassNames("gp-admin-standard-field-v057", "gp-admin-profile-field-v057");
        List<String> profileValues = List.of("Padrão", "Acompanhamento", "Conferente", "Administrador");
        profile.setItems(profileValues); profile.setItemLabelGenerator(this::t);
        ComboBox<String> sector = new ComboBox<>();
        sector.setLabel(t("Setor"));
        sector.setItems(sectors.get());
        sector.setAllowCustomValue(false);
        sector.setClearButtonVisible(false);
        sector.addClassNames("gp-admin-standard-field-v057", "gp-admin-sector-field-v057");
        PasswordField password = new PasswordField(existing == null ? t("Senha") : t("Nova senha (opcional)"));
        PasswordField confirmPassword = new PasswordField(existing == null ? t("Confirmar Senha") : t("Confirmar Nova Senha"));
        username.addClassName("gp-admin-standard-field-v057");
        password.addClassName("gp-admin-standard-field-v057");
        confirmPassword.addClassName("gp-admin-standard-field-v057");
        username.setWidthFull(); profile.setWidthFull(); sector.setWidthFull(); password.setWidthFull(); confirmPassword.setWidthFull();
        if (existing != null) {
            username.setValue(existing.username()); profile.setValue(profileCanonical(existing.profile()));
            if (existing.sector() != null && sectors.get().contains(existing.sector())) sector.setValue(existing.sector());
        } else profile.setValue("Padrão");
        profile.addValueChangeListener(e -> sector.setEnabled("Padrão".equals(e.getValue())));
        sector.setEnabled("Padrão".equals(profile.getValue()));
        Div form = new Div(username, profile, sector, password, confirmPassword); form.addClassNames("gp-admin-form", "gp-admin-user-form");
        Button save = new Button(t("Salvar"), e -> {
            try {
                if(!Objects.equals(password.getValue(), confirmPassword.getValue())) throw new IllegalArgumentException(existing==null ? "A confirmação da senha não confere." : "A confirmação da nova senha não confere.");
                auth.saveUser(existing == null ? null : existing.id(), username.getValue(), profile.getValue(), sector.getValue(), password.getValue());
                d.close(); parent.close(); open();
            } catch (Exception ex) { notification.accept(t(ex.getMessage())); }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        d.add(form);
        d.getFooter().add(new Button(t("Cancelar"), e -> d.close()), save);
        d.open();
    }

    private void confirmDeleteUser(User user, Dialog parent) {
        Dialog d = dialogs.apply(t("Excluir Usuário"));
        d.setWidth("min(430px,92vw)");
        d.add(new Paragraph(t("Confirma a exclusão de") + " " + user.username() + "?"));
        Button delete = new Button(t("Excluir"), e -> {
            try { auth.deleteUser(user.id()); d.close(); parent.close(); open(); }
            catch (Exception ex) { notification.accept(t(ex.getMessage())); }
        });
        delete.addThemeVariants(ButtonVariant.ERROR);
        d.getFooter().add(new Button(t("Cancelar"), e -> d.close()), delete);
        d.open();
    }


    private String profileCanonical(String profile) {
        return switch (profile) {
            case "administrador" -> "Administrador";
            case "acompanhamento" -> "Acompanhamento";
            case "conferente" -> "Conferente";
            default -> "Padrão";
        };
    }

    private String profileLabel(String profile) {
        return t(profileCanonical(profile));
    }

}

