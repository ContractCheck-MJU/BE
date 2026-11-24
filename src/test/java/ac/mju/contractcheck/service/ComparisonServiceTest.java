package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.domain.AnalysisSummary;
import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.ClauseSimilarity;
import ac.mju.contractcheck.domain.ComparedClause;
import ac.mju.contractcheck.domain.Comparison;
import ac.mju.contractcheck.domain.ComparisonDetail;
import ac.mju.contractcheck.domain.ComparisonPair;
import ac.mju.contractcheck.domain.ComparisonPolicy;
import ac.mju.contractcheck.domain.RiskResult;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.domain.enums.ChangeType;
import ac.mju.contractcheck.domain.enums.RiskLevel;
import ac.mju.contractcheck.global.error.ApiException;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.repository.AnalysisRepository;
import ac.mju.contractcheck.repository.ClauseEmbeddingRepository;
import ac.mju.contractcheck.repository.ClauseRepository;
import ac.mju.contractcheck.repository.ComparisonRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** FR-CMP-01 비교 생성: 검증 순서, 분석 완료 요구, 짝 계산 결과 저장. FR-CMP-02 비교 조회: 조항·위험 등급 붙이기. */
class ComparisonServiceTest {

    private static final UserId OWNER = new UserId(7);
    private static final long BEFORE = 10L;
    private static final long AFTER = 20L;

    private final ComparisonRepository comparisons = mock(ComparisonRepository.class);
    private final ContractService contracts = mock(ContractService.class);
    private final ClauseRepository clauses = mock(ClauseRepository.class);
    private final AnalysisRepository analyses = mock(AnalysisRepository.class);
    private final ClauseEmbeddingRepository embeddings = mock(ClauseEmbeddingRepository.class);
    private final ComparisonService service = new ComparisonService(
            comparisons, contracts, clauses, analyses, embeddings, new ComparisonPolicy(0.6));

