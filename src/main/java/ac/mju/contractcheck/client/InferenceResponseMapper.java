package ac.mju.contractcheck.client;

import ac.mju.contractcheck.client.InferenceMessages.ClassifyItem;
import ac.mju.contractcheck.client.InferenceMessages.ClassifyResponse;
import ac.mju.contractcheck.client.InferenceMessages.EmbedResponse;
import ac.mju.contractcheck.domain.Classification;
import ac.mju.contractcheck.domain.ClauseClassification;
import ac.mju.contractcheck.domain.ClauseText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 추론 서버 응답을 검증해 도메인 값으로 바꾼다. HTTP를 모르는 순수 변환이라 서버 없이 테스트한다.
 * 어긋난 응답은 {@link InferenceException}으로 거절해, 잘못된 판정이 저장되는 일을 막는다.
 */
final class InferenceResponseMapper {

    private InferenceResponseMapper() {}

    /** 결과를 id로 요청 조항과 짝지어 요청 순서대로 돌려준다. 서버가 순서를 바꿔도 짝이 어긋나지 않는다. */
    static List<ClauseClassification> toClassifications(List<ClauseText> batch, ClassifyResponse response) {
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

    /** 개수·1024차원·유한 값을 확인한다. 임베딩은 id가 없어 요청 순서를 그대로 믿는다(스펙 한계). */
    static List<float[]> toVectors(List<String> batch, EmbedResponse response) {
        if (response.vectors() == null || response.vectors().size() != batch.size()) {
            throw new InferenceException("임베딩 응답의 벡터 개수가 요청과 다릅니다.");
        }
        for (float[] vector : response.vectors()) {
            if (vector == null || vector.length != InferenceClient.EMBEDDING_DIMENSION) {
                throw new InferenceException("임베딩 차원이 " + InferenceClient.EMBEDDING_DIMENSION + "이 아닙니다.");
            }
            for (float value : vector) {
                if (!Float.isFinite(value)) {
                    throw new InferenceException("임베딩에 유한하지 않은 값이 있습니다.");
                }
            }
        }
        return response.vectors();
    }

    /** 위험이면 해당 유형의 확률(probs[riskType])을 쓴다. 위험이 아니면 0이다(probs의 의미는 스펙 미정이라 쓰지 않는다). */
    private static ClauseClassification toClassification(ClassifyItem item) {
        String modelVersion = item.modelVersion() == null ? "" : item.modelVersion();
        if (!item.isRisky()) {
            return new ClauseClassification(item.id(), new Classification(false, 0.0), "", modelVersion);
        }
        Double probability = item.riskType() == null || item.probs() == null ? null : item.probs().get(item.riskType());
        if (probability == null || probability.isNaN() || probability < 0.0 || probability > 1.0) {
            throw new InferenceException("위험 조항의 확률 값이 없거나 범위를 벗어났습니다.");
        }
        return new ClauseClassification(item.id(), new Classification(true, probability), item.riskType(), modelVersion);
    }
}
