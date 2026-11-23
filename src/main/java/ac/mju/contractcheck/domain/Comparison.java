package ac.mju.contractcheck.domain;

public record Comparison(long id, UserId owner, long beforeContractId, long afterContractId) {}
