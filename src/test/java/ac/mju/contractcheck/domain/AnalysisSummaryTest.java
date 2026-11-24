package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 종합 점수·요약의 임시 규칙(스펙 미정). 공식이 확정되면 이 테스트와 AnalysisSummary만 고친다. */
class AnalysisSummaryTest {

    @Test
    @DisplayName("위험·주의·정상 건수를 요약 문장으로 만든다")
    void summaryCountsEachLevel() {
        AnalysisSummary summary = AnalysisSummary.of(
                List.of(RiskLevel.HIGH, RiskLevel.HIGH, RiskLevel.CAUTION, RiskLevel.SAFE));

        assertThat(summary.summary()).isEqualTo("위험 2건, 주의 1건, 정상 1건");
    }

    @Test
    @DisplayName("모두 정상이면 100점, 모두 위험이면 0점이다")
    void scoreBounds() {
        assertThat(AnalysisSummary.of(List.of(RiskLevel.SAFE, RiskLevel.SAFE)).overallScore()).isEqualTo(100.0);
        assertThat(AnalysisSummary.of(List.of(RiskLevel.HIGH, RiskLevel.HIGH)).overallScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("주의는 위험의 절반만큼 감점한다")
    void cautionCountsHalf() {
        // 위험 1 + 주의 2 + 정상 1 → 감점 (1 + 1) / 4 = 50%
        AnalysisSummary summary = AnalysisSummary.of(
                List.of(RiskLevel.HIGH, RiskLevel.CAUTION, RiskLevel.CAUTION, RiskLevel.SAFE));

        assertThat(summary.overallScore()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("점수는 소수 둘째 자리까지 반올림한다(컬럼 NUMERIC(5,2))")
    void scoreIsRoundedToTwoDecimals() {
        AnalysisSummary summary = AnalysisSummary.of(List.of(RiskLevel.HIGH, RiskLevel.SAFE, RiskLevel.SAFE));

        assertThat(summary.overallScore()).isEqualTo(66.67);
    }

    @Test
    @DisplayName("조항이 하나도 없으면 점수를 정할 수 없다")
    void emptyIsRejected() {
        assertThatThrownBy(() -> AnalysisSummary.of(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
