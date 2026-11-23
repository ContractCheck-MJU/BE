package ac.mju.contractcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RiskPolicyTest {

    @Test
    @DisplayName("스펙 임계값(0.6, 0.5)과 경계값 0·1은 허용한다")
    void acceptsProbabilityRange() {
        assertThat(new RiskPolicy(0.6, 0.5).confidenceThreshold()).isEqualTo(0.6);
        assertThat(new RiskPolicy(0.0, 1.0).evidenceScoreThreshold()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("정합성: 임계값이 0~1을 벗어나거나 NaN이면 만들 수 없다")
    void rejectsOutOfRangeThresholds() {
        assertThatThrownBy(() -> new RiskPolicy(-0.1, 0.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RiskPolicy(0.6, 1.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RiskPolicy(Double.NaN, 0.5)).isInstanceOf(IllegalArgumentException.class);
    }
}
