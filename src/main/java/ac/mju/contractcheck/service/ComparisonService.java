package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.ClauseComparator;
import ac.mju.contractcheck.domain.ComparedClause;
import ac.mju.contractcheck.domain.Comparison;
import ac.mju.contractcheck.domain.ComparisonDetail;
import ac.mju.contractcheck.domain.ComparisonPair;
import ac.mju.contractcheck.domain.ComparisonPolicy;
import ac.mju.contractcheck.domain.RiskResult;
import ac.mju.contractcheck.domain.UserId;
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
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-CMP-01 수정본 업로드·비교
 * FR-CMP-02 변경 사항 표시
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonService {
    @NonNull
    private final ComparisonRepository comparisons;
    @NonNull
    private final ContractService contracts;
    @NonNull
    private final ClauseRepository clauses;
    @NonNull
    private final AnalysisRepository analyses;
    @NonNull
    private final ClauseEmbeddingRepository embeddings;
    @NonNull
    private final ComparisonPolicy policy;

    /**
     * FR-CMP-01 수정본 업로드·비교
     * 이미 올린 두 계약서(이전·이후)의 조항을 분석 때 저장한 임베딩으로 짝짓고, 변경 유형과 위험 변화를 계산해
     * comparisons 1행과 comparison_pairs를 저장한다. 외부 호출이 없어 요청 안에서 끝내고 이용권은 쓰지 않는다(TC-CRD-009).
     *
     * <ol>
     *   <li>같은 계약서끼리면 400 SAME_CONTRACT.</li>
     *   <li>두 계약서 모두 본인 소유여야 한다. 아니면 404 CONTRACT_NOT_FOUND(존재 여부 비공개).</li>
     *   <li>두 계약서 모두 분석을 마친 적이 있어야 한다. 아니면 409 ANALYSIS_REQUIRED(TC-CMP-008).
     *       위험 등급은 각 계약의 최신 분석 완료 건을 쓴다.</li>
     *   <li>유사도는 pgvector로 Spring에서 계산한다. FastAPI /similarity는 쓰지 않는다(합의 아키텍처, TC-CMP-011).
     *       짝·변경 유형·위험 변화 규칙은 {@link ClauseComparator}가 정한다.</li>
     * </ol>
     * 같은 두 계약서를 다시 비교하면 새 비교를 만든다(그사이 재분석했을 수 있다). 계약 유형이 달라도 비교한다.
     *
     * <p>한계: 분석 뒤에 조항을 고치면 임베딩·위험 등급은 고치기 전 기준이다. "재분석 필요" 표시가 스펙 미정이라 그 결정 때 함께 다룬다.
     * 스펙 미정: SAME_CONTRACT·ANALYSIS_REQUIRED의 HTTP 상태(지금은 400·409 제안값).
     */
    @Transactional
    public Comparison create(UserId owner, long beforeContractId, long afterContractId) {
        if (beforeContractId == afterContractId) {
            throw new ApiException(400, "SAME_CONTRACT", "같은 계약서끼리는 비교할 수 없습니다.");
        }
        contracts.get(owner, beforeContractId);
        contracts.get(owner, afterContractId);
        Analysis before = latestAnalyzed(owner, beforeContractId);
        Analysis after = latestAnalyzed(owner, afterContractId);

        List<ComparisonPair> pairs = ClauseComparator.compare(
                clauses.findByContract(beforeContractId, owner),
                clauses.findByContract(afterContractId, owner),
                embeddings.findSimilarities(beforeContractId, afterContractId),
                riskLevels(before),
                riskLevels(after),
                policy);

        Comparison saved = comparisons.save(new Comparison(0, owner, beforeContractId, afterContractId, Instant.now()));
        comparisons.savePairs(saved.id(), pairs);
        log.info("비교 생성 comparisonId={} owner={} pairs={}", saved.id(), owner, pairs.size());
        return saved;
    }

    /**
     * FR-CMP-02 변경 사항 표시
     * 본인 비교의 조항 쌍을 저장 순서대로 읽어 양쪽 조항의 번호·제목·본문과 위험 등급을 붙인다. 남의 비교나 없는 비교는
     * 404 COMPARISON_NOT_FOUND다. 조항은 계약마다 한 번씩만 읽는다(쌍마다 조회하지 않는다). SAME 쌍도 모두 돌려준다.
     *
     * <p>위험 등급은 조회 시점의 최신 분석 완료 건 기준이다. 비교한 뒤 재분석했다면 저장된 risk_delta(비교 당시 기준)와
     * 어긋날 수 있다. 비교에 쓴 분석을 저장하려면 마이그레이션이 필요해 지금은 한계로 둔다.
     * 쌍이 가리키는 조항이 없어졌으면 그쪽만 비우고 나머지는 보여 준다.
     */
    @Transactional(readOnly = true)
    public ComparisonDetail get(UserId owner, long id) {
        Comparison comparison = comparisons.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("COMPARISON_NOT_FOUND", "비교 결과를 찾을 수 없습니다."));
        Map<Long, ComparedClause> before = comparedClauses(owner, comparison.beforeContractId());
        Map<Long, ComparedClause> after = comparedClauses(owner, comparison.afterContractId());
        List<ComparisonDetail.Entry> entries = comparisons.findPairs(comparison.id()).stream()
                .map(pair -> new ComparisonDetail.Entry(
                        pair,
                        pair.beforeClauseId() == null ? null : before.get(pair.beforeClauseId()),
                        pair.afterClauseId() == null ? null : after.get(pair.afterClauseId())))
                .toList();
        return new ComparisonDetail(comparison, entries);
    }

    private Map<Long, ComparedClause> comparedClauses(UserId owner, long contractId) {
        Map<Long, RiskLevel> levels = analyses.findLatestAnalyzed(contractId, owner)
                .map(this::riskLevels)
                .orElse(Map.of());
        return clauses.findByContract(contractId, owner).stream()
                .collect(Collectors.toMap(
                        Clause::id,
                        clause -> new ComparedClause(
                                clause.id(), clause.no(), clause.title(), clause.text(), levels.get(clause.id())),
                        (first, second) -> first));
    }

    private Analysis latestAnalyzed(UserId owner, long contractId) {
        return analyses.findLatestAnalyzed(contractId, owner)
                .orElseThrow(() -> new ConflictException(
                        "ANALYSIS_REQUIRED", "두 계약서 모두 분석을 마친 뒤 비교할 수 있습니다."));
    }

    private Map<Long, RiskLevel> riskLevels(Analysis analysis) {
        return analyses.findResults(analysis.id()).stream()
                .collect(Collectors.toMap(RiskResult::clauseId, RiskResult::level, (first, second) -> first));
    }
}
