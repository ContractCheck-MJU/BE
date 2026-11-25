package ac.mju.contractcheck.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Python 추론 서버 접속 설정(contractcheck.inference.*). 키는 환경변수로만 받는다.
 * 분류(/classify)와 임베딩(/embed)은 다른 곳에서 돌 수 있어 주소를 따로 둔다. 운영은 분류가 서버 컨테이너,
 * 임베딩이 메모리가 넉넉한 별도 PC다. 로컬은 둘 다 같은 주소(INFERENCE_BASE_URL)면 된다.
 *
 * @param classifyBaseUrl /classify를 보낼 주소
 * @param embedBaseUrl /embed를 보낼 주소
 * @param timeout 호출 한 번의 제한 시간(스펙: 10초)
 * @param maxAttempts 실패 시 포함한 총 시도 횟수. 재시도 횟수는 스펙에 없어 제안값이다.
 * @param retryBackoff 재시도 사이 대기. 시도 횟수만큼 곱해 늘린다.
 * @param classifyBatchSize /classify 한 번에 보낼 조항 수. 추론 서버는 조항 하나를 192토큰 창 최대 16개로 나누고
 *     요청 하나에 창 256개까지만 받으므로, 16개씩 보내면 긴 조항이 섞여도 한도를 넘지 않는다.
 * @param embedBatchSize /embed 한 번에 보낼 텍스트 수(서버 최대 64)
 */
@ConfigurationProperties(prefix = "contractcheck.inference")
public record InferenceProperties(
        @DefaultValue("http://localhost:8000") String classifyBaseUrl,
        @DefaultValue("http://localhost:8000") String embedBaseUrl,
        @DefaultValue("unset") String apiKey,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("200ms") Duration retryBackoff,
        @DefaultValue("16") int classifyBatchSize,
        @DefaultValue("64") int embedBatchSize) {

    /** 잘못된 값이면 호출할 때가 아니라 시작할 때 실패한다(배치 0이면 분할이 끝나지 않고, 시도 0이면 한 번도 부르지 않는다). */
    public InferenceProperties {
        if (classifyBaseUrl == null || classifyBaseUrl.isBlank()) {
            throw new IllegalArgumentException("contractcheck.inference.classify-base-url은 비어 있을 수 없습니다.");
        }
        if (embedBaseUrl == null || embedBaseUrl.isBlank()) {
            throw new IllegalArgumentException("contractcheck.inference.embed-base-url은 비어 있을 수 없습니다.");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("contractcheck.inference.timeout은 0보다 커야 합니다.");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("contractcheck.inference.max-attempts는 1 이상이어야 합니다.");
        }
        if (retryBackoff.isNegative()) {
            throw new IllegalArgumentException("contractcheck.inference.retry-backoff는 0 이상이어야 합니다.");
        }
        if (classifyBatchSize < 1 || classifyBatchSize > 64) {
            throw new IllegalArgumentException("contractcheck.inference.classify-batch-size는 1~64여야 합니다.");
        }
        if (embedBatchSize < 1 || embedBatchSize > 64) {
            throw new IllegalArgumentException("contractcheck.inference.embed-batch-size는 1~64여야 합니다.");
        }
    }
}
