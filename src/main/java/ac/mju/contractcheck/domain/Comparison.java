package ac.mju.contractcheck.domain;

import java.time.Instant;

/** 두 계약서의 비교 한 건(comparisons). 저장 전이면 id가 0이다. 조항 쌍은 {@link ComparisonPair}로 따로 저장한다. */
public record Comparison(long id, UserId owner, long beforeContractId, long afterContractId, Instant createdAt) {}
