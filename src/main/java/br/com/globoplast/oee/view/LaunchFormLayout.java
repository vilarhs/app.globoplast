package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.LaunchRecord;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextField;

import java.util.function.Function;

final class LaunchFormLayout {
    private LaunchFormLayout() {}

    static Div build(LaunchFormFields fields, LaunchRecord record, boolean showTime,
                     Function<String, String> translate, Function<String, String> formatDate) {
        Div rowDate = new Div(fields.date);
        rowDate.addClassNames("gp-launch-row", "gp-launch-row-date");
        Div time = new Div();
        time.addClassName("gp-launch-time");
        if (showTime) {
            String hour = record.getLaunchTime() == null || record.getLaunchTime().isBlank()
                    ? "—" : record.getLaunchTime();
            String edited = record.getEditedAt();
            time.setText(translate.apply("Hora do lançamento") + ": " + hour
                    + (edited == null || edited.isBlank()
                    ? "" : " · " + translate.apply("Editado") + ": " + formatDate.apply(edited)));
        }

        Div rowBasic = new Div(fields.order, fields.product, fields.weight);
        rowBasic.addClassNames("gp-launch-row", "gp-launch-row-3");
        Div rowMachine = new Div(fields.machine, fields.capacity, fields.hours);
        rowMachine.addClassNames("gp-launch-row", "gp-launch-row-3");
        Div shiftA = shift(fields.shiftA, fields.scrapA);
        Div shiftB = shift(fields.shiftB, fields.scrapB);
        Div shiftC = shift(fields.shiftC, fields.scrapC);
        Div rowShifts = new Div(shiftA, shiftB, shiftC);
        rowShifts.addClassNames("gp-launch-row", "gp-launch-row-3");
        Div rowStops = new Div(fields.changeovers, fields.setup, fields.breakdown);
        rowStops.addClassNames("gp-launch-row", "gp-launch-row-3");
        Div rowObs = new Div(fields.observations);
        rowObs.addClassNames("gp-launch-row", "gp-launch-row-observations");

        Div root = new Div();
        root.addClassName("gp-launch-form-python");
        root.add(rowDate);
        if (showTime) root.add(time);
        root.add(rowBasic, rowMachine, rowShifts, rowStops, rowObs);
        return root;
    }

    private static Div shift(TextField production, TextField scrap) {
        Div column = new Div(production, scrap);
        column.addClassName("gp-launch-shift-column");
        return column;
    }
}
