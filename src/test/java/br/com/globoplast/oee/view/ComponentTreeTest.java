package br.com.globoplast.oee.view;

import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class ComponentTreeTest {
    @Test
    void findsNestedComponentById() {
        Div root = new Div();
        Div nested = new Div();
        nested.setId("target");
        root.add(new Div(nested));

        assertSame(nested, ComponentTree.findById(root, "target"));
        assertNull(ComponentTree.findById(root, "missing"));
    }
}
