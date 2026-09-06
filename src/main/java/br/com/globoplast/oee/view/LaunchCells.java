package br.com.globoplast.oee.view;

import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.shared.Tooltip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LaunchCells {
    private final Function<String, String> translate;
    private final LongFunction<String> formatInteger;
    private final DoubleFunction<String> formatOneDecimal;
    private final DoubleFunction<String> formatDecimal;

    LaunchCells(Function<String, String> translate, LongFunction<String> formatInteger,
                DoubleFunction<String> formatOneDecimal, DoubleFunction<String> formatDecimal) {
        this.translate = translate;
        this.formatInteger = formatInteger;
        this.formatOneDecimal = formatOneDecimal;
        this.formatDecimal = formatDecimal;
    }

    Component fullText(String text) {
        String full = Norm.text(text);
        if (full.isBlank()) return new Span("—");
        Span value = new Span(full);
        value.addClassName("gp-full-text-cell-v110");
        value.getElement().setAttribute("tabindex", "0");
        value.getElement().setAttribute("aria-label", full);
        value.addAttachListener(event -> value.getElement().executeJs("""
            (() => {
                if (this.__gpTruncatedTitleV111) return;
                this.__gpTruncatedTitleV111 = true;
                this.addEventListener('pointerenter', () => {
                    const clipped = this.scrollWidth > this.clientWidth + 1 ||
                                    this.scrollHeight > this.clientHeight + 1;
                    if (clipped) this.setAttribute('title', this.innerText || this.textContent || '');
                    else this.removeAttribute('title');
                });
            })();
        """));
        return value;
    }

    Component order(LaunchRecord record) {
        List<String> ops = extractOps(record == null ? "" : record.getOrderNumber());
        if (ops.isEmpty()) return new Span("—");
        if (ops.size() == 1) return new Span(ops.get(0));

        List<Integer> totals = List.of();
        String detail = record.getProductionDetail();
        if (detail != null && !detail.isBlank()) {
            try {
                List<String> detailOps = extractJsonStringArray(detail, "ops");
                if (detailOps.equals(ops)) totals = extractJsonIntArray(detail, "totais");
            } catch (RuntimeException ignored) {
                totals = List.of();
            }
        }
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < ops.size(); index++) {
            String quantity = index < totals.size()
                    ? formatInteger.apply(Math.max(0, totals.get(index))) + " " + t("pçs") : "—";
            lines.add(ops.get(index) + " · " + quantity);
        }
        Span value = new Span(ops.get(0) + "...");
        value.addClassName("gp-launch-order-compact-v083");
        String full = String.join("\n", lines);
        tooltip(value, full);
        return value;
    }

    String productMetadataText(LaunchRecord record) {
        if (record == null) return "";
        List<String> parts = new ArrayList<>();
        String code = Norm.text(record.getProduct());
        String client = Norm.text(record.getClientErp());
        String description = Norm.text(record.getDescriptionErp());
        if (!code.isBlank()) parts.add(code);
        if (!client.isBlank()) parts.add(client);
        if (!description.isBlank()) parts.add(description);
        return String.join(" · ", parts);
    }

    Component product(LaunchRecord record) {
        String full = productMetadataText(record);
        if (full.isBlank()) return new Span("—");
        String code = Norm.text(record.getProduct());
        Span value = new Span(code.isBlank() ? full : code + (full.equals(code) ? "" : "..."));
        value.addClassName("gp-launch-product-description-v094");
        tooltip(value, full);
        return value;
    }

    Component oee(LaunchRecord record, int decimals) {
        Span wrapper = new Span();
        wrapper.addClassName("gp-oee-cell");
        String formatted = decimals <= 1
                ? formatOneDecimal.apply(record.getOeePct()) : formatDecimal.apply(record.getOeePct());
        Span value = new Span(formatted + "%");
        value.addClassName("gp-oee-value");
        if (record.getOeePct() < 85.0) value.addClassName("gp-oee-low");
        wrapper.add(value);
        String observation = meaningfulObservation(record.getProblem());
        if (!observation.isBlank()) {
            Icon info = VaadinIcon.INFO_CIRCLE.create();
            info.addClassName("gp-oee-observation-icon-v063");
            info.getElement().setAttribute("tabindex", "0");
            info.getElement().setAttribute("aria-label", t("Observação") + ": " + observation);
            Tooltip.forComponent(info).withText(observation)
                    .withPosition(Tooltip.TooltipPosition.TOP).withHoverDelay(150);
            wrapper.add(info);
        }
        return wrapper;
    }

    private void tooltip(Span value, String text) {
        value.getElement().setAttribute("tabindex", "0");
        value.getElement().setAttribute("aria-label", text);
        value.getElement().setAttribute("data-gp-tooltip", "true");
        Tooltip.forComponent(value).withText(text)
                .withPosition(Tooltip.TooltipPosition.TOP).withHoverDelay(150);
    }

    private static List<String> extractOps(String raw) {
        if (raw == null || raw.trim().isBlank()) return List.of();
        return Arrays.stream(raw.trim().split("\\s*[/;|]\\s*"))
                .map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    private static List<String> extractJsonStringArray(String json, String key) {
        Matcher array = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\[(.*?)]").matcher(json);
        if (!array.find()) return List.of();
        List<String> values = new ArrayList<>();
        Matcher quoted = Pattern.compile("\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(array.group(1));
        while (quoted.find()) values.add(jsonUnescape(quoted.group(1)));
        return values;
    }

    private static List<Integer> extractJsonIntArray(String json, String key) {
        Matcher array = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\[([^]]*)]").matcher(json);
        if (!array.find() || array.group(1).trim().isBlank()) return List.of();
        List<Integer> values = new ArrayList<>();
        for (String part : array.group(1).split(",")) values.add((int) Double.parseDouble(part.trim()));
        return values;
    }

    private static String jsonUnescape(String value) {
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (escaped) {
                switch (character) {
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case '\\' -> result.append('\\');
                    case '"' -> result.append('"');
                    default -> result.append(character);
                }
                escaped = false;
            } else if (character == '\\') escaped = true;
            else result.append(character);
        }
        if (escaped) result.append('\\');
        return result.toString();
    }

    private static String meaningfulObservation(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        return value.isBlank() || value.equalsIgnoreCase("Nenhum") || value.equalsIgnoreCase("None")
                || value.equalsIgnoreCase("Nan") || value.equals("-") ? "" : value;
    }

    private String t(String text) {
        return translate.apply(text);
    }
}
