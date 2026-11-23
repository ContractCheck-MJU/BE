package ac.mju.contractcheck.config;

import static org.assertj.core.api.Assertions.assertThat;

import ac.mju.contractcheck.domain.RiskLevelResolver;
import ac.mju.contractcheck.domain.RiskPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 임계값 설정(contractcheck.analysis.*)이 domain의 RiskPolicy와 RiskLevelResolver로 이어지는지 확인한다. */
class AnalysisConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(AnalysisConfig.class);

    @Test
    @DisplayName("설정값이 RiskPolicy 빈으로 바인딩되고 RiskLevelResolver 빈이 만들어진다")
    void bindsThresholdsFromProperties() {
        runner.withPropertyValues(
                        "contractcheck.analysis.confidence-threshold=0.7",
                        "contractcheck.analysis.evidence-score-threshold=0.4")
                .run(context -> {
                    assertThat(context.getBean(RiskPolicy.class)).isEqualTo(new RiskPolicy(0.7, 0.4));
                    assertThat(context).hasSingleBean(RiskLevelResolver.class);
                });
    }

    @Test
    @DisplayName("범위를 벗어난 임계값이면 앱이 시작되지 않는다")
    void invalidThresholdFailsStartup() {
        runner.withPropertyValues(
                        "contractcheck.analysis.confidence-threshold=1.5",
                        "contractcheck.analysis.evidence-score-threshold=0.5")
                .run(context -> assertThat(context).hasFailed());
    }
}
