package ac.mju.contractcheck.client;

import ac.mju.contractcheck.client.InferenceMessages.ClassifyRequest;
import ac.mju.contractcheck.client.InferenceMessages.ClassifyResponse;
import ac.mju.contractcheck.client.InferenceMessages.ClauseIn;
import ac.mju.contractcheck.client.InferenceMessages.EmbedRequest;
import ac.mju.contractcheck.client.InferenceMessages.EmbedResponse;
import ac.mju.contractcheck.config.InferenceProperties;
import ac.mju.contractcheck.domain.ClauseClassification;
import ac.mju.contractcheck.domain.ClauseText;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 추론 서버를 HTTP로 부르는 구현체. 스펙시트 'API 명세'의 POST /classify, POST /embed 계약을 따른다.
 * 이 클래스는 입력을 batchSize(기본 64)씩 나눠 보내고 결과를 이어 붙이는 일만 한다. 조항을 하나씩 보내는 루프는 없다.
 * 전송·재시도는 {@link InferenceTransport}, 응답 검증·변환은 {@link InferenceResponseMapper}가 맡는다.
 *
 * <p>{@code contractcheck.inference.provider}가 http(기본)일 때만 빈으로 등록된다.
 */
@Component
@ConditionalOnProperty(prefix = "contractcheck.inference", name = "provider", havingValue = "http", matchIfMissing = true)
public class HttpInferenceClient implements InferenceClient {

    private final int batchSize;
    private final InferenceTransport transport;

    public HttpInferenceClient(@NonNull InferenceProperties properties, @NonNull RestClient inferenceRestClient) {
        this.batchSize = properties.batchSize();
        this.transport = new InferenceTransport(properties, inferenceRestClient);
    }

    @Override
    public List<ClauseClassification> classify(List<ClauseText> clauses) {
        List<ClauseClassification> results = new ArrayList<>(clauses.size());
        for (List<ClauseText> batch : partition(clauses)) {
            ClassifyRequest request = new ClassifyRequest(batch.stream().map(c -> new ClauseIn(c.id(), c.text())).toList());
            ClassifyResponse response = transport.post("/classify", request, ClassifyResponse.class);
            results.addAll(InferenceResponseMapper.toClassifications(batch, response));
        }
        return results;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (List<String> batch : partition(texts)) {
            EmbedResponse response = transport.post("/embed", new EmbedRequest(batch, true), EmbedResponse.class);
            vectors.addAll(InferenceResponseMapper.toVectors(batch, response));
        }
        return vectors;
    }

    private <T> List<List<T>> partition(List<T> items) {
        List<List<T>> batches = new ArrayList<>();
        for (int from = 0; from < items.size(); from += batchSize) {
            batches.add(items.subList(from, Math.min(items.size(), from + batchSize)));
        }
        return batches;
    }
}
