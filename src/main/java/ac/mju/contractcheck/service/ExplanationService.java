package ac.mju.contractcheck.service;

import ac.mju.contractcheck.client.LlmClient;
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
import ac.mju.contractcheck.global.error.ApiException;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.repository.AnalysisRepository;
import ac.mju.contractcheck.repository.ClauseRepository;
import ac.mju.contractcheck.repository.ExplanationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * FR-EXP-01 쉬운말 설명 생성
 * FR-EXP-02 근거 첨부
 * FR-EXP-03 수정 문구 제안
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExplanationService {

    /** 지원하는 설명 톤. 스펙 미정: 다른 톤 값과 저장 컬럼. 정해질 때까지 기본 하나만 받는다. */
    static final String DEFAULT_TONE = "기본";

    @NonNull
    private final ExplanationRepository explanations;
    @NonNull
    private final ClauseRepository clauses;
    @NonNull
    private final AnalysisRepository analyses;
    @NonNull
    private final PiiMaskingService masking;
    @NonNull
    private final EvidenceRetriever evidenceRetriever;
    @NonNull
    private final LlmClient llm;
    @NonNull
    private final RiskPolicy riskPolicy;
    @NonNull
    private final TransactionTemplate transaction;

    /**
     * FR-EXP-01 쉬운말 설명 생성, FR-EXP-02 근거 첨부, FR-EXP-03 수정 문구 제안
     * 조항 하나의 쉬운말 설명과 수정 문구를 Claude로 만들고, 프롬프트에 넣은 근거를 인용으로 저장한다.
     * 이용권은 차감하지 않는다(TC-CRD-009).
     *
     * <ol>
     *   <li>tone은 없거나 "기본"이어야 한다. 아니면 400 VALIDATION_ERROR.</li>
     *   <li>조항이 본인 계약의 것이어야 한다. 아니면 404 CLAUSE_NOT_FOUND(존재 여부 비공개).</li>
     *   <li>계약이 분석을 마친 적이 있어야 한다. 아니면 409 ANALYSIS_REQUIRED(TC-EXP-008).</li>
     *   <li>같은 조항은 최초 1회 + 재생성 3회까지다. 넘으면 429 REGENERATE_LIMIT(TC-EXP-010).</li>
     *   <li>근거는 분석 때 저장한 조항 임베딩으로 legal_chunks Top-5를 찾고, 근거 임계값(RiskPolicy) 이상만 쓴다.</li>
     *   <li>조항은 마스킹한 본문만 Claude에 보낸다. Claude 호출은 트랜잭션 밖이고, 시간 초과면 504 LLM_TIMEOUT(TC-EXP-013).</li>
     *   <li>설명과 인용은 한 트랜잭션으로 저장한다. 계약·분석 상태는 바꾸지 않는다.</li>
     * </ol>
     * 스펙 미정: ANALYSIS_REQUIRED·REGENERATE_LIMIT·LLM_TIMEOUT의 HTTP 상태(지금은 409·429·504 제안값),
     * tone 허용 값, kftc_cases 근거(적재 데이터가 없어 legal_chunks만 쓴다).
     * 한계: 재생성 횟수는 세고 저장하는 사이에 잠그지 않아, 같은 조항을 동시에 요청하면 한도를 한 번 넘을 수 있다.
     */
    public Explanation create(UserId owner, long clauseId, String tone) {
        if (tone != null && !DEFAULT_TONE.equals(tone)) {
            throw new ApiException(400, "VALIDATION_ERROR", "지원하지 않는 설명 톤입니다.");
        }
        Clause clause = clauses.findOwned(clauseId, owner)
                .orElseThrow(() -> new NotFoundException("CLAUSE_NOT_FOUND", "조항을 찾을 수 없습니다."));
        Analysis analysis = analyses.findLatestAnalyzed(clause.contractId(), owner)
                .orElseThrow(() -> new ConflictException("ANALYSIS_REQUIRED", "계약서 분석을 마친 뒤 설명을 볼 수 있습니다."));
        if (!Explanation.canCreate(explanations.countByClause(clauseId))) {
            throw new ApiException(429, "REGENERATE_LIMIT", "이 조항의 설명은 더 만들 수 없습니다.");
        }

        Optional<RiskResult> risk = analyses.findResults(analysis.id()).stream()
                .filter(result -> result.clauseId() == clauseId)
                .findFirst();
        List<EvidenceHit> evidence = evidenceRetriever.searchByClause(clauseId, EvidenceRetriever.DEFAULT_LIMIT).stream()
                .filter(hit -> hit.similarity() >= riskPolicy.evidenceScoreThreshold())
                .toList();
        ExplanationContext context = new ExplanationContext(
                masking.mask(clause.text()),
                risk.map(result -> result.level().name()).orElse(""),
                risk.map(RiskResult::riskType).orElse(""),
                evidence);

        ExplanationDraft draft = llm.explain(context);

        Explanation saved = transaction.execute(status -> explanations.save(new Explanation(
                0, clauseId, owner, draft.text(), draft.suggestion(), draft.llmModel(), draft.promptVersion(),
                Instant.now(), ExplanationCitation.fromEvidence(evidence))));
        log.info("설명 생성 explanationId={} clauseId={} owner={} citations={}",
                saved.id(), clauseId, owner, saved.citations().size());
        return saved;
    }

    /**
     * FR-EXP-02 근거 첨부, FR-EXP-03 수정 문구 제안
     * 본인 조항의 설명을 인용과 함께 돌려준다. 없거나 남의 설명이면 404 EXPLANATION_NOT_FOUND다.
     */
    @Transactional(readOnly = true)
    public Explanation get(UserId owner, long id) {
        return explanations.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("EXPLANATION_NOT_FOUND", "설명을 찾을 수 없습니다."));
    }
}
