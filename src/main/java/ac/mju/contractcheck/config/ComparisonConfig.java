package ac.mju.contractcheck.config;

import ac.mju.contractcheck.domain.ComparisonPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ComparisonProperties.class)
public class ComparisonConfig {

    /** 설정값으로 정책 객체를 만든다. 범위를 벗어난 값이면 여기서 앱 시작이 실패한다. */
    @Bean
    ComparisonPolicy comparisonPolicy(ComparisonProperties properties) {
        return new ComparisonPolicy(properties.matchThreshold());
    }
}
