package br.com.globoplast.oee.view;

import br.com.globoplast.oee.util.Norm;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.select.Select;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Campo de período inspirado no calendário do app Python.
 *
 * O campo permanece dentro do formulário/filtro, mas o calendário é um
 * Popover independente. Assim, ao abrir o calendário ele flutua sobre a
 * página e nunca aumenta a altura do dropdown de filtros.
 */
public final class DateRangePicker extends Div {
    private final String language;
    private final Function<String, String> tr;
    private final LocalDate min;
    private final LocalDate max;
    private Runnable onChange;
    private final boolean singleDate;

    private final Span label = new Span();
    private final Button field = new Button();
    private final Popover calendar = new Popover();
    private final Div panel = new Div();
    private final Div days = new Div();
    private final Div weekdays = new Div();
    private final Select<Integer> month = new Select<>();
    private final Select<Integer> year = new Select<>();

    private LocalDate start;
    private LocalDate end;
    private LocalDate anchor;
    private YearMonth visibleMonth;
    private boolean updatingHeader;
    private boolean all;

    public DateRangePicker(
            String labelText,
            LocalDate start,
            LocalDate end,
            LocalDate min,
            LocalDate max,
            String language,
            Function<String, String> translator,
            Runnable onChange
    ) {
        this(labelText, start, end, min, max, language, translator, onChange, false);
    }

    public DateRangePicker(
            String labelText,
            LocalDate start,
            LocalDate end,
            LocalDate min,
            LocalDate max,
            String language,
            Function<String, String> translator,
            Runnable onChange,
            boolean singleDate
    ) {
        this.singleDate = singleDate;
        this.language = "en-US".equals(language) ? "en-US" : "pt-BR";
        this.tr = translator == null ? Function.identity() : translator;
        LocalDate today = Norm.productiveToday();
        LocalDate safeMin = min == null ? today.minusYears(10) : min;
        LocalDate safeMax = max == null ? today.plusYears(2) : max;
        if (safeMax.isBefore(safeMin)) {
            LocalDate swap = safeMin;
            safeMin = safeMax;
            safeMax = swap;
        }
        this.min = safeMin;
        this.max = safeMax;
        this.onChange = onChange == null ? () -> { } : onChange;

        this.start = clamp(start == null ? today : start);
        this.end = singleDate ? this.start : clamp(end == null ? this.start : end);
        normalizeRange();
        this.all = !singleDate && Objects.equals(this.start, this.min) && Objects.equals(this.end, this.max);
        this.visibleMonth = YearMonth.from(this.start);

        addClassName("gp-period-picker");
        label.addClassName("gp-period-label");
        label.setText(labelText == null ? tr.apply("Período") : labelText);

        field.addClassName("gp-period-field");
        field.setIcon(VaadinIcon.CALENDAR.create());
        field.setIconAfterText(true);
        field.getElement().setAttribute("aria-haspopup", "dialog");
        field.getElement().setAttribute("aria-label", label.getText());
        updateFieldText();

        buildPanel();
        calendar.setTarget(field);
        calendar.setPosition(PopoverPosition.BOTTOM_START);
        calendar.setWidth("min(340px, calc(100vw - 24px))");
        calendar.setModal(true);
        calendar.setBackdropVisible(false);
        calendar.setCloseOnOutsideClick(true);
        calendar.setCloseOnEsc(true);
        calendar.setAriaLabel(label.getText());
        calendar.addClassName("gp-period-popover");
        calendar.add(panel);
        calendar.addOpenedChangeListener(e -> {
            if (e.isOpened()) renderCalendar();
        });

        add(label, field, calendar);
        renderCalendar();
    }

    public LocalDate getStart() {
        return start;
    }

    public LocalDate getEnd() {
        return end;
    }

    public LocalDate getValue() {
        return start;
    }

    public boolean isAll() {
        return all;
    }

