package br.com.globoplast.oee.view;

import br.com.globoplast.oee.util.DisplayFormat;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.util.Locale;

/**
 * Painel industrial de indicadores para Resumo do Dia/Mês.
 * Os cálculos chegam prontos da visão consolidada; esta classe cuida apenas
 * da leitura visual, sem manter estado ou cache próprio.
 */
public final class OeeStatusPanel extends Div {

    public OeeStatusPanel(
            double oee,
            double availability,
            double performance,
            double quality,
            long produced,
            long target,
            double scrapKg,
            int equipmentCount,
            String panelTitle,
            String oeeLabel,
            String availabilityLabel,
            String performanceLabel,
            String qualityLabel,
            String producedLabel,
            String targetLabel,
            String scrapLabel,
            String equipmentLabel,
            Locale locale
    ) {
        addClassName("gp-oee-status-panel");
        Locale displayLocale = locale == null ? Locale.forLanguageTag("pt-BR") : locale;

        Div heading = new Div();
        heading.addClassName("gp-oee-panel-heading");
        Span title = new Span(panelTitle == null ? "" : panelTitle);
        title.addClassName("gp-oee-panel-title");
        heading.add(title);

        Div body = new Div();
        body.addClassName("gp-oee-panel-body");

        Div gauges = new Div();
        gauges.addClassName("gp-oee-status-gauges");
        gauges.add(
                gauge(oee, oeeLabel, true, displayLocale),
                gauge(availability, availabilityLabel, false, displayLocale),
                gauge(performance, performanceLabel, false, displayLocale),
                gauge(quality, qualityLabel, false, displayLocale)
        );

        Div output = new Div();
        output.addClassName("gp-oee-output");
        output.add(
                numberBlock(formatInt(displayLocale, produced), producedLabel),
                numberBlock(formatInt(displayLocale, target), targetLabel),
                numberBlock(formatDecimal(displayLocale, scrapKg, 1) + " kg", scrapLabel),
                numberBlock(DisplayFormat.integer(Math.max(0, equipmentCount), displayLocale), equipmentLabel)
        );

        body.add(gauges, output);
        add(heading, body);
    }

    private Div gauge(double raw, String label, boolean main, Locale locale) {
        double value = Double.isFinite(raw) ? raw : 0.0;
        double visual = Math.max(0.0, Math.min(100.0, value));

        Div ring = new Div();
        ring.addClassNames("gp-oee-gauge", main ? "gp-oee-gauge-main" : "gp-oee-gauge-small");
        ring.getStyle().set("--gp-oee-angle", String.format(Locale.ROOT, "%.3fdeg", visual * 3.6));
        if (value < 85.0 && main) ring.addClassName("gp-oee-gauge-low");

        Div inner = new Div();
        inner.addClassName("gp-oee-gauge-inner");
        Span valueText = new Span(DisplayFormat.decimal(value, 1, locale) + "%");
        valueText.addClassName("gp-oee-gauge-value");
        Span title = new Span(label == null ? "" : label);
        title.addClassName("gp-oee-gauge-label");
        inner.add(valueText, title);
        ring.add(inner);
        return ring;
    }

    private Div numberBlock(String value, String label) {
        Div block = new Div();
        block.addClassName("gp-oee-output-block");
        Span number = new Span(value);
        number.addClassName("gp-oee-output-value");
        Span text = new Span(label == null ? "" : label);
        text.addClassName("gp-oee-output-label");
        block.add(number, text);
        return block;
    }

    private String formatInt(Locale locale, long value) {
        return DisplayFormat.integer(value, locale);
    }

    private String formatDecimal(Locale locale, double value, int decimals) {
        return DisplayFormat.decimal(value, decimals, locale);
    }
}
