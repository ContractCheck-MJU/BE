package ac.mju.contractcheck.domain;

/** 이전 계약 조항과 이후 계약 조항의 임베딩 코사인 유사도(1 − 코사인 거리). */
public record ClauseSimilarity(long beforeClauseId, long afterClauseId, double similarity) {}
