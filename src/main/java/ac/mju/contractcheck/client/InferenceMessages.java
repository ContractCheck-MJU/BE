package ac.mju.contractcheck.client;

import java.util.List;
import java.util.Map;

/** 추론 서버와 주고받는 JSON 형식. 스펙시트 'API 명세'의 POST /classify, POST /embed다. client 패키지 안에서만 쓴다. */
final class InferenceMessages {

    private InferenceMessages() {}

    record ClauseIn(String id, String text) {}

    record ClassifyRequest(List<ClauseIn> clauses) {}

    record ClassifyItem(String id, boolean isRisky, String riskType, Map<String, Double> probs, String modelVersion) {}

    record ClassifyResponse(List<ClassifyItem> results) {}

    record EmbedRequest(List<String> texts, boolean normalize) {}

    record EmbedResponse(List<float[]> vectors) {}
}
