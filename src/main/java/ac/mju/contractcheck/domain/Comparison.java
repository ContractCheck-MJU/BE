package ac.mju.contractcheck.domain;

public record Comparison(long id, String owner, long beforeContractId, long afterContractId) {}
