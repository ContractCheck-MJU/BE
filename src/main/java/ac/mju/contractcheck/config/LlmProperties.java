package ac.mju.contractcheck.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Claude 접속 설정(contractcheck.llm.*). 키는 환경변수(ANTHROPIC_API_KEY)로만 받는다.
 *
 * @param model Claude 모델 ID
 * @param effort 생각 깊이(low·medium·high·xhigh·max). 응답 목표 10초(TC-EXP-013)라 기본은 low다.
 * @param timeout 호출 한 번의 제한 시간. 넘으면 LLM_TIMEOUT이다.
 * @param maxRetries 연결 실패·429·5xx일 때 SDK가 다시 시도하는 횟수. 시간 초과도 다시 시도하므로 전체 대기는 timeout × (maxRetries + 1)까지다.
 * @param maxTokens 응답 최대 토큰. 설명·수정 문구 두 문단이라 짧게 둔다.
 */
@ConfigurationProperties(prefix = "contractcheck.llm")
public record LlmProperties(
        @DefaultValue("unset") String apiKey,
        @DefaultValue("claude-sonnet-5-5") String model,
        @DefaultValue("low") String effort,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("1") int maxRetries,
        @DefaultValue("4096") long maxTokens) {

    /** 잘못된 값이면 호출할 때가 아니라 시작할 때 실패한다. */
    public LlmProperties {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("contractcheck.llm.model은 비어 있을 수 없습니다.");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("contractcheck.llm.timeout은 0보다 커야 합니다.");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("contractcheck.llm.max-retries는 0 이상이어야 합니다.");
        }
        if (maxTokens < 1) {
            throw new IllegalArgumentException("contractcheck.llm.max-tokens는 1 이상이어야 합니다.");
        }
    }
}
