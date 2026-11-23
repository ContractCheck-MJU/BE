package ac.mju.contractcheck.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ac.mju.contractcheck.client.InferenceMessages.ClassifyItem;
import ac.mju.contractcheck.client.InferenceMessages.ClassifyResponse;
import ac.mju.contractcheck.client.InferenceMessages.EmbedResponse;
import ac.mju.contractcheck.domain.ClauseClassification;
import ac.mju.contractcheck.domain.ClauseText;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 응답 검증·변환 규칙만 HTTP 없이 확인한다. 추론 서버가 잘못된 값을 줘도 잘못된 판정이 저장되지 않아야 한다. */
class InferenceResponseMapperTest {

    private static final List<ClauseText> ONE = List.of(new ClauseText("1", "조항"));

    @Test
    @DisplayName("응답 순서가 요청과 달라도 id로 짝을 지어 입력 순서대로 돌려준다")
    void matchesByIdNotByPosition() {
        ClassifyResponse response = new ClassifyResponse(List.of(
                item("b", false, null, Map.of("정상", 0.9)),
                item("a", true, "면책", Map.of("면책", 0.95))));

        List<ClauseClassification> results = InferenceResponseMapper.toClassifications(
                List.of(new ClauseText("a", "가"), new ClauseText("b", "나")), response);

        assertThat(results).extracting(ClauseClassification::clauseId).containsExactly("a", "b");
        assertThat(results.get(0).classification().probability()).isEqualTo(0.95);
        assertThat(results.get(1).classification().risky()).isFalse();
    }

    @Test
    @DisplayName("같은 id가 두 번 나오면 거절한다(한 결과가 다른 조항에 붙는 것을 막는다)")
    void rejectsDuplicateIds() {
        ClassifyResponse response = new ClassifyResponse(List.of(
                item("1", false, null, Map.of()), item("1", false, null, Map.of())));

        assertThatThrownBy(() -> InferenceResponseMapper.toClassifications(
                List.of(new ClauseText("1", "가"), new ClauseText("2", "나")), response))
                .isInstanceOf(InferenceException.class).hasMessageContaining("중복");
    }

    @Test
    @DisplayName("위험 확률이 0~1을 벗어나거나 NaN이면 거절한다")
    void rejectsOutOfRangeProbability() {
        for (double bad : new double[] {1.2, -0.1, Double.NaN}) {
            ClassifyResponse response = new ClassifyResponse(List.of(item("1", true, "면책", Map.of("면책", bad))));

            assertThatThrownBy(() -> InferenceResponseMapper.toClassifications(ONE, response))
                    .as("확률 %s", bad).isInstanceOf(InferenceException.class).hasMessageContaining("확률");
        }
    }

    @Test
    @DisplayName("위험인데 유형이 없으면 거절하고, 위험이 아니면 확률 없이도 받는다")
    void riskTypeIsRequiredOnlyForRiskyClauses() {
        assertThatThrownBy(() -> InferenceResponseMapper.toClassifications(
                ONE, new ClassifyResponse(List.of(item("1", true, null, Map.of("면책", 0.9))))))
                .isInstanceOf(InferenceException.class);

        assertThat(InferenceResponseMapper.toClassifications(
                ONE, new ClassifyResponse(List.of(item("1", false, null, null)))).get(0).riskType()).isEmpty();
    }

    @Test
    @DisplayName("임베딩은 개수·1024차원·유한 값이어야 한다")
    void validatesVectors() {
        float[] valid = new float[InferenceClient.EMBEDDING_DIMENSION];
        float[] nan = new float[InferenceClient.EMBEDDING_DIMENSION];
        nan[5] = Float.NaN;

        assertThat(InferenceResponseMapper.toVectors(List.of("가"), new EmbedResponse(List.of(valid)))).containsExactly(valid);
        assertThatThrownBy(() -> InferenceResponseMapper.toVectors(List.of("가"), new EmbedResponse(List.of(nan))))
                .isInstanceOf(InferenceException.class).hasMessageContaining("유한");
        assertThatThrownBy(() -> InferenceResponseMapper.toVectors(List.of("가"), new EmbedResponse(List.of(new float[3]))))
                .isInstanceOf(InferenceException.class).hasMessageContaining("차원");
        assertThatThrownBy(() -> InferenceResponseMapper.toVectors(List.of("가", "나"), new EmbedResponse(List.of(valid))))
                .isInstanceOf(InferenceException.class).hasMessageContaining("개수");
    }

    private static ClassifyItem item(String id, boolean risky, String riskType, Map<String, Double> probs) {
        return new ClassifyItem(id, risky, riskType, probs, "m");
    }
}
