package br.com.globoplast.oee.view;

import com.vaadin.flow.component.Component;

import java.util.stream.Stream;

final class ComponentTree {
    private ComponentTree() {}

    static Component findById(Component root, String id) {
        if (root == null || id == null) return null;
        return descendants(root)
                .filter(component -> component.getId().orElse("").equals(id))
                .findFirst()
                .orElse(null);
    }

    private static Stream<Component> descendants(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(ComponentTree::descendants));
    }
}
