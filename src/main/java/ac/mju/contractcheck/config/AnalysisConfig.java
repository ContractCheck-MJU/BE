package ac.mju.contractcheck.config;

import ac.mju.contractcheck.domain.RiskLevelResolver;
import ac.mju.contractcheck.domain.RiskPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RiskPolicy.class)
public class AnalysisConfig {

    @Bean
    RiskLevelResolver riskLevelResolver(RiskPolicy policy) {
        return new RiskLevelResolver(policy);
    }
}
