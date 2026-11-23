package ac.mju.contractcheck.client;

import ac.mju.contractcheck.config.InferenceProperties;
import ac.mju.contractcheck.domain.Classification;
import ac.mju.contractcheck.domain.ClauseClassification;
import ac.mju.contractcheck.domain.ClauseText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 추론 서버를 HTTP로 부르는 구현체. 스펙시트 'API 명세'의 POST /classify, POST /embed 계약을 따른다.
 *
 * <ul>
 *   <li>모든 요청에 {@code X-Internal-Api-Key}를 붙이고, 제한 시간은 {@link InferenceProperties#timeout()}이다.</li>
 *   <li>입력은 batchSize(기본 64)씩 나눠 보낸다. 조항 하나씩 보내는 루프를 돌리지 않는다.</li>
 *   <li>연결 실패·시간 초과·5xx는 maxAttempts까지 재시도한다. 4xx(키 오류·검증 실패)는 재시도해도 같아 바로 실패한다.</li>
 *   <li>응답의 개수·id·차원·값을 검증해 어긋나면 {@link InferenceException}이다.</li>
 * </ul>
 *
 * 로그에는 개수와 상태 코드만 남기고 조항 본문은 남기지 않는다.
 * {@code contractcheck.inference.provider}가 http(기본)일 때만 빈으로 등록된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "contractcheck.inference", name = "provider", havingValue = "http", matchIfMissing = true)
public class HttpInferenceClient implements InferenceClient {

    static final int EMBEDDING_DIMENSION = 1024;
    private static final String KEY_HEADER = "X-Internal-Api-Key";

    @NonNull
    private final InferenceProperties properties;
    @NonNull
    private final RestClient inferenceRestClient;

    @Override
    public List<ClauseClassification> classify(List<ClauseText> clauses) {
        List<ClauseClassification> results = new ArrayList<>(clauses.size());
        for (List<ClauseText> batch : partition(clauses)) {
            results.addAll(classifyBatch(batch));
        }
        return results;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (List<String> batch : partition(texts)) {
            vectors.addAll(embedBatch(batch));
        }
        return vectors;
    }

    private List<ClauseClassification> classifyBatch(List<ClauseText> batch) {
        ClassifyRequest request = new ClassifyRequest(batch.stream().map(c -> new ClauseIn(c.id(), c.text())).toList());
        ClassifyResponse response = post("/classify", request, ClassifyResponse.class);
        if (response.results() == null || response.results().size() != batch.size()) {
            throw new InferenceException("분류 응답의 결과 개수가 요청과 다릅니다.");
        }
        Map<String, ClassifyItem> byId = new HashMap<>();
        for (ClassifyItem item : response.results()) {
            if (item == null || item.id() == null || byId.put(item.id(), item) != null) {
                throw new InferenceException("분류 응답의 id가 비었거나 중복됩니다.");
            }
        }
        List<ClauseClassification> results = new ArrayList<>(batch.size());
        for (ClauseText clause : batch) {
            ClassifyItem item = byId.get(clause.id());
            if (item == null) {
                throw new InferenceException("분류 응답에 요청한 조항이 없습니다.");
            }
            results.add(toClassification(item));
        }
        return results;
    }

    /** 위험이면 해당 유형의 확률(probs[riskType])을 쓴다. 위험이 아니면 0이다(probs의 의미는 스펙 미정이라 쓰지 않는다). */
    private static ClauseClassification toClassification(ClassifyItem item) {
        if (!item.isRisky()) {
            return new ClauseClassification(item.id(), new Classification(false, 0.0), "", valueOrEmpty(item.modelVersion()));
        }
        Double probability = item.riskType() == null || item.probs() == null ? null : item.probs().get(item.riskType());
        if (probability == null || probability.isNaN() || probability < 0.0 || probability > 1.0) {
            throw new InferenceException("위험 조항의 확률 값이 없거나 범위를 벗어났습니다.");
        }
        return new ClauseClassification(
                item.id(), new Classification(true, probability), item.riskType(), valueOrEmpty(item.modelVersion()));
    }

    private List<float[]> embedBatch(List<String> batch) {
        EmbedResponse response = post("/embed", new EmbedRequest(batch, true), EmbedResponse.class);
        if (response.vectors() == null || response.vectors().size() != batch.size()) {
            throw new InferenceException("임베딩 응답의 벡터 개수가 요청과 다릅니다.");
        }
        List<float[]> vectors = new ArrayList<>(batch.size());
        for (float[] vector : response.vectors()) {
            if (vector == null || vector.length != EMBEDDING_DIMENSION) {
                throw new InferenceException("임베딩 차원이 " + EMBEDDING_DIMENSION + "이 아닙니다.");
            }
            for (float value : vector) {
                if (!Float.isFinite(value)) {
                    throw new InferenceException("임베딩에 유한하지 않은 값이 있습니다.");
                }
            }
            vectors.add(vector);
        }
        return vectors;
    }

    private <T> T post(String path, Object body, Class<T> type) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                T response = inferenceRestClient.post()
                        .uri(path)
                        .header(KEY_HEADER, properties.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(type);
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
                pause(attempt);
            }
        }
        throw new InferenceException("추론 서버를 호출하지 못했습니다(재시도 소진).", last);
    }

    private void pause(int attempt) {
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

    private <T> List<List<T>> partition(List<T> items) {
        List<List<T>> batches = new ArrayList<>();
        int size = properties.batchSize();
        for (int from = 0; from < items.size(); from += size) {
            batches.add(items.subList(from, Math.min(items.size(), from + size)));
        }
        return batches;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    // 요청·응답 형식은 스펙시트 'API 명세'(POST /classify, POST /embed)다. 이 파일 안에서만 쓴다.
    private record ClauseIn(String id, String text) {}

    private record ClassifyRequest(List<ClauseIn> clauses) {}

    private record ClassifyItem(String id, boolean isRisky, String riskType, Map<String, Double> probs, String modelVersion) {}

    private record ClassifyResponse(List<ClassifyItem> results) {}

    private record EmbedRequest(List<String> texts, boolean normalize) {}

    private record EmbedResponse(List<float[]> vectors) {}
}
