package ac.mju.contractcheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 등급 결정 임계값 설정(contractcheck.analysis.*). domain의 RiskPolicy는 이 값으로 {@link AnalysisConfig}가 만든다. */
@ConfigurationProperties(prefix = "contractcheck.analysis")
public record AnalysisProperties(double confidenceThreshold, double evidenceScoreThreshold) {
}
