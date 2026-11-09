package ac.mju.contractcheck.domain;

import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * 분류 결과와 근거 검색 결과로 조항의 최종 위험 등급을 정한다.
 *
 * <ul>
 *   <li>TC-ANL-017: 위험 확률이 confidenceThreshold 미만이면 CAUTION + lowConfidence (HIGH로 확정하지 않음)</li>
 *   <li>TC-ANL-018: 리랭크 점수가 evidenceScoreThreshold 이상인 근거가 0건이면 CAUTION + 근거 부족</li>
 * </ul>
 *
 * 비위험 분류(is_risky=false) 처리 규칙은 스펙시트에 아직 명시되지 않아 SAFE 로만 반환한다.
 */
@RequiredArgsConstructor
public class RiskLevelResolver {

    @NonNull
    private final RiskPolicy policy;

    public RiskAssessment resolve(Classification classification, List<Double> evidenceScores) {
        if (!classification.risky()) {
            return new RiskAssessment(RiskLevel.SAFE, false, false);
        }
        boolean lowConfidence = classification.probability() < policy.confidenceThreshold();
        boolean evidenceInsufficient = evidenceScores.stream()
                .noneMatch(score -> score >= policy.evidenceScoreThreshold());
        RiskLevel level = (lowConfidence || evidenceInsufficient) ? RiskLevel.CAUTION : RiskLevel.HIGH;
        return new RiskAssessment(level, lowConfidence, evidenceInsufficient);
    }
}
