package ac.mju.contractcheck.domain;

public record Analysis(long id, long contractId, String owner, String status, double overallScore, String summary) {}
