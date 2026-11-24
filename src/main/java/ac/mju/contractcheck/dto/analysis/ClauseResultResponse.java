package ac.mju.contractcheck.dto.analysis;

import ac.mju.contractcheck.domain.RiskResult;
import java.util.List;

/** 조항 하나의 분석 결과. 스펙 응답 필드: clauseId, riskLevel, riskType, prob, similarCases[]. */
public record ClauseResultResponse(long clauseId, String riskLevel, String riskType, double prob, List<Object> similarCases) {

    /** similarCases는 공정위 사례 검색(kftc_cases)이 생기기 전까지 비어 있다. */
    public static ClauseResultResponse from(RiskResult result) {
        return new ClauseResultResponse(result.clauseId(), result.level().name(), result.riskType(), result.probability(), List.of());
    }
}
