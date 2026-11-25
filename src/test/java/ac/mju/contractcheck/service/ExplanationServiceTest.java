package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.client.LlmClient;
import ac.mju.contractcheck.client.LlmTimeoutException;
import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.EvidenceHit;
import ac.mju.contractcheck.domain.Explanation;
import ac.mju.contractcheck.domain.ExplanationCitation;
import ac.mju.contractcheck.domain.ExplanationContext;
import ac.mju.contractcheck.domain.ExplanationDraft;
import ac.mju.contractcheck.domain.RiskPolicy;
import ac.mju.contractcheck.domain.RiskResult;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.domain.enums.AnalysisStatus;
import ac.mju.contractcheck.domain.enums.RiskLevel;
import ac.mju.contractcheck.global.error.ApiException;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.repository.AnalysisRepository;
import ac.mju.contractcheck.repository.ClauseRepository;
import ac.mju.contractcheck.repository.ExplanationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** FR-EXP-01·02·03 설명 생성·조회: 검증 순서, 분석 완료 요구, 재생성 제한, 마스킹·근거, Claude 실패 처리. Claude는 mock한다. */
class ExplanationServiceTest {

    private static final UserId OWNER = new UserId(7);
    private static final long CLAUSE = 31L;
    private static final long CONTRACT = 10L;
    private static final long ANALYSIS = 100L;
    private static final String ORIGINAL = "임차인 홍길동은 퇴거 시 보증금 반환을 청구할 수 없다.";
    private static final String MASKED = "임차인 [이름]은 퇴거 시 보증금 반환을 청구할 수 없다.";

