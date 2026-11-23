package ac.mju.contractcheck.domain;

public record Explanation(long id, long clauseId, UserId owner, String tone, String text, String suggestion) {}
