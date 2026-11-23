package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.client.InferenceClient;
import ac.mju.contractcheck.client.InferenceException;
import ac.mju.contractcheck.domain.Classification;
import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.ClauseClassification;
import ac.mju.contractcheck.domain.ClauseRisk;
import ac.mju.contractcheck.domain.ClauseText;
import ac.mju.contractcheck.domain.EvidenceHit;
import ac.mju.contractcheck.domain.RiskLevel;
import ac.mju.contractcheck.domain.RiskLevelResolver;
import ac.mju.contractcheck.domain.RiskPolicy;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

/** 추론 서버·임베딩·근거 검색은 모두 mock이다. 등급 규칙은 RiskLevelResolver(임계값 0.6, 0.5)를 그대로 쓴다. */
class ClauseRiskServiceTest {

    private final InferenceClient inference = mock(InferenceClient.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final EvidenceRetriever evidenceRetriever = mock(EvidenceRetriever.class);
    private ClauseRiskService service;

    @BeforeEach
    void setUp() {
        service = new ClauseRiskService(inference, embeddingModel, evidenceRetriever,
                new RiskLevelResolver(new RiskPolicy(0.6, 0.5)));
    }

    @Test
    @DisplayName("TC-ANL-001: 위험 확률이 높고 근거가 있으면 HIGH이고 유형·근거·임베딩을 담는다")
    void riskyWithEvidenceIsHigh() {
        Clause clause = clause(1, "임차인은 계약갱신요구권을 행사하지 않는다.");
        float[] embedding = {0.1f};
        stub(List.of(classified("1", true, "임차인 권리 제한", 0.93)), List.of(embedding));
        when(evidenceRetriever.search(embedding, EvidenceRetriever.DEFAULT_LIMIT)).thenReturn(List.of(hit(0.62), hit(0.4)));

        ClauseRisk risk = service.assess(List.of(clause)).get(0);

        assertThat(risk.assessment().level()).isEqualTo(RiskLevel.HIGH);
        assertThat(risk.riskType()).isEqualTo("임차인 권리 제한");
        assertThat(risk.probability()).isEqualTo(0.93);
        assertThat(risk.modelVersion()).isEqualTo("mock-0.1");
        assertThat(risk.embedding()).isSameAs(embedding);
        assertThat(risk.evidence()).hasSize(2);
    }

    @Test
    @DisplayName("TC-ANL-017: 위험 확률이 0.6 미만이면 HIGH로 확정하지 않고 CAUTION + lowConfidence다")
    void lowConfidenceIsCaution() {
        float[] embedding = {0.1f};
        stub(List.of(classified("1", true, "비용 전가", 0.52)), List.of(embedding));
        when(evidenceRetriever.search(embedding, EvidenceRetriever.DEFAULT_LIMIT)).thenReturn(List.of(hit(0.7)));

        ClauseRisk risk = service.assess(List.of(clause(1, "소모품 교체는 임차인이 부담한다."))).get(0);

        assertThat(risk.assessment().level()).isEqualTo(RiskLevel.CAUTION);
        assertThat(risk.assessment().lowConfidence()).isTrue();
    }

    @Test
    @DisplayName("TC-ANL-018: 근거가 모두 임계값(0.5) 미만이면 CAUTION + 근거 부족으로 하향한다")
    void noQualifiedEvidenceDowngradesToCaution() {
        float[] embedding = {0.1f};
        stub(List.of(classified("1", true, "면책", 0.9)), List.of(embedding));
        when(evidenceRetriever.search(embedding, EvidenceRetriever.DEFAULT_LIMIT)).thenReturn(List.of(hit(0.49), hit(0.3)));

        ClauseRisk risk = service.assess(List.of(clause(1, "임대인은 책임을 지지 않는다."))).get(0);

        assertThat(risk.assessment().level()).isEqualTo(RiskLevel.CAUTION);
        assertThat(risk.assessment().evidenceInsufficient()).isTrue();
        assertThat(risk.assessment().lowConfidence()).isFalse();
    }

    @Test
    @DisplayName("TC-ANL-013: 위험이 아닌 조항은 SAFE이고 근거 검색을 하지 않는다")
    void safeClauseSkipsEvidenceSearch() {
        stub(List.of(classified("1", false, "", 0.0)), List.of(new float[] {0.1f}));

        ClauseRisk risk = service.assess(List.of(clause(1, "임대인은 주택을 사용·수익에 필요한 상태로 유지한다."))).get(0);

        assertThat(risk.assessment().level()).isEqualTo(RiskLevel.SAFE);
        assertThat(risk.evidence()).isEmpty();
        verify(evidenceRetriever, never()).search(any(), anyInt());
    }

    @Test
    @DisplayName("여러 조항도 분류·임베딩은 한 번씩(배치)만 부르고, 위험 조항에만 검색하며 순서를 지킨다")
    void batchesCallsAndKeepsOrder() {
        float[] safeVec = {1f};
        float[] riskyVec = {2f};
        stub(List.of(classified("10", false, "", 0.0), classified("20", true, "계약해지", 0.92)), List.of(safeVec, riskyVec));
        when(evidenceRetriever.search(riskyVec, EvidenceRetriever.DEFAULT_LIMIT)).thenReturn(List.of(hit(0.7)));

        List<ClauseRisk> risks = service.assess(List.of(clause(10, "정상 조항"), clause(20, "즉시 해지")));

        assertThat(risks).extracting(ClauseRisk::clauseId).containsExactly(10L, 20L);
        assertThat(risks).extracting(r -> r.assessment().level()).containsExactly(RiskLevel.SAFE, RiskLevel.HIGH);
        verify(inference, times(1)).classify(any());
        verify(embeddingModel, times(1)).embed(any(List.class));
        verify(evidenceRetriever, times(1)).search(any(), anyInt());
    }

    @Test
    @DisplayName("조항이 없으면 외부를 아무것도 부르지 않는다")
    void emptyInputCallsNothing() {
        assertThat(service.assess(List.of())).isEmpty();

        verifyNoInteractions(inference, embeddingModel, evidenceRetriever);
    }

    @Test
    @DisplayName("TC-ANL-016: 분류 호출이 실패하면 InferenceException을 올리고 임베딩·검색은 하지 않는다")
    void classifyFailurePropagates() {
        when(inference.classify(any())).thenThrow(new InferenceException("down"));

        assertThatThrownBy(() -> service.assess(List.of(clause(1, "조항")))).isInstanceOf(InferenceException.class);

        verifyNoInteractions(embeddingModel, evidenceRetriever);
    }

    @Test
    @DisplayName("임베딩 호출이 실패하면 InferenceException을 올리고 검색은 하지 않는다")
    void embedFailurePropagates() {
        when(inference.classify(any())).thenReturn(List.of(classified("1", true, "면책", 0.9)));
        when(embeddingModel.embed(any(List.class))).thenThrow(new InferenceException("down"));

        assertThatThrownBy(() -> service.assess(List.of(clause(1, "조항")))).isInstanceOf(InferenceException.class);

        verifyNoInteractions(evidenceRetriever);
    }

    @Test
    @DisplayName("분류 결과 개수가 조항 수와 다르면 인덱스 오류가 아니라 InferenceException이다")
    void classificationCountMismatchIsRejected() {
        when(inference.classify(any())).thenReturn(List.of(classified("1", false, "", 0.0)));

        assertThatThrownBy(() -> service.assess(List.of(clause(1, "가"), clause(2, "나"))))
                .isInstanceOf(InferenceException.class).hasMessageContaining("분류");
        verifyNoInteractions(embeddingModel, evidenceRetriever);
    }

    @Test
    @DisplayName("임베딩 개수가 조항 수와 다르면 InferenceException이고 근거 검색은 하지 않는다")
    void embeddingCountMismatchIsRejected() {
        when(inference.classify(any())).thenReturn(List.of(classified("1", true, "면책", 0.9), classified("2", true, "면책", 0.9)));
        when(embeddingModel.embed(any(List.class))).thenReturn(List.of(new float[] {1f}));

        assertThatThrownBy(() -> service.assess(List.of(clause(1, "가"), clause(2, "나"))))
                .isInstanceOf(InferenceException.class).hasMessageContaining("임베딩");
        verifyNoInteractions(evidenceRetriever);
    }

    @Test
    @DisplayName("추론 서버에는 조항 id를 문자열 키로, 본문을 그대로 보낸다")
    void sendsClauseIdAndText() {
        stub(List.of(classified("5", false, "", 0.0)), List.of(new float[] {1f}));

        service.assess(List.of(clause(5, "본문")));

        verify(inference).classify(List.of(new ClauseText("5", "본문")));
    }

    private void stub(List<ClauseClassification> classifications, List<float[]> embeddings) {
        when(inference.classify(any())).thenReturn(classifications);
        when(embeddingModel.embed(any(List.class))).thenReturn(embeddings);
    }

    private static Clause clause(long id, String text) {
        return new Clause(id, 1L, "1", (int) id, "제" + id + "조", text, "");
    }

    private static ClauseClassification classified(String id, boolean risky, String riskType, double probability) {
        return new ClauseClassification(id, new Classification(risky, probability), riskType, "mock-0.1");
    }

    private static EvidenceHit hit(double similarity) {
        return new EvidenceHit(1, 1, "LAW", "민법 제640조", "제640조", "본문", similarity);
    }
}
