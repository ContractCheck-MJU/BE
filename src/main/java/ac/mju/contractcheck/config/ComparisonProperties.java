package ac.mju.contractcheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 조항 비교 임계값 설정(contractcheck.comparison.*). domain의 ComparisonPolicy는 이 값으로 {@link ComparisonConfig}가 만든다. */
@ConfigurationProperties(prefix = "contractcheck.comparison")
public record ComparisonProperties(double matchThreshold) {
}
