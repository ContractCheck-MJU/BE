package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.domain.ClauseComparator;
import ac.mju.contractcheck.domain.Comparison;
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
     *
     * TODO(FR-CMP-02): 비교 헤더와 comparison_pairs 목록을 함께 반환한다.
     * 1. 입력 검증: id 필수.
     * 2. 권한/소유자 확인: comparisons.user_id. 아니면 COMPARISON_NOT_FOUND(404).
     * 3. DB/외부 호출: comparisons와 comparison_pairs를 조회하고, 조항 텍스트·위험 변화를 붙인다.
     *    컨트롤러가 현재 빈 pairs를 내려 서비스가 쌍을 포함해 반환하도록 맞춰야 한다.
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: COMPARISON_NOT_FOUND(404).
     * 6. 반환: 변경 유형별 쌍 목록.
     */
    public Comparison get(UserId owner, long id) {
        return comparisons.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("COMPARISON_NOT_FOUND", "비교 결과를 찾을 수 없습니다."));
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