    private final ExplanationRepository explanations = mock(ExplanationRepository.class);
    private final ClauseRepository clauses = mock(ClauseRepository.class);
    private final AnalysisRepository analyses = mock(AnalysisRepository.class);
    private final PiiMaskingService masking = mock(PiiMaskingService.class);
    private final EvidenceRetriever evidence = mock(EvidenceRetriever.class);
    private final LlmClient llm = mock(LlmClient.class);
    private final ExplanationService service = new ExplanationService(
            explanations, clauses, analyses, masking, evidence, llm, new RiskPolicy(0.6, 0.5),
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    @Test
    @DisplayName("FR-EXP-01 지원하지 않는 tone이면 400 VALIDATION_ERROR이고 조항도 조회하지 않는다")
    void rejectsUnsupportedTone() {
        assertThatThrownBy(() -> service.create(OWNER, CLAUSE, "아주 쉽게"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(400);
                    assertThat(exception.code()).isEqualTo("VALIDATION_ERROR");
                });
        verifyNoInteractions(clauses, llm);
    }

    @Test
    @DisplayName("FR-EXP-01 내 조항이 아니거나 없으면 404 CLAUSE_NOT_FOUND이고 Claude를 부르지 않는다")
    void rejectsClauseNotOwned() {
        when(clauses.findOwned(CLAUSE, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(OWNER, CLAUSE, null))
                .isInstanceOfSatisfying(NotFoundException.class,
                        exception -> assertThat(exception.code()).isEqualTo("CLAUSE_NOT_FOUND"));
        verifyNoInteractions(llm);
    }

    @Test
    @DisplayName("TC-EXP-008 계약서 분석을 마친 적이 없으면 409 ANALYSIS_REQUIRED이고 Claude를 부르지 않는다")
    void requiresAnalyzedContract() {
        when(clauses.findOwned(CLAUSE, OWNER)).thenReturn(Optional.of(clause()));
        when(analyses.findLatestAnalyzed(CONTRACT, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(OWNER, CLAUSE, "기본"))
                .isInstanceOfSatisfying(ConflictException.class,
                        exception -> assertThat(exception.code()).isEqualTo("ANALYSIS_REQUIRED"));
        verifyNoInteractions(llm);
    }

    @Test
    @DisplayName("TC-EXP-010 같은 조항은 최초 1회와 재생성 3회까지 만들고 그 뒤는 429 REGENERATE_LIMIT다")
    void limitsRegeneration() {
        analyzedClause();
        when(explanations.countByClause(CLAUSE)).thenReturn(4L);

        assertThatThrownBy(() -> service.create(OWNER, CLAUSE, null))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(429);
                    assertThat(exception.code()).isEqualTo("REGENERATE_LIMIT");
                });
        verifyNoInteractions(llm);
        verify(explanations, never()).save(any());
    }

    @Test
    @DisplayName("TC-EXP-010 재생성 3회째(이미 3개)까지는 만든다")
    void allowsThirdRegeneration() {
        analyzedClause();
        when(explanations.countByClause(CLAUSE)).thenReturn(3L);
        stubLlmAndSave();

        assertThat(service.create(OWNER, CLAUSE, null).id()).isEqualTo(900);
    }

    @Test
    @DisplayName("FR-EXP-01·02·03 마스킹한 본문·위험 등급·임계값 이상 근거로 Claude를 부르고 설명·수정 문구·문서 단위 인용을 저장한다")
    void createsExplanationWithCitations() {
        analyzedClause();
        when(analyses.findResults(ANALYSIS)).thenReturn(List.of(
                new RiskResult(CLAUSE, true, RiskLevel.HIGH, "RISK_CANDIDATE", 0.9),
                new RiskResult(99L, false, RiskLevel.SAFE, "", 0.0)));
        when(evidence.searchByClause(CLAUSE, EvidenceRetriever.DEFAULT_LIMIT)).thenReturn(List.of(
                hit(1, 11, "LAW", 0.82),
                hit(1, 12, "LAW", 0.88),
                hit(2, 21, "PRECEDENT", 0.61),
                hit(3, 31, "LAW", 0.42)));
        stubLlmAndSave();

        Explanation created = service.create(OWNER, CLAUSE, "기본");

        ArgumentCaptor<ExplanationContext> context = ArgumentCaptor.forClass(ExplanationContext.class);
        verify(llm).explain(context.capture());
        assertThat(context.getValue().maskedClause()).isEqualTo(MASKED).doesNotContain("홍길동");
        assertThat(context.getValue().riskLevel()).isEqualTo("HIGH");
        assertThat(context.getValue().riskType()).isEqualTo("RISK_CANDIDATE");
        assertThat(context.getValue().evidence()).extracting(EvidenceHit::chunkId)
                .as("근거 임계값(0.5) 미만은 빼고 보낸다")
                .containsExactly(11L, 12L, 21L);

        ArgumentCaptor<Explanation> saved = ArgumentCaptor.forClass(Explanation.class);
        verify(explanations).save(saved.capture());
        assertThat(saved.getValue().clauseId()).isEqualTo(CLAUSE);
        assertThat(saved.getValue().text()).isEqualTo("보증금을 못 돌려받을 수 있습니다.");
        assertThat(saved.getValue().suggestion()).isEqualTo("임대인은 계약 종료 시 보증금을 반환한다.");
        assertThat(saved.getValue().llmModel()).isEqualTo("claude-sonnet-5-5");
        assertThat(saved.getValue().promptVersion()).isEqualTo("exp-v1");
        assertThat(saved.getValue().citations()).containsExactly(
                new ExplanationCitation("LAW", 1, 0.88),
                new ExplanationCitation("PRECEDENT", 2, 0.61));
        assertThat(created.id()).isEqualTo(900);
    }

    @Test
    @DisplayName("FR-EXP-02 분석 결과에 조항이 없고 근거도 없으면 빈 등급·빈 인용으로 만든다")
    void createsWithoutRiskOrEvidence() {
        analyzedClause();
        when(analyses.findResults(ANALYSIS)).thenReturn(List.of());
        when(evidence.searchByClause(anyLong(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        stubLlmAndSave();

        service.create(OWNER, CLAUSE, null);

        ArgumentCaptor<ExplanationContext> context = ArgumentCaptor.forClass(ExplanationContext.class);
        verify(llm).explain(context.capture());
        assertThat(context.getValue().riskLevel()).isEmpty();
        assertThat(context.getValue().evidence()).isEmpty();
        ArgumentCaptor<Explanation> saved = ArgumentCaptor.forClass(Explanation.class);
        verify(explanations).save(saved.capture());
        assertThat(saved.getValue().citations()).isEmpty();
    }

    @Test
    @DisplayName("TC-EXP-013 Claude가 제한 시간을 넘기면 504 LLM_TIMEOUT이고 설명을 저장하지 않는다")
    void timeoutDoesNotSave() {
        analyzedClause();
        when(llm.explain(any())).thenThrow(new LlmTimeoutException("시간 초과", null));

        assertThatThrownBy(() -> service.create(OWNER, CLAUSE, null))
                .isInstanceOfSatisfying(LlmTimeoutException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(504);
                    assertThat(exception.code()).isEqualTo("LLM_TIMEOUT");
                });
        verify(explanations, never()).save(any());
    }

    @Test
    @DisplayName("FR-EXP-02 남의 설명이나 없는 설명은 404 EXPLANATION_NOT_FOUND다")
    void getRejectsExplanationNotOwned() {
        when(explanations.findOwned(5L, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(OWNER, 5L))
                .isInstanceOfSatisfying(NotFoundException.class,
                        exception -> assertThat(exception.code()).isEqualTo("EXPLANATION_NOT_FOUND"));
    }

    private void analyzedClause() {
        when(clauses.findOwned(CLAUSE, OWNER)).thenReturn(Optional.of(clause()));
        when(analyses.findLatestAnalyzed(CONTRACT, OWNER)).thenReturn(Optional.of(new Analysis(
                ANALYSIS, CONTRACT, OWNER, AnalysisStatus.ANALYZED, 50, "요약", "m1", Instant.now())));
        when(masking.mask(ORIGINAL)).thenReturn(MASKED);
    }

    private void stubLlmAndSave() {
        when(llm.explain(any())).thenReturn(new ExplanationDraft(
                "보증금을 못 돌려받을 수 있습니다.", "임대인은 계약 종료 시 보증금을 반환한다.", "claude-sonnet-5-5", "exp-v1"));
        when(explanations.save(any())).thenAnswer(invocation -> {
            Explanation explanation = invocation.getArgument(0);
            return new Explanation(900, explanation.clauseId(), explanation.owner(), explanation.text(),
                    explanation.suggestion(), explanation.llmModel(), explanation.promptVersion(),
                    explanation.createdAt(), explanation.citations());
        });
    }

    private static Clause clause() {
        return new Clause(CLAUSE, CONTRACT, OWNER, 3, "제3조(보증금)", ORIGINAL, "");
    }

    private static EvidenceHit hit(long documentId, long chunkId, String docType, double similarity) {
        return new EvidenceHit(documentId, chunkId, docType, "주택임대차보호법", "제3조", "근거 본문", "", similarity);
    }
}
