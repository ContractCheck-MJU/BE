package ac.mju.contractcheck.domain;

import ac.mju.contractcheck.domain.enums.ChangeType;

/**
 * 비교 결과의 조항 쌍 하나(comparison_pairs). ADDED는 이전 조항이, DELETED는 이후 조항이 없고 둘 다 유사도가 없다.
 *
 * @param riskDelta 위험 등급 점수(SAFE 0·CAUTION 1·HIGH 2)의 이후 − 이전. 양수면 더 위험해졌다.
 */
public record ComparisonPair(
        Long beforeClauseId, Long afterClauseId, Double similarity, ChangeType changeType, int riskDelta) {}
