package br.com.globoplast.oee.model;

public record SyncState(String source, String lastReceipt, Long lastErpId, long totalRecords) {}