    @Test
    @DisplayName("FR-CMP-01 같은 계약서끼리 비교하면 400 SAME_CONTRACT이고 아무것도 조회·저장하지 않는다")
    void rejectsSameContract() {
        assertThatThrownBy(() -> service.create(OWNER, BEFORE, BEFORE))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(400);
                    assertThat(exception.code()).isEqualTo("SAME_CONTRACT");
                });
        verify(contracts, never()).get(any(), anyLong());
        verify(comparisons, never()).save(any());
    }

    @Test
    @DisplayName("FR-CMP-01 내 계약서가 아니거나 없으면 404 CONTRACT_NOT_FOUND이고 저장하지 않는다")
    void rejectsContractNotOwned() {
        when(contracts.get(OWNER, AFTER)).thenThrow(new NotFoundException("CONTRACT_NOT_FOUND", "계약서를 찾을 수 없습니다."));

        assertThatThrownBy(() -> service.create(OWNER, BEFORE, AFTER))
                .isInstanceOfSatisfying(NotFoundException.class,
                        exception -> assertThat(exception.code()).isEqualTo("CONTRACT_NOT_FOUND"));
        verify(comparisons, never()).save(any());
    }

    @Test
    @DisplayName("TC-CMP-008 두 계약서 중 하나라도 분석을 마친 적이 없으면 409 ANALYSIS_REQUIRED다")
    void requiresBothContractsAnalyzed() {
        when(analyses.findLatestAnalyzed(BEFORE, OWNER)).thenReturn(Optional.of(analyzed(100, BEFORE)));
        when(analyses.findLatestAnalyzed(AFTER, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(OWNER, BEFORE, AFTER))
                .isInstanceOfSatisfying(ConflictException.class,
                        exception -> assertThat(exception.code()).isEqualTo("ANALYSIS_REQUIRED"));
        verify(embeddings, never()).findSimilarities(anyLong(), anyLong());
        verify(comparisons, never()).save(any());
    }

    @Test
    @DisplayName("TC-CMP-011·TC-CRD-009 최신 분석 결과와 저장된 임베딩으로 짝을 만들어 비교와 쌍을 저장하고 이용권은 쓰지 않는다")
    void createsComparisonWithPairs() {
        when(analyses.findLatestAnalyzed(BEFORE, OWNER)).thenReturn(Optional.of(analyzed(100, BEFORE)));
        when(analyses.findLatestAnalyzed(AFTER, OWNER)).thenReturn(Optional.of(analyzed(200, AFTER)));
        when(analyses.findResults(100)).thenReturn(List.of(risk(1, RiskLevel.HIGH)));
        when(analyses.findResults(200)).thenReturn(List.of(risk(11, RiskLevel.CAUTION)));
        when(clauses.findByContract(BEFORE, OWNER)).thenReturn(List.of(clause(1, BEFORE, "위약금은 월세의 3배로 한다.")));
        when(clauses.findByContract(AFTER, OWNER)).thenReturn(List.of(clause(11, AFTER, "위약금은 월세의 1배로 한다.")));
        when(embeddings.findSimilarities(BEFORE, AFTER)).thenReturn(List.of(new ClauseSimilarity(1, 11, 0.93)));
        when(comparisons.save(any())).thenAnswer(invocation -> {
            Comparison comparison = invocation.getArgument(0);
            return new Comparison(55, comparison.owner(), comparison.beforeContractId(),
                    comparison.afterContractId(), comparison.createdAt());
        });

        Comparison created = service.create(OWNER, BEFORE, AFTER);

        assertThat(created.id()).isEqualTo(55);
        assertThat(created.owner()).isEqualTo(OWNER);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ComparisonPair>> pairs = ArgumentCaptor.forClass(List.class);
        verify(comparisons).savePairs(eq(55L), pairs.capture());
        assertThat(pairs.getValue()).containsExactly(new ComparisonPair(1L, 11L, 0.93, ChangeType.MODIFIED, -1));
    }

    @Test
    @DisplayName("FR-CMP-02 남의 비교나 없는 비교는 404 COMPARISON_NOT_FOUND다")
    void getRejectsComparisonNotOwned() {
        when(comparisons.findOwned(55, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(OWNER, 55))
                .isInstanceOfSatisfying(NotFoundException.class,
                        exception -> assertThat(exception.code()).isEqualTo("COMPARISON_NOT_FOUND"));
        verify(comparisons, never()).savePairs(anyLong(), anyList());
    }

    @Test
    @DisplayName("FR-CMP-02 저장된 쌍 순서대로 양쪽 조항의 번호·제목·본문과 최신 분석 위험 등급을 붙인다")
    void getAttachesClausesAndRiskLevelsInSavedOrder() {
        Comparison comparison = new Comparison(55, OWNER, BEFORE, AFTER, Instant.parse("2026-11-24T00:00:00Z"));
        when(comparisons.findOwned(55, OWNER)).thenReturn(Optional.of(comparison));
        when(comparisons.findPairs(55)).thenReturn(List.of(
                new ComparisonPair(1L, 11L, 0.93, ChangeType.MODIFIED, -1),
                new ComparisonPair(null, 12L, null, ChangeType.ADDED, 2),
                new ComparisonPair(2L, null, null, ChangeType.DELETED, 0)));
        when(clauses.findByContract(BEFORE, OWNER)).thenReturn(List.of(
                new Clause(1, BEFORE, OWNER, 3, "제3조(위약금)", "위약금은 월세의 3배", ""),
                new Clause(2, BEFORE, OWNER, 4, "제4조(원상복구)", "원상복구 비용은 임차인 부담", "")));
        when(clauses.findByContract(AFTER, OWNER)).thenReturn(List.of(
                new Clause(11, AFTER, OWNER, 3, "제3조(위약금)", "위약금은 월세의 1배", ""),
                new Clause(12, AFTER, OWNER, 4, "제4조(중도해지)", "중도 해지 불가", "")));
        when(analyses.findLatestAnalyzed(BEFORE, OWNER)).thenReturn(Optional.of(analyzed(100, BEFORE)));
        when(analyses.findLatestAnalyzed(AFTER, OWNER)).thenReturn(Optional.of(analyzed(200, AFTER)));
        when(analyses.findResults(100)).thenReturn(List.of(risk(1, RiskLevel.HIGH)));
        when(analyses.findResults(200)).thenReturn(List.of(risk(11, RiskLevel.CAUTION), risk(12, RiskLevel.HIGH)));

        ComparisonDetail detail = service.get(OWNER, 55);

        assertThat(detail.comparison()).isEqualTo(comparison);
        assertThat(detail.entries()).extracting(entry -> entry.pair().changeType())
                .containsExactly(ChangeType.MODIFIED, ChangeType.ADDED, ChangeType.DELETED);
        ComparisonDetail.Entry modified = detail.entries().getFirst();
        assertThat(modified.before()).isEqualTo(new ComparedClause(1, 3, "제3조(위약금)", "위약금은 월세의 3배", RiskLevel.HIGH));
        assertThat(modified.after()).isEqualTo(new ComparedClause(11, 3, "제3조(위약금)", "위약금은 월세의 1배", RiskLevel.CAUTION));
        assertThat(detail.entries().get(1).before()).isNull();
        assertThat(detail.entries().get(1).after().riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(detail.entries().get(2).after()).isNull();
        assertThat(detail.entries().get(2).before().riskLevel()).isNull();
        assertThat(detail.summary()).isEqualTo(new ComparisonDetail.Summary(1, 1, 1, 0));
    }

    @Test
    @DisplayName("FR-CMP-02 쌍이 가리키는 조항이 없어졌거나 분석 완료 건이 없으면 그쪽 정보만 비우고 나머지는 보여 준다")
    void getToleratesMissingClausesAndAnalyses() {
        Comparison comparison = new Comparison(55, OWNER, BEFORE, AFTER, Instant.parse("2026-11-24T00:00:00Z"));
        when(comparisons.findOwned(55, OWNER)).thenReturn(Optional.of(comparison));
        when(comparisons.findPairs(55)).thenReturn(List.of(new ComparisonPair(1L, 11L, 1.0, ChangeType.SAME, 0)));
        when(clauses.findByContract(BEFORE, OWNER)).thenReturn(List.of());
        when(clauses.findByContract(AFTER, OWNER)).thenReturn(List.of(new Clause(11, AFTER, OWNER, 1, "제1조", "본문", "")));
        when(analyses.findLatestAnalyzed(anyLong(), any())).thenReturn(Optional.empty());

        ComparisonDetail detail = service.get(OWNER, 55);

        assertThat(detail.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.before()).isNull();
            assertThat(entry.after()).isEqualTo(new ComparedClause(11, 1, "제1조", "본문", null));
        });
    }

    private static Analysis analyzed(long id, long contractId) {
        Analysis accepted = Analysis.accepted(contractId, OWNER, Instant.parse("2026-11-24T00:00:00Z"));
        Analysis withId = new Analysis(id, accepted.contractId(), accepted.owner(), accepted.status(),
                accepted.overallScore(), accepted.summary(), accepted.modelVersion(), accepted.createdAt());
        return withId.analyzed(new AnalysisSummary(50, "요약"), "test-model");
    }

    private static RiskResult risk(long clauseId, RiskLevel level) {
        return new RiskResult(clauseId, level != RiskLevel.SAFE, level, "PENALTY", 0.8);
    }

    private static Clause clause(long id, long contractId, String text) {
        return new Clause(id, contractId, OWNER, 1, "제1조", text, "");
    }
}
