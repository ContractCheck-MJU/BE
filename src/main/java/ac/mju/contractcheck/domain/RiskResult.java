package ac.mju.contractcheck.domain;

import ac.mju.contractcheck.domain.enums.RiskLevel;

/**
 * 조항 하나의 위험 판정 저장값(risk_results). 위험 후보로 분류됐으면 {@code risky}이고, 등급이 SAFE가 아니다.
 * 스펙 미정: similar_case_ids는 공정위 사례 검색(kftc_cases)이 생기면 채운다.
 */
public record RiskResult(long clauseId, boolean risky, RiskLevel level, String riskType, double probability) {

    public static RiskResult from(ClauseRisk risk) {
        RiskLevel level = risk.assessment().level();
        return new RiskResult(risk.clauseId(), level != RiskLevel.SAFE, level, risk.riskType(), risk.probability());
    }
}
