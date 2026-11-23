package ac.mju.contractcheck.domain;

public record Analysis(long id, long contractId, UserId owner, String status, double overallScore, String summary) {}
