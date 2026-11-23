package ac.mju.contractcheck.client;

import ac.mju.contractcheck.config.InferenceProperties;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 추론 서버로 가는 HTTP 전송 한 가지만 맡는다: 인증 헤더, 재시도, 오류 변환.
 * 연결 실패·시간 초과·5xx는 maxAttempts까지 재시도하고(시도마다 대기를 늘린다), 4xx(키 오류·검증 실패)는
 * 다시 보내도 같아 바로 실패한다. 로그에는 경로·시도 횟수·상태 코드만 남기고 요청·응답 본문은 남기지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
final class InferenceTransport {

    private static final String KEY_HEADER = "X-Internal-Api-Key";

    @NonNull
    private final InferenceProperties properties;
    @NonNull
    private final RestClient restClient;

    <T> T post(String path, Object body, Class<T> responseType) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                T response = restClient.post()
                        .uri(path)
                        .header(KEY_HEADER, properties.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(responseType);
                if (response == null) {
                    throw new InferenceException("추론 서버가 빈 응답을 돌려줬습니다.");
                }
                return response;
            } catch (HttpClientErrorException exception) {
                log.warn("추론 서버가 요청을 거절했습니다 path={} status={}", path, exception.getStatusCode().value());
                throw new InferenceException("추론 서버가 요청을 거절했습니다. status=" + exception.getStatusCode().value(), exception);
            } catch (HttpServerErrorException | ResourceAccessException exception) {
                last = exception;
                log.warn("추론 서버 호출 실패 path={} attempt={}/{} type={}",
                        path, attempt, properties.maxAttempts(), exception.getClass().getSimpleName());
                backOff(attempt);
            }
        }
        throw new InferenceException("추론 서버를 호출하지 못했습니다(재시도 소진).", last);
    }

    private void backOff(int attempt) {
        long millis = properties.retryBackoff().toMillis() * attempt;
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InferenceException("추론 서버 호출이 중단되었습니다.", exception);
        }
    }
}
