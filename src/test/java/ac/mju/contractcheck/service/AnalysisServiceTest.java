package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.client.InferenceException;
import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.domain.AnalysisStatus;
import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.ClauseRisk;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.ContractState;
import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.RiskAssessment;
import ac.mju.contractcheck.domain.RiskLevel;
import ac.mju.contractcheck.domain.RiskResult;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.global.error.CreditInsufficientException;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.repository.AnalysisRepository;
import ac.mju.contractcheck.repository.ClauseEmbeddingRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 분석 접수·판정·실패 복구의 순서와 분기를 저장소·외부 서비스를 가짜로 두고 확인한다.
 * 비동기 실행기는 호출한 스레드에서 바로 실행하는 것으로 바꿔 결과가 결정적이다.
 */
class AnalysisServiceTest {

    private static final UserId OWNER = new UserId(7);
    private static final long CONTRACT_ID = 3;
    private static final long ANALYSIS_ID = 11;

    private final AnalysisRepository analyses = mock(AnalysisRepository.class);
    private final ContractService contracts = mock(ContractService.class);
    private final ClauseService clauseService = mock(ClauseService.class);
    private final ClauseRiskService riskService = mock(ClauseRiskService.class);
    private final CreditService credits = mock(CreditService.class);
    private final ClauseEmbeddingRepository embeddings = mock(ClauseEmbeddingRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private Executor executor = Runnable::run;
    private AnalysisService service;

    private final List<Clause> clauses = List.of(
            new Clause(101, CONTRACT_ID, OWNER, 1, "제1조", "첫째 조항", null),
            new Clause(102, CONTRACT_ID, OWNER, 2, "제2조", "둘째 조항", null));

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(contracts.get(OWNER, CONTRACT_ID)).thenReturn(contract(ContractState.SPLIT));
        when(analyses.existsInProgress(CONTRACT_ID)).thenReturn(false);
        when(credits.me(OWNER)).thenReturn(new CreditAccount(OWNER, 1, 0));
        when(clauseService.list(OWNER, CONTRACT_ID)).thenReturn(clauses);
        when(analyses.save(any())).thenAnswer(call -> {
            Analysis given = call.getArgument(0);
            return given.id() == 0
                    ? new Analysis(ANALYSIS_ID, given.contractId(), given.owner(), given.status(), 0, "", null, given.createdAt())
                    : given;
        });
        service = newService();
    }

    private AnalysisService newService() {
        return new AnalysisService(analyses, contracts, clauseService, riskService, credits, embeddings,
                new TransactionTemplate(transactionManager), executor);
    }

    private static Contract contract(ContractState status) {
        return new Contract(CONTRACT_ID, OWNER, "계약", "MONTHLY", "contracts/k", status, "", Instant.now(), null);
    }

    private static ClauseRisk risk(long clauseId, RiskLevel level) {
        return new ClauseRisk(clauseId, level == RiskLevel.SAFE ? null : "면책", 0.9,
                new RiskAssessment(level, false, false), "mock-0.1", new float[] {0.1f, 0.2f}, List.of());
    }

    @Test
    @DisplayName("TC-ANL-001 접수: 분석 행을 만들고 이용권을 차감한 뒤 판정해 결과와 임베딩을 저장하고 ANALYZED로 바꾼다")
    void startRunsPipelineInOrder() {
        when(riskService.assess(clauses)).thenReturn(List.of(risk(101, RiskLevel.HIGH), risk(102, RiskLevel.SAFE)));

        Analysis accepted = service.start(OWNER, CONTRACT_ID);

        assertThat(accepted.id()).isEqualTo(ANALYSIS_ID);
        assertThat(accepted.status()).isEqualTo(AnalysisStatus.ANALYZING);
        InOrder order = inOrder(analyses, credits, riskService, embeddings);
        order.verify(analyses).save(any());
        order.verify(credits).use(OWNER, ANALYSIS_ID);
        order.verify(riskService).assess(clauses);
        order.verify(embeddings).saveAll(anyList());
        order.verify(analyses).saveResults(anyLong(), anyList());
        ArgumentCaptor<Analysis> saved = ArgumentCaptor.forClass(Analysis.class);
        order.verify(analyses).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(AnalysisStatus.ANALYZED);
        assertThat(saved.getValue().summary()).isEqualTo("위험 1건, 주의 0건, 정상 1건");
        assertThat(saved.getValue().modelVersion()).isEqualTo("mock-0.1");
        verify(credits, never()).restore(any(), anyLong());
    }

    @Test
    @DisplayName("조항별 결과는 위험 여부·등급·유형·확률로 저장한다")
    void resultsArePersistedPerClause() {
        when(riskService.assess(clauses)).thenReturn(List.of(risk(101, RiskLevel.HIGH), risk(102, RiskLevel.SAFE)));

        service.start(OWNER, CONTRACT_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RiskResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(analyses).saveResults(org.mockito.ArgumentMatchers.eq(ANALYSIS_ID), captor.capture());
        assertThat(captor.getValue()).containsExactly(
                new RiskResult(101, true, RiskLevel.HIGH, "면책", 0.9),
                new RiskResult(102, false, RiskLevel.SAFE, null, 0.9));
    }

    @Test
    @DisplayName("조항 분할이 끝나지 않은 계약(status≠SPLIT)은 409 CONTRACT_NOT_READY이고 차감·분석 행이 없다")
    void contractNotSplitIsRejected() {
        when(contracts.get(OWNER, CONTRACT_ID)).thenReturn(contract(ContractState.UPLOADED));

        assertThatThrownBy(() -> service.start(OWNER, CONTRACT_ID))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo("CONTRACT_NOT_READY"));

        verify(analyses, never()).save(any());
        verify(credits, never()).use(any(), anyLong());
    }

