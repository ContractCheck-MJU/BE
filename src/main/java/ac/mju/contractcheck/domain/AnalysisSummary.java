package ac.mju.contractcheck.domain;

import ac.mju.contractcheck.domain.enums.RiskLevel;
import java.util.List;

/**
 * 분석의 종합 점수와 요약.
 *
 * <p>스펙 미정: 스펙시트에 overall_score·summary 산출 공식이 없어 임시 규칙을 쓴다.
 * 점수는 100에서 (위험 건수 + 주의 건수 × 0.5) / 전체 조항 수 비율만큼 감점한 값이고, 요약은 등급별 건수 문장이다.
 * 공식이 확정되면 이 클래스만 고친다.
 */
public record AnalysisSummary(double overallScore, String summary) {

    private static final double CAUTION_WEIGHT = 0.5;

    public static AnalysisSummary of(List<RiskLevel> levels) {
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("조항이 없어 종합 점수를 정할 수 없습니다.");
        }
        long high = levels.stream().filter(level -> level == RiskLevel.HIGH).count();
        long caution = levels.stream().filter(level -> level == RiskLevel.CAUTION).count();
        long safe = levels.size() - high - caution;
        double penalty = (high + caution * CAUTION_WEIGHT) / levels.size();
        double score = Math.round((1 - penalty) * 10000) / 100.0;
        return new AnalysisSummary(score, "위험 " + high + "건, 주의 " + caution + "건, 정상 " + safe + "건");
    }
}
