package br.com.globoplast.oee.view;

import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Shared behavior for the filter multi-select controls. */
final class FilterControls {
    private FilterControls() { }

    static Button searchButton() {
        Button button = new Button();
        Span glyph = new Span();
        glyph.addClassName("gp-search-filter-funnel-v044");
        glyph.getElement().setAttribute("aria-hidden", "true");
        glyph.getElement().setProperty("innerHTML",
                "<svg viewBox=\"0 0 24 24\" width=\"18\" height=\"18\" aria-hidden=\"true\" focusable=\"false\">" +
                "<path d=\"M3.5 5.25h17l-6.7 7.55v5.15l-3.6 1.8V12.8L3.5 5.25Z\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linejoin=\"round\"/>" +
                "</svg>");
        button.setIcon(glyph);
        button.addClassName("gp-search-filter-button-v044");
        return button;
    }

    static void updateButton(Button button, boolean active) {
        if (button == null) return;
        if (active) button.addClassName("gp-filter-active");
        else button.removeClassName("gp-filter-active");
    }

    static void updateOptions(MultiSelectComboBox<String> field, List<String> options) {
        List<String> safe = options == null ? List.of() : options;
        LinkedHashSet<String> valid = new LinkedHashSet<>(field.getValue());
        valid.removeIf(value -> !safe.contains(value));
        field.setItems(safe);
        if (!Objects.equals(valid, field.getValue())) field.setValue(valid);
    }

    static MultiSelectComboBox<String> multiSelect(
            String label, List<String> items, Set<String> selected, String placeholder) {
        MultiSelectComboBox<String> box = new MultiSelectComboBox<>(label);
        box.setItems(items);
        box.setPlaceholder(placeholder);
        box.getElement().setProperty("clearButtonVisible", true);
        box.setKeepFilter(false);
        box.getElement().setProperty("keepFilter", false);
        box.setSelectedItemsOnTop(true);
        if (selected != null && !selected.isEmpty()) {
            box.select(selected.stream().filter(items::contains).toList());
        }
        box.setWidthFull();
        box.addClassName("gp-filter-multiselect");
        box.addValueChangeListener(e -> clearSearchText(box));
        box.addAttachListener(e -> clearSearchText(box));
        return box;
    }

    static void forceUppercaseSectorFilter(MultiSelectComboBox<?> box) {
        box.addClassName("gp-uppercase-sector-filter-v061");
        box.addAttachListener(e -> box.getElement().executeJs("""
            const host=this;
            const apply=()=>{
              const input=host.inputElement || host.shadowRoot?.querySelector('input');
              if(!input)return;
              const update=()=>{
                if(String(input.value||'').length>0){
                  input.style.setProperty('text-transform','uppercase','important');
                }else{
                  input.style.removeProperty('text-transform');
                }
              };
              if(!input.__gpUppercaseSectorContentV063){
                input.__gpUppercaseSectorContentV063=true;
                input.addEventListener('input',update,true);
              }
              update();
            };
            apply();
            requestAnimationFrame(apply);
            if(!host.__gpUppercaseSectorV061){
              host.__gpUppercaseSectorV061=true;
              host.addEventListener('opened-changed',apply);
            }
        """));
    }

    private static void clearSearchText(MultiSelectComboBox<?> box) {
        box.setKeepFilter(false);
        box.getElement().setProperty("keepFilter", false);
        box.getElement().setProperty("filter", "");
        box.getElement().executeJs("""
            const host=this;
            const clearTree=(root)=>{
              if(!root)return;
              if(root.nodeType===1){
                const tag=(root.tagName||'').toLowerCase();
                if(tag.includes('combo-box')){
                  try{root.keepFilter=false;}catch(e){}
                  try{root.filter='';}catch(e){}
                  try{if('_filter' in root)root._filter='';}catch(e){}
                }
                if(tag==='input'){
                  try{root.value='';}catch(e){}
                  try{root.removeAttribute('value');}catch(e){}
                }
              }
              if(root.shadowRoot)clearTree(root.shadowRoot);
              const children=root.children||root.childNodes||[];
              Array.from(children).forEach(clearTree);
            };
            const clearResidual=()=>{
              host.keepFilter=false;
              try{host.filter='';}catch(e){}
              try{if('_filter' in host)host._filter='';}catch(e){}
              clearTree(host);
            };
            const schedule=()=>{
              clearResidual();
              requestAnimationFrame(clearResidual);
              setTimeout(clearResidual,0);
              setTimeout(clearResidual,40);
            };
            const selected=()=>{
              schedule();
              requestAnimationFrame(()=>{try{host.opened=false;}catch(e){}});
            };
            if(!host.__gpMultiSelectSingleRenderV052){
              host.__gpMultiSelectSingleRenderV052=true;
              host.keepFilter=false;
              host.addEventListener('selected-items-changed',selected);
              host.addEventListener('opened-changed',event=>{
                if(event.detail&&event.detail.value)schedule();
              });
            }
            schedule();
        """);
    }
}
