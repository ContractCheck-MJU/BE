package ac.mju.contractcheck.config;

import ac.mju.contractcheck.domain.RiskLevelResolver;
import ac.mju.contractcheck.domain.RiskPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(AnalysisProperties.class)
public class AnalysisConfig {

    /** 설정값으로 정책 객체를 만든다. 범위를 벗어난 값이면 여기서 앱 시작이 실패한다. */
    @Bean
    RiskPolicy riskPolicy(AnalysisProperties properties) {
        return new RiskPolicy(properties.confidenceThreshold(), properties.evidenceScoreThreshold());
    }

    @Bean
    RiskLevelResolver riskLevelResolver(RiskPolicy policy) {
        return new RiskLevelResolver(policy);
    }

    /**
     * 분석 판정을 요청 스레드 밖에서 돌리는 실행기. 대기열이 가득 차면 접수를 거절해(RejectedExecutionException)
     * 서비스가 FAILED 처리와 이용권 복구를 한다.
     */
    @Bean(name = "analysisExecutor")
    ThreadPoolTaskExecutor analysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("analysis-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
