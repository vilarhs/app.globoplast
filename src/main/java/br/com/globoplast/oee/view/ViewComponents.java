package br.com.globoplast.oee.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.shared.Tooltip;

final class ViewComponents {
    private ViewComponents() {}

    static Button actionIcon(VaadinIcon icon, String aria) {
        Button b = new Button(icon.create());
        b.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        b.getElement().setAttribute("aria-label", aria);
        b.setTooltipText(aria).withPosition(Tooltip.TooltipPosition.TOP);
        b.addClassName("gp-action-icon");
        if (icon == VaadinIcon.TRASH) {
            b.addThemeVariants(ButtonVariant.ERROR);
            b.addClassName("gp-action-icon-danger");
        }
        return b;
    }

    static HorizontalLayout actionIcons(Button... buttons) {
        HorizontalLayout actions = new HorizontalLayout(buttons);
        actions.setPadding(false);
        actions.setSpacing(false);
        actions.setWidthFull();
        actions.setAlignItems(FlexComponent.Alignment.CENTER);
        actions.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        actions.addClassName("gp-action-buttons");
        return actions;
    }

    static Dialog dialog(String title, String closeLabel) {
        Dialog d = new Dialog();
        d.addThemeName("gp-modal-v053");
        d.addClassName("gp-modal-v053");
        d.setHeaderTitle(title);
        d.setModal(true);
        d.setCloseOnEsc(true);
        d.setCloseOnOutsideClick(false);
        d.addOpenedChangeListener(event -> d.getElement().executeJs("""
            requestAnimationFrame(()=>{
              const open=Boolean(document.querySelector('vaadin-dialog-overlay[opened]'));
              document.documentElement.classList.toggle('gp-modal-open-v061',open);
              document.body.classList.toggle('gp-modal-open-v061',open);
            });
        """));

        Button close = new Button(VaadinIcon.CLOSE.create(), e -> d.close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        close.addClassName("gp-dialog-close");
        close.setTooltipText(closeLabel).withPosition(Tooltip.TooltipPosition.TOP);
        close.getElement().setAttribute("aria-label", closeLabel);
        d.getHeader().add(close);
        return d;
    }

}

