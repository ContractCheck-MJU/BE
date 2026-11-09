package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.repository.ClauseRepository;
import ac.mju.contractcheck.service.ContractService;
import ac.mju.contractcheck.global.error.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClauseService {
    @NonNull
    private final ClauseRepository clauses;
    @NonNull
    private final ContractService contracts;


    /**
     * FR-UPL-03 조항 분할
     *
     * TODO(FR-UPL-03): 계약의 조항 목록을 반환한다.
     * 1. 입력 검증: contractId 필수.
     * 2. 권한/소유자 확인: contracts.user_id. 아니면 CONTRACT_NOT_FOUND(404).
     * 3. DB/외부 호출: clauses를 contract_id, clause_no 순으로 조회한다. 분석이 있으면 risk_results를 조인해 위험 등급을 함께 내릴 수 있다(API 명세 필드 기준).
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: CONTRACT_NOT_FOUND(404).
     * 6. 반환: clause_no, title, text, (있으면) risk_level.
     */
    public List<Clause> list(String owner, long contractId) {
        contracts.get(owner, contractId);
        return clauses.findByContract(contractId, owner);
    }

    /**
     * FR-UPL-03 조항 분할
     *
     * TODO(FR-UPL-03): 분할된 조항을 수정한다(TC-UPL-013).
     * 1. 입력 검증: no/title/text 형식과 공백 여부를 확인한다.
     * 2. 권한/소유자 확인: clauses.contract_id → contracts.user_id. 아니면 CLAUSE_NOT_FOUND(404).
     * 3. DB/외부 호출: clauses.clause_no, title, text를 갱신한다. 임베딩은 재분석 시 다시 계산한다.
     * 4. 상태 전이: 재분석이 필요함을 표시한다. 스펙 미정: 재분석 필요 플래그 컬럼(clauses에 없음), 수정 즉시 재분석을 자동 시작할지 여부.
     * 5. 예외/에러코드: CLAUSE_NOT_FOUND(404).
     * 6. 반환: 수정된 조항.
     */
    public Clause update(String owner, long id, Integer no, String title, String text) {
        Clause clause = clauses.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("CLAUSE_NOT_FOUND", "조항을 찾을 수 없습니다."));
        Clause saved = clauses.save(clause.withEdit(no, title, text));
        log.info("조항 수정 clauseId={} owner={}", saved.id(), owner);
        return saved;
    }
}
