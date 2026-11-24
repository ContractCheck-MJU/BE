package ac.mju.contractcheck.domain;

import java.util.List;

/**
 * 설명을 만들 때 Claude에 넘기는 재료.
 *
 * @param maskedClause 개인정보를 가린 조항 본문. 원문은 외부로 보내지 않는다.
 * @param riskLevel 최신 분석의 위험 등급(HIGH·CAUTION·SAFE). 분석 결과에 조항이 없으면 빈 문자열이다.
 * @param riskType 위험 유형. 없으면 빈 문자열이다.
 * @param evidence 근거 임계값을 넘은 법령·판례 청크. 없을 수 있다.
 */
public record ExplanationContext(String maskedClause, String riskLevel, String riskType, List<EvidenceHit> evidence) {

    public ExplanationContext {
        if (maskedClause == null || maskedClause.isBlank()) {
            throw new IllegalArgumentException("조항 본문이 비어 있습니다.");
        }
        riskLevel = riskLevel == null ? "" : riskLevel;
        riskType = riskType == null ? "" : riskType;
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
