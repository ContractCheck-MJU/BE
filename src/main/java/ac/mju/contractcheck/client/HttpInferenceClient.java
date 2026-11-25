package ac.mju.contractcheck.client;

import ac.mju.contractcheck.client.InferenceMessages.ClassifyRequest;
import ac.mju.contractcheck.client.InferenceMessages.ClassifyResponse;
import ac.mju.contractcheck.client.InferenceMessages.ClauseIn;
import ac.mju.contractcheck.client.InferenceMessages.EmbedRequest;
import ac.mju.contractcheck.client.InferenceMessages.EmbedResponse;
import ac.mju.contractcheck.config.InferenceConfig;
import ac.mju.contractcheck.config.InferenceProperties;
import ac.mju.contractcheck.domain.ClauseClassification;
import ac.mju.contractcheck.domain.ClauseText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 추론 서버를 HTTP로 부르는 구현체. 스펙시트 'API 명세'의 POST /classify, POST /embed 계약을 따른다.
 * 분류와 임베딩은 서로 다른 주소(contractcheck.inference.classify-base-url, embed-base-url)로 보낸다.
 * 이 클래스는 입력을 배치 크기(분류 16, 임베딩 64)씩 나눠 보내고 결과를 이어 붙이는 일만 한다. 조항을 하나씩 보내는 루프는 없다.
 * 전송·재시도는 {@link InferenceTransport}, 응답 검증·변환은 {@link InferenceResponseMapper}가 맡는다.
 *
 * <p>/classify에는 {@value #MODE_HEADER}: {@value #RESEARCH_MODE} 헤더를 붙인다. 추론 서버의 분류 모델은 약라벨로 학습한
 * 연구·시연용이라 이 헤더가 없으면 403으로 거절한다. 팀 결정에 따라 이진 분류 결과(위험 여부)를 그대로 쓴다.
 *
 * <p>{@code contractcheck.inference.provider}가 http(기본)일 때만 빈으로 등록된다.
 */
@Component
@ConditionalOnProperty(prefix = "contractcheck.inference", name = "provider", havingValue = "http", matchIfMissing = true)
public class HttpInferenceClient implements InferenceClient {

    static final String MODE_HEADER = "X-ContractCheck-Mode";
    static final String RESEARCH_MODE = "research-only";

    private final int classifyBatchSize;
    private final int embedBatchSize;
    private final InferenceTransport classifyTransport;
    private final InferenceTransport embedTransport;

    public HttpInferenceClient(
            @NonNull InferenceProperties properties,
            @NonNull @Qualifier(InferenceConfig.CLASSIFY_REST_CLIENT) RestClient classifyRestClient,
            @NonNull @Qualifier(InferenceConfig.EMBED_REST_CLIENT) RestClient embedRestClient) {
        this.classifyBatchSize = properties.classifyBatchSize();
        this.embedBatchSize = properties.embedBatchSize();
        this.classifyTransport = new InferenceTransport(properties, classifyRestClient, Map.of(MODE_HEADER, RESEARCH_MODE));
        this.embedTransport = new InferenceTransport(properties, embedRestClient, Map.of());
    }

    @Override
    public List<ClauseClassification> classify(List<ClauseText> clauses) {
        List<ClauseClassification> results = new ArrayList<>(clauses.size());
        for (List<ClauseText> batch : partition(clauses, classifyBatchSize)) {
            ClassifyRequest request = new ClassifyRequest(batch.stream().map(c -> new ClauseIn(c.id(), c.text())).toList());
            ClassifyResponse response = classifyTransport.post("/classify", request, ClassifyResponse.class);
            results.addAll(InferenceResponseMapper.toClassifications(batch, response));
        }
        return results;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (List<String> batch : partition(texts, embedBatchSize)) {
            EmbedResponse response = embedTransport.post("/embed", new EmbedRequest(batch, true), EmbedResponse.class);
            vectors.addAll(InferenceResponseMapper.toVectors(batch, response));
        }
        return vectors;
    }

    private static <T> List<List<T>> partition(List<T> items, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int from = 0; from < items.size(); from += batchSize) {
            batches.add(items.subList(from, Math.min(items.size(), from + batchSize)));
        }
        return batches;
    }
}
