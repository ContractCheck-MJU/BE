package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.Comparison;
import ac.mju.contractcheck.repository.ComparisonRepository;
import ac.mju.contractcheck.global.error.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonService {
    @NonNull
    private final ComparisonRepository comparisons;
    @NonNull
    private final ContractService contracts;


    /**
     * FR-CMP-01 수정본 업로드·비교
     *
     * TODO(FR-CMP-01): 두 계약의 조항을 저장된 임베딩으로 정렬·비교한다. 이용권은 차감하지 않는다(TC-CRD-009).
     * 1. 입력 검증: before/after 계약 ID가 있어야 하고, 같으면 SAME_CONTRACT. 둘 다 분석 완료여야 한다(TC-CMP-008, ANALYSIS_REQUIRED).
     * 2. 권한/소유자 확인: 두 계약 모두 contracts.user_id가 현재 사용자. 아니면 CONTRACT_NOT_FOUND(404).
     * 3. DB/외부 호출: clauses.embedding을 읽어 Spring에서 코사인({@code embedding <=> ?}) + HungarianMatcher(임계값 0.6)로 쌍을 만든다.
     *    FastAPI /similarity는 사용하지 않는다(합의 아키텍처. 시트 TC-CMP-011과 불일치).
     *    comparisons(user_id, before_contract_id, after_contract_id)와
     *    comparison_pairs(before_clause_id, after_clause_id, similarity, change_type, risk_delta)를 저장한다.
     *    change_type은 ADDED/DELETED/MODIFIED/SAME.
     * 4. 상태 전이: 비교 행 생성만. 계약 상태는 바꾸지 않는다.
     * 5. 예외/에러코드: SAME_CONTRACT, ANALYSIS_REQUIRED, CONTRACT_NOT_FOUND(404).
     * 6. 반환: comparison id. 스펙 미정: SAME과 MODIFIED의 경계(유사도 1.0인지, 문구 완전 일치인지, TC-CMP-006).
     */
    public Comparison create(String owner, long beforeContractId, long afterContractId) {
        contracts.get(owner, beforeContractId);
        contracts.get(owner, afterContractId);
        Comparison saved = comparisons.save(new Comparison(0, owner, beforeContractId, afterContractId));
        log.info("비교 생성 comparisonId={} owner={}", saved.id(), owner);
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
    public Comparison get(String owner, long id) {
        return comparisons.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("COMPARISON_NOT_FOUND", "비교 결과를 찾을 수 없습니다."));
    }
}
