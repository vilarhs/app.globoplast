package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.service.LaunchService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.util.function.BiConsumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;

final class ProductMetadataLookup {
    private ProductMetadataLookup() {}

    static void install(TextField product, TextField weight, LaunchRecord record, Div description,
                        LaunchService launches, Function<String, String> normalize,
                        DoubleFunction<String> formatWeight,
                        BiConsumer<Div, LaunchRecord> refreshDescription) {
        if (product == null || product.isReadOnly()) return;
        product.setValueChangeMode(ValueChangeMode.LAZY);
        product.setValueChangeTimeout(300);
        product.addValueChangeListener(event -> {
            LaunchService.ProductMetadata metadata = launches.productMetadata(event.getValue());
            record.setProduct(normalize.apply(event.getValue()));
            record.setDescriptionErp(metadata.description());
            record.setClientErp(metadata.client());
            if (!record.isErp()) {
                double unitWeightG = launches.productUnitWeightG(record.getProduct());
                weight.setValue(unitWeightG > 0 ? formatWeight.apply(unitWeightG) : "");
            }
            refreshDescription.accept(description, record);
        });
    }
}
