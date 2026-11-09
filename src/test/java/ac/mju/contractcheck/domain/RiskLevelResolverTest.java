package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 근거: 스펙시트 'TC-위험분석' TC-ANL-017·018, 'AI·RAG 파이프라인' 7단계(리랭크 점수 임계 0.5). */
class RiskLevelResolverTest {

    private final RiskLevelResolver resolver = new RiskLevelResolver(new RiskPolicy(0.6, 0.5));

    @Test
    @DisplayName("TC-ANL-017: 위험 확률 0.52 < 임계 0.6 이면 HIGH로 확정하지 않고 CAUTION + lowConfidence")
    void lowConfidenceIsCaution() {
        RiskAssessment result = resolver.resolve(new Classification(true, 0.52), List.of(0.8));

        assertThat(result.level()).isEqualTo(RiskLevel.CAUTION);
        assertThat(result.lowConfidence()).isTrue();
    }

    @Test
    @DisplayName("TC-ANL-018: 위험 확률 0.9 라도 리랭크 0.5 이상 근거가 0건이면 CAUTION + 근거 부족")
    void noEvidenceDowngradesToCaution() {
        RiskAssessment result = resolver.resolve(new Classification(true, 0.9), List.of(0.49, 0.3));

        assertThat(result.level()).isEqualTo(RiskLevel.CAUTION);
        assertThat(result.evidenceInsufficient()).isTrue();
        assertThat(result.lowConfidence()).isFalse();
    }

    @Test
    @DisplayName("TC-ANL-018 대조군: 확률·근거 모두 충족하면 HIGH 유지")
    void confidentWithEvidenceIsHigh() {
        RiskAssessment result = resolver.resolve(new Classification(true, 0.9), List.of(0.5));

        assertThat(result.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.evidenceInsufficient()).isFalse();
    }
}
