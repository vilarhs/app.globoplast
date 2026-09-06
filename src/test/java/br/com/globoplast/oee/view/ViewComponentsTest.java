package br.com.globoplast.oee.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ViewComponentsTest {
    @Test
    void preservesActionStylesAndDialogCloseLabel() {
        Button edit = ViewComponents.actionIcon(VaadinIcon.EDIT, "Editar");
        Button delete = ViewComponents.actionIcon(VaadinIcon.TRASH, "Apagar");
        assertEquals("Editar", edit.getElement().getAttribute("aria-label"));
        assertTrue(edit.hasClassName("gp-action-icon"));
        assertFalse(edit.hasClassName("gp-action-icon-danger"));
        assertTrue(delete.hasClassName("gp-action-icon-danger"));
        assertTrue(delete.getThemeNames().contains(ButtonVariant.ERROR.getVariantName()));
        var actions = ViewComponents.actionIcons(edit, delete);
        assertEquals(2, actions.getComponentCount());
        assertTrue(actions.hasClassName("gp-action-buttons"));
        assertEquals("100%", actions.getWidth());
        Dialog dialog = ViewComponents.dialog("Teste", "Close");
        assertEquals("Teste", dialog.getHeaderTitle());
        assertTrue(dialog.hasClassName("gp-modal-v053"));
        assertTrue(dialog.isCloseOnEsc());
        assertFalse(dialog.isCloseOnOutsideClick());
        Button close = (Button) dialog.getHeader().getElement().getChildren()
                .flatMap(element -> element.getComponent().stream())
                .filter(Button.class::isInstance).findFirst().orElseThrow();
        assertEquals("Close", close.getElement().getAttribute("aria-label"));
        assertTrue(close.hasClassName("gp-dialog-close"));
    }
}
