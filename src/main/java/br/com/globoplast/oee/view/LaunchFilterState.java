package br.com.globoplast.oee.view;

import java.time.LocalDate;
import java.util.Set;
import com.vaadin.flow.data.provider.SortDirection;

record LaunchFilterState(LocalDate start, LocalDate end, String search,
                         Set<String> sectors, Set<String> machines,
                         Set<String> clients, int limit, String sort, SortDirection sortDirection) {}
