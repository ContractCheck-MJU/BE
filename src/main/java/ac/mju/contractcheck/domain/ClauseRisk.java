package ac.mju.contractcheck.domain;

import java.util.List;

/**
 * 조항 하나의 위험 판정 결과(FR-ANL-01).
 *
 * @param embedding 조항 임베딩(BGE-M3 1024차원). clauses.embedding 저장과 비교(FR-CMP)에 쓴다.
 * @param evidence 위험 후보에 대한 근거 검색 결과. 위험이 아니면 검색하지 않아 비어 있다.
 */
public record ClauseRisk(
        long clauseId,
        String riskType,
        double probability,
        RiskAssessment assessment,
        String modelVersion,
        float[] embedding,
        List<EvidenceHit> evidence) {
}
