package ac.mju.contractcheck.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Python 추론 서버 접속 설정(contractcheck.inference.*). 키는 환경변수로만 받는다.
 *
 * @param timeout 호출 한 번의 제한 시간(스펙: 10초)
 * @param maxAttempts 실패 시 포함한 총 시도 횟수. 재시도 횟수는 스펙에 없어 제안값이다.
 * @param retryBackoff 재시도 사이 대기. 시도 횟수만큼 곱해 늘린다.
 * @param batchSize 한 번에 보낼 최대 개수(스펙: 64)
 */
@ConfigurationProperties(prefix = "contractcheck.inference")
public record InferenceProperties(
        @DefaultValue("http://localhost:8000") String baseUrl,
        @DefaultValue("unset") String apiKey,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("200ms") Duration retryBackoff,
        @DefaultValue("64") int batchSize) {
}
