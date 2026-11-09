package ac.mju.contractcheck.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 등급 결정 임계값.
 *
 * @param confidenceThreshold 위험 확률이 이 값 미만이면 HIGH로 확정하지 않는다 (TC-ANL-017: 0.6)
 * @param evidenceScoreThreshold 리랭크 점수가 이 값 이상인 근거만 인정한다 (AI·RAG 파이프라인 7단계: 0.5)
 */
@ConfigurationProperties(prefix = "contractcheck.analysis")
public record RiskPolicy(double confidenceThreshold, double evidenceScoreThreshold) {
}
