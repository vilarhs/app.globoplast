package br.com.globoplast.oee.view;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextField;

final class LaunchFormFields {
    Div root;
    DateRangePicker date;
    ComboBox<String> machine;
    TextField capacity;
    TextField product;
    TextField order;
    TextField hours;
    TextField weight;
    TextField shiftA;
    TextField scrapA;
    TextField shiftB;
    TextField scrapB;
    TextField shiftC;
    TextField scrapC;
    TextField changeovers;
    TextField setup;
    TextField breakdown;
    TextField observations;
}
