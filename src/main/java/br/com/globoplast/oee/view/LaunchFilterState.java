package br.com.globoplast.oee.view;

import java.time.LocalDate;
import java.util.Set;

record LaunchFilterState(LocalDate start, LocalDate end, String search,
                         Set<String> sectors, Set<String> machines,
                         Set<String> clients, int limit) {}