    @Test
    @DisplayName("TC-CRD-006 같은 계약에 판정 중인 분석이 있으면 409 ANALYSIS_IN_PROGRESS이고 차감하지 않는다")
    void duplicateAnalysisIsRejected() {
        when(analyses.existsInProgress(CONTRACT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.start(OWNER, CONTRACT_ID))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo("ANALYSIS_IN_PROGRESS"));

        verify(credits, never()).use(any(), anyLong());
        verify(analyses, never()).save(any());
    }

    @Test
    @DisplayName("TC-CRD-004 이용권이 없으면 402이고 분석 행을 만들지 않는다")
    void noCreditCreatesNoAnalysis() {
        when(credits.me(OWNER)).thenReturn(new CreditAccount(OWNER, 0, 0));

        assertThatThrownBy(() -> service.start(OWNER, CONTRACT_ID)).isInstanceOf(CreditInsufficientException.class);

        verify(analyses, never()).save(any());
        verify(credits, never()).use(any(), anyLong());
    }

    @Test
    @DisplayName("TC-CRD-005 동시 요청 경쟁에서 져서 차감이 거절되면 만든 분석 행을 지우고 판정하지 않는다")
    void lostRaceDeletesAcceptedAnalysis() {
        doThrow(new CreditInsufficientException()).when(credits).use(OWNER, ANALYSIS_ID);

        assertThatThrownBy(() -> service.start(OWNER, CONTRACT_ID)).isInstanceOf(CreditInsufficientException.class);

        verify(analyses).delete(ANALYSIS_ID);
        verify(riskService, never()).assess(any());
        verify(credits, never()).restore(any(), anyLong());
    }

    @Test
    @DisplayName("TC-ANL-016·TC-CRD-007 추론 서버 장애: FAILED로 바꾸고 이용권을 복구하며 결과는 저장하지 않는다")
    void inferenceFailureMarksFailedAndRestores() {
        when(riskService.assess(clauses)).thenThrow(new InferenceException("분석 서버 점검 중"));

        Analysis accepted = service.start(OWNER, CONTRACT_ID);

        assertThat(accepted.status()).isEqualTo(AnalysisStatus.ANALYZING);
        ArgumentCaptor<Analysis> saved = ArgumentCaptor.forClass(Analysis.class);
        verify(analyses, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).status()).isEqualTo(AnalysisStatus.FAILED);
        verify(analyses, never()).saveResults(anyLong(), anyList());
        verify(credits).restore(OWNER, ANALYSIS_ID);
    }

    @Test
    @DisplayName("결과 저장이 실패해도 FAILED로 바꾸고 이용권을 복구한다")
    void persistFailureMarksFailedAndRestores() {
        when(riskService.assess(clauses)).thenReturn(List.of(risk(101, RiskLevel.HIGH), risk(102, RiskLevel.SAFE)));
        doThrow(new IllegalStateException("저장 실패")).when(analyses).saveResults(anyLong(), anyList());

        service.start(OWNER, CONTRACT_ID);

        ArgumentCaptor<Analysis> saved = ArgumentCaptor.forClass(Analysis.class);
        verify(analyses, org.mockito.Mockito.atLeast(2)).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(AnalysisStatus.FAILED);
        verify(credits).restore(OWNER, ANALYSIS_ID);
    }

    @Test
    @DisplayName("비동기 작업을 접수하지 못하면 FAILED로 바꾸고 이용권을 복구한 뒤 오류를 알린다")
    void rejectedExecutionMarksFailedAndRestores() {
        executor = command -> {
            throw new RejectedExecutionException("대기열 가득");
        };
        service = newService();

        assertThatThrownBy(() -> service.start(OWNER, CONTRACT_ID)).isInstanceOf(RejectedExecutionException.class);

        verify(credits).restore(OWNER, ANALYSIS_ID);
        ArgumentCaptor<Analysis> saved = ArgumentCaptor.forClass(Analysis.class);
        verify(analyses, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).status()).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    @DisplayName("복구까지 실패해도 분석은 FAILED로 남고 예외가 밖으로 나가지 않는다")
    void restoreFailureDoesNotEscape() {
        when(riskService.assess(clauses)).thenThrow(new InferenceException("장애"));
        doThrow(new IllegalStateException("복구 실패")).when(credits).restore(OWNER, ANALYSIS_ID);

        service.start(OWNER, CONTRACT_ID);

        ArgumentCaptor<Analysis> saved = ArgumentCaptor.forClass(Analysis.class);
        verify(analyses, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).status()).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    @DisplayName("TC-ANL-019 남의 분석이나 없는 분석은 404 ANALYSIS_NOT_FOUND다")
    void getUnknownAnalysisIs404() {
        when(analyses.findOwned(ANALYSIS_ID, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(OWNER, ANALYSIS_ID))
                .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.code()).isEqualTo("ANALYSIS_NOT_FOUND"));
        assertThatThrownBy(() -> service.results(OWNER, ANALYSIS_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("분석 결과 조회는 소유자를 확인한 뒤 조항별 결과를 돌려준다")
    void resultsAreReturnedForOwner() {
        Analysis analysis = new Analysis(ANALYSIS_ID, CONTRACT_ID, OWNER, AnalysisStatus.ANALYZED, 50, "요약", "m", Instant.now());
        List<RiskResult> stored = List.of(new RiskResult(101, true, RiskLevel.HIGH, "면책", 0.9));
        when(analyses.findOwned(ANALYSIS_ID, OWNER)).thenReturn(Optional.of(analysis));
        when(analyses.findResults(ANALYSIS_ID)).thenReturn(stored);

        assertThat(service.results(OWNER, ANALYSIS_ID)).isEqualTo(stored);
    }
}
