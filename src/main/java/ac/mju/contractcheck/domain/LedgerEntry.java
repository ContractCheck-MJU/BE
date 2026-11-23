package ac.mju.contractcheck.domain;

public record LedgerEntry(UserId owner, String type, int amount, String refId, String createdAt) {}
