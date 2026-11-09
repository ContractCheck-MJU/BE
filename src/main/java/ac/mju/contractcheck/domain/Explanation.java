package ac.mju.contractcheck.domain;

public record Explanation(long id, long clauseId, String owner, String tone, String text, String suggestion) {}
