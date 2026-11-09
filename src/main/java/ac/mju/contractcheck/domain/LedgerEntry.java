package ac.mju.contractcheck.domain;

public record LedgerEntry(String owner, String type, int amount, String refId, String createdAt) {}
