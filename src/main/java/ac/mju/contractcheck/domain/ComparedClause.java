package ac.mju.contractcheck.domain;

import ac.mju.contractcheck.domain.enums.RiskLevel;

/**
 * 비교 결과에 붙이는 조항 한쪽의 정보. 위험 등급은 조회 시점의 최신 분석 완료 건 기준이며, 판정이 없으면 null이다.
 */
public record ComparedClause(long clauseId, int no, String title, String text, RiskLevel riskLevel) {}
