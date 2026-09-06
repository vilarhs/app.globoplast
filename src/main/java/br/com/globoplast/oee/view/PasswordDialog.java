package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.User;
import br.com.globoplast.oee.service.AuthService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.PasswordField;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

final class PasswordDialog {
    private final AuthService auth;
    private final User user;
    private final Function<String, String> translate;
    private final Function<String, Dialog> dialogs;
    private final Consumer<String> notification;

    PasswordDialog(AuthService auth, User user, Function<String, String> translate,
                   Function<String, Dialog> dialogs, Consumer<String> notification) {
        this.auth = auth;
        this.user = user;
        this.translate = translate;
        this.dialogs = dialogs;
        this.notification = notification;
    }

    void open() {
        Dialog dialog = dialogs.apply(t("Alterar Senha"));
        PasswordField password = new PasswordField(t("Nova Senha"));
        PasswordField confirm = new PasswordField(t("Confirmar Nova Senha"));
        Button save = new Button(t("Alterar Senha"), event -> {
            if (password.getValue().isBlank()) {
                notification.accept(t("Informe a nova senha."));
                return;
            }
            if (!Objects.equals(password.getValue(), confirm.getValue())) {
                notification.accept(t("A confirmação da nova senha não confere."));
                return;
            }
            auth.changePassword(user.id(), password.getValue());
            dialog.close();
            notification.accept(t("Senha alterada com sucesso."));
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        Div form = new Div(password, confirm);
        form.addClassName("gp-password-form");
        dialog.add(form);
        dialog.getFooter().add(new Button(t("Cancelar"), event -> dialog.close()), save);
        dialog.open();
    }

    private String t(String text) {
        return translate.apply(text);
    }
}
