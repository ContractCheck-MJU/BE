package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.domain.AnalysisSummary;
import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.ClauseEmbedding;
import ac.mju.contractcheck.domain.ClauseRisk;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.RiskResult;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.global.error.CreditInsufficientException;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.repository.AnalysisRepository;
import ac.mju.contractcheck.repository.ClauseEmbeddingRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * FR-ANL-01 조항 위험 분류, FR-ANL-02 분석 결과 조회.
 *
 * <p>접수는 요청 스레드에서 짧게 끝낸다: 검증 → 이용권 확인 → 분석 행(ANALYZING) 생성 → 이용권 차감 → 비동기 판정 접수.
 * 판정은 별도 스레드에서 추론 서버를 부르고(트랜잭션 밖), 결과·임베딩 저장과 상태 전이는 한 트랜잭션으로 묶는다.
 * 접수 뒤 어느 단계에서든 실패하면 분석을 FAILED로 바꾸고 이용권을 복구한다(TC-CRD-007).
 */
@Slf4j
@Service
public class AnalysisService {

    private final AnalysisRepository analyses;
    private final ContractService contracts;
    private final ClauseService clauseService;
    private final ClauseRiskService riskService;
    private final CreditService credits;
    private final ClauseEmbeddingRepository embeddings;
    private final TransactionTemplate transaction;
    private final Executor executor;

    public AnalysisService(
            @NonNull AnalysisRepository analyses,
            @NonNull ContractService contracts,
            @NonNull ClauseService clauseService,
            @NonNull ClauseRiskService riskService,
            @NonNull CreditService credits,
            @NonNull ClauseEmbeddingRepository embeddings,
            @NonNull TransactionTemplate transaction,
            @NonNull @Qualifier("analysisExecutor") Executor executor) {
        this.analyses = analyses;
        this.contracts = contracts;
        this.clauseService = clauseService;
        this.riskService = riskService;
        this.credits = credits;
        this.embeddings = embeddings;
        this.transaction = transaction;
        this.executor = executor;
    }

    /**
     * FR-ANL-01 조항 위험 분류
     * 분석을 접수하고 이용권 1회를 차감한 뒤 판정을 비동기로 시작한다. 이용권이 없으면 분석 행을 만들지 않는다(TC-CRD-004).
     * 같은 계약에 판정 중인 분석이 있으면 409 ANALYSIS_IN_PROGRESS(TC-CRD-006), 조항 분할이 끝나지 않았으면 409 CONTRACT_NOT_READY다.
     * 없는 계약과 타인 계약은 404다.
     *
     * TODO(FR-ANL-01): kftc_cases 검색과 similar_case_ids, 위험 조항의 Claude 설명(FR-EXP-01), 업로드 429 한도(스펙 미정).
     * 스펙 미정: overall_score·summary 산출 공식(AnalysisSummary는 임시 규칙), 재분석 시 기존 분석 보존 범위,
     * contracts.status(UPLOADED/OCR/SPLIT)와 분석 상태의 동기화, 서버가 중간에 죽어 ANALYZING으로 남은 분석의 복구.
     * 같은 계약의 동시 접수는 확인과 저장 사이에 틈이 있어 DB 유니크 제약이 필요하다(마이그레이션 번호 공지 후 추가).
     */
    public Analysis start(UserId owner, long contractId) {
        Contract contract = contracts.get(owner, contractId);
        if (!"SPLIT".equals(contract.status())) {
            throw new ConflictException("CONTRACT_NOT_READY", "조항 분할이 끝난 계약서만 분석할 수 있습니다.");
        }
        if (analyses.existsInProgress(contractId)) {
            throw new ConflictException("ANALYSIS_IN_PROGRESS", "이 계약서의 분석이 이미 진행 중입니다.");
        }
        CreditAccount account = credits.me(owner);
        if (account.freeRemaining() + account.paidRemaining() <= 0) {
            throw new CreditInsufficientException();
        }
        List<Clause> clauses = clauseService.list(owner, contractId);
        if (clauses.isEmpty()) {
            throw new ConflictException("CONTRACT_NOT_READY", "분석할 조항이 없습니다.");
        }

        Analysis accepted = transaction.execute(status -> analyses.save(Analysis.accepted(contractId, owner, Instant.now())));
        try {
            credits.use(owner, accepted.id());
        } catch (RuntimeException exception) {
            // 차감되지 않았으면 분석 행도 남기지 않는다(TC-CRD-004·005).
            transaction.executeWithoutResult(status -> analyses.delete(accepted.id()));
            throw exception;
        }
        try {
            executor.execute(() -> run(accepted, clauses));
        } catch (RuntimeException exception) {
            fail(accepted);
            throw exception;
        }
        log.info("분석 접수 analysisId={} contractId={} userId={}", accepted.id(), contractId, owner);
        return accepted;
    }

    /** 판정 스레드의 본체. 어떤 예외도 밖으로 내보내지 않고 FAILED 처리와 복구로 끝낸다. */
    private void run(Analysis accepted, List<Clause> clauses) {
        try {
            List<ClauseRisk> risks = riskService.assess(clauses);
            AnalysisSummary summary = AnalysisSummary.of(risks.stream().map(risk -> risk.assessment().level()).toList());
            String modelVersion = risks.isEmpty() ? null : risks.get(0).modelVersion();
            transaction.executeWithoutResult(status -> {
                embeddings.saveAll(risks.stream().map(risk -> new ClauseEmbedding(risk.clauseId(), risk.embedding())).toList());
                analyses.saveResults(accepted.id(), risks.stream().map(RiskResult::from).toList());
                analyses.save(accepted.analyzed(summary, modelVersion));
            });
            log.info("분석 완료 analysisId={} clauses={}", accepted.id(), clauses.size());
        } catch (RuntimeException exception) {
            log.warn("분석 실패 analysisId={} 원인={}", accepted.id(), exception.toString());
            fail(accepted);
        }
    }

    /** FAILED로 바꾸고 이용권을 복구한다. 둘 중 하나가 실패해도 다른 하나는 시도하고, 예외는 밖으로 내보내지 않는다. */
    private void fail(Analysis accepted) {
        try {
            transaction.executeWithoutResult(status -> analyses.save(accepted.failed()));
        } catch (RuntimeException exception) {
            log.error("분석 FAILED 저장 실패 analysisId={}", accepted.id(), exception);
        }
        try {
            credits.restore(accepted.owner(), accepted.id());
        } catch (RuntimeException exception) {
            log.error("이용권 복구 실패 analysisId={} userId={}", accepted.id(), accepted.owner(), exception);
        }
    }

    /**
     * FR-ANL-02 분석 결과 조회
     * 분석 헤더(status, overall_score, summary)를 돌려준다. 진행 중이면 status=ANALYZING이다. 없는 분석과 타인 분석은 404 ANALYSIS_NOT_FOUND다(TC-ANL-019).
     */
    public Analysis get(UserId owner, long id) {
        return analyses.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("ANALYSIS_NOT_FOUND", "분석 결과를 찾을 수 없습니다."));
    }

    /**
     * FR-ANL-02 분석 결과 조회
     * 해당 분석의 조항별 위험 결과를 돌려준다. 소유자 확인은 {@link #get}과 같다.
     * TODO(FR-ANL-02): similarCases(공정위 사례)와 "근거 부족" 표기(저장 컬럼이 스키마에 없어 스펙 확인 필요).
     */
    public List<RiskResult> results(UserId owner, long id) {
        get(owner, id);
        return analyses.findResults(id);
    }
}