    public void setChangeListener(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

    public void setReadOnly(boolean readOnly) {
        field.setEnabled(!readOnly);
        field.getElement().setAttribute("aria-readonly", String.valueOf(readOnly));
        if (readOnly) addClassName("gp-period-readonly");
        else removeClassName("gp-period-readonly");
    }

    public void focus() {
        field.focus();
    }

    public void setValue(LocalDate start, LocalDate end) {
        this.start = clamp(start == null ? this.start : start);
        this.end = singleDate ? this.start : clamp(end == null ? this.start : end);
        normalizeRange();
        all = false;
        visibleMonth = YearMonth.from(this.start);
        anchor = null;
        updateFieldText();
        renderCalendar();
    }

    public void setValue(LocalDate value) {
        setValue(value, value);
    }

    private void buildPanel() {
        panel.addClassName("gp-period-panel");

        Button prev = new Button("‹");
        Button next = new Button("›");
        prev.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        next.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        prev.addClassName("gp-period-nav");
        next.addClassName("gp-period-nav");
        prev.setTooltipText(tr.apply("Mês anterior")).withPosition(com.vaadin.flow.component.shared.Tooltip.TooltipPosition.TOP);
        next.setTooltipText(tr.apply("Próximo mês")).withPosition(com.vaadin.flow.component.shared.Tooltip.TooltipPosition.TOP);
        prev.addClickListener(e -> changeMonth(-1));
        next.addClickListener(e -> changeMonth(1));

        month.addClassName("gp-period-month");
        month.getElement().setAttribute("aria-label", tr.apply("Mês"));
        List<Integer> months = IntStream.rangeClosed(1, 12).boxed().toList();
        month.setItems(months);
        month.setItemLabelGenerator(this::monthName);
        month.addValueChangeListener(e -> {
            if (updatingHeader || e.getValue() == null) return;
            visibleMonth = YearMonth.of(visibleMonth.getYear(), e.getValue());
            selectVisibleMonth();
        });

        year.addClassName("gp-period-year");
        year.getElement().setAttribute("aria-label", tr.apply("Ano"));
        List<Integer> years = IntStream.rangeClosed(min.getYear(), max.getYear()).boxed().toList();
        year.setItems(years);
        year.addValueChangeListener(e -> {
            if (updatingHeader || e.getValue() == null) return;
            int m = visibleMonth.getMonthValue();
            YearMonth candidate = YearMonth.of(e.getValue(), m);
            visibleMonth = clampMonth(candidate);
            renderCalendar();
        });

        Div header = new Div(prev, month, year, next);
        header.addClassName("gp-period-header");

        weekdays.addClassName("gp-period-weekdays");
        days.addClassName("gp-period-days");

        Button clear = new Button(tr.apply("Limpar"));
        clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        clear.addClassName("gp-period-clear");
        clear.addClickListener(e -> {
            if (singleDate) {
                LocalDate today = clamp(Norm.productiveToday());
                start = today;
                end = today;
                all = false;
            } else {
                start = min;
                end = max;
                all = true;
            }
            anchor = null;
            visibleMonth = clampMonth(YearMonth.from(clamp(Norm.productiveToday())));
            updateFieldText();
            renderCalendar();
            calendar.setOpened(false);
            onChange.run();
        });

        Div footer = new Div(clear);
        footer.addClassName("gp-period-footer");
        panel.add(header, weekdays, days, footer);
    }

    private void selectVisibleMonth() {
        if (singleDate) {
            renderCalendar();
            return;
        }
        LocalDate first = visibleMonth.atDay(1);
        LocalDate last = visibleMonth.atEndOfMonth();
        if (last.isBefore(min) || first.isAfter(max)) {
            renderCalendar();
            return;
        }
        start = clamp(first);
        end = clamp(last);
        normalizeRange();
        all = false;
        anchor = null;
        updateFieldText();
        renderCalendar();
        onChange.run();
    }

    private void changeMonth(int delta) {
        YearMonth candidate = visibleMonth.plusMonths(delta);
        YearMonth clamped = clampMonth(candidate);
        if (!Objects.equals(clamped, visibleMonth)) {
            visibleMonth = clamped;
            renderCalendar();
        }
    }

    private YearMonth clampMonth(YearMonth value) {
        YearMonth minMonth = YearMonth.from(min);
        YearMonth maxMonth = YearMonth.from(max);
        if (value.isBefore(minMonth)) return minMonth;
        if (value.isAfter(maxMonth)) return maxMonth;
        return value;
    }

    private void renderCalendar() {
        updatingHeader = true;
        month.setValue(visibleMonth.getMonthValue());
        year.setValue(visibleMonth.getYear());
        updatingHeader = false;

        renderWeekdays();
        days.removeAll();

        LocalDate first = visibleMonth.atDay(1);
        int offset = first.getDayOfWeek().getValue() % 7; // domingo = 0
        LocalDate gridStart = first.minusDays(offset);

        for (int i = 0; i < 42; i++) {
            LocalDate date = gridStart.plusDays(i);
            Button day = new Button(String.valueOf(date.getDayOfMonth()));
            // Sem variante tertiary-inline: ela sobrescrevia visualmente o estado
            // selecionado em alguns temas do Vaadin. O calendário usa somente
            // as classes próprias do componente.
            day.addClassName("gp-period-day");
            if (date.getMonth() != visibleMonth.getMonth()) day.addClassName("gp-period-outside");
            if (date.isBefore(min) || date.isAfter(max)) {
                day.setEnabled(false);
            } else {
                boolean inRange = !all && !date.isBefore(start) && !date.isAfter(end);
                boolean isStart = !all && date.equals(start);
                boolean isEnd = !all && date.equals(end);
                if (inRange) day.addClassName("gp-period-in-range");
                if (isStart) day.addClassName("gp-period-start");
                if (isEnd) day.addClassName("gp-period-end");
                if (isStart || isEnd) {
                    day.addClassName("gp-period-selected");
                    day.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                    day.getElement().setAttribute("aria-selected", "true");
                    day.getElement().getStyle().set("--lumo-primary-color", "#ff4b4b");
                    day.getElement().getStyle().set("--lumo-primary-text-color", "#ffffff");
                    day.getElement().getStyle().set("color", "#ffffff");
                    day.getElement().getStyle().set("font-weight", "600");
                    day.getElement().getStyle().set("border-radius", "50%");
                }
                day.addClickListener(e -> choose(date));
            }
            days.add(day);
        }
    }

    private void renderWeekdays() {
        weekdays.removeAll();
        DayOfWeek[] order = {
                DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY
        };
        Locale locale = locale();
        for (DayOfWeek d : order) {
            String text = d.getDisplayName(TextStyle.SHORT, locale).replace(".", "");
            if (text.length() > 3) text = text.substring(0, 3);
            Span item = new Span(text);
            item.addClassName("gp-period-weekday");
            weekdays.add(item);
        }
    }

    private void choose(LocalDate date) {
        all = false;
        if (singleDate) {
            anchor = null;
            start = date;
            end = date;
            normalizeRange();
            updateFieldText();
            renderCalendar();
            onChange.run();
            calendar.setOpened(false);
            return;
        }
        if (anchor == null) {
            anchor = date;
            start = date;
            end = date;
        } else {
            start = date.isBefore(anchor) ? date : anchor;
            end = date.isBefore(anchor) ? anchor : date;
            anchor = null;
        }
        normalizeRange();
        updateFieldText();
        renderCalendar();
        onChange.run();
        if (anchor == null) calendar.setOpened(false);
    }

    private void normalizeRange() {
        start = clamp(start);
        end = clamp(end);
        if (end.isBefore(start)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
    }

    private LocalDate clamp(LocalDate value) {
        if (value == null) return min;
        if (value.isBefore(min)) return min;
        if (value.isAfter(max)) return max;
        return value;
    }

    private String monthName(Integer monthNumber) {
        if (monthNumber == null) return "";
        String value = Month.of(monthNumber).getDisplayName(TextStyle.FULL, locale());
        if (value.isBlank()) return value;
        return value.substring(0, 1).toUpperCase(locale()) + value.substring(1);
    }

    private Locale locale() {
        return "en-US".equals(language) ? Locale.US : Locale.forLanguageTag("pt-BR");
    }

    private void updateFieldText() {
        if (all) {
            field.setText(tr.apply("Todas"));
            return;
        }
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale());
        String text = start.equals(end) ? f.format(start) : f.format(start) + " – " + f.format(end);
        field.setText(text);
    }
}
