package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.ClauseSplitResult;
import ac.mju.contractcheck.domain.ClauseSplitter;
import ac.mju.contractcheck.domain.StoredClauses;
import ac.mju.contractcheck.global.error.ApiException;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.repository.ClauseRepository;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClauseService {

    private static final int MAX_TITLE_LENGTH = 200;

    @NonNull
    private final ClauseRepository clauses;
    @NonNull
    private final ContractService contracts;

    private final ClauseSplitter splitter = new ClauseSplitter();

    /**
     * FR-UPL-03 조항 분할
     * OCR 원문을 조항으로 나눠 저장한다. 그 계약에 이미 있던 조항은 모두 지우고 새 결과로 바꾼다.
     * 없는 계약과 타인 계약은 404 CONTRACT_NOT_FOUND다. 조항 패턴이 없으면 문단 단위로 대체 분할하고 paragraphFallback을 true로 돌려준다(TC-UPL-012).
     *
     * TODO(FR-UPL-02): OCR이 끝나면 이 메서드를 부르고 contracts.status를 OCR에서 SPLIT으로 바꾼다. 지금은 호출하는 곳이 없다.
     * 스펙 미정: 분할 대상이 OCR 원문인지 마스킹본인지(FR-UPL-04), 번호·문단 조항의 title(지금은 빈 문자열), 첫 조항 앞 문구 처리.
     */
    public StoredClauses split(String owner, long contractId, String rawText) {
        contracts.get(owner, contractId);
        ClauseSplitResult result = splitter.split(rawText);
        List<Clause> saved = clauses.replaceAll(contractId, owner, result.clauses());
        log.info("조항 분할 저장 contractId={} count={} paragraphFallback={}", contractId, saved.size(), result.paragraphFallback());
        return new StoredClauses(saved, result.paragraphFallback());
    }

    /**
     * FR-UPL-03 조항 분할
     * 계약의 조항 목록을 번호 순으로 돌려준다. 없는 계약과 타인 계약은 404 CONTRACT_NOT_FOUND이고, 조항이 없으면 빈 목록이다.
     *
     * TODO(FR-ANL-02): 분석이 영속화되면 risk_results를 조인해 위험 등급을 함께 내린다(지금은 빈 문자열).
     */
    public List<Clause> list(String owner, long contractId) {
        contracts.get(owner, contractId);
        return clauses.findByContract(contractId, owner);
    }

    /**
     * FR-UPL-03 조항 분할
     * 조항의 번호·제목·본문 중 전달한 값만 수정한다(TC-UPL-013). 없는 조항과 타인 조항은 404 CLAUSE_NOT_FOUND다.
     * 번호는 1 이상, 제목은 200자 이내, 본문은 공백만이면 안 되고, 어긋나면 400 VALIDATION_ERROR다.
     * 임베딩은 재분석 시 다시 계산한다.
     *
     * 스펙 미정: 수정 후 재분석 필요를 표시하는 방법(clauses에 플래그 컬럼이 없어 needsReanalysis는 항상 false),
     * 수정 즉시 재분석을 자동 시작할지, 번호가 다른 조항과 겹칠 때의 처리.
     */
    public Clause update(String owner, long id, Integer no, String title, String text) {
        validateEdit(no, title, text);
        Clause clause = clauses.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("CLAUSE_NOT_FOUND", "조항을 찾을 수 없습니다."));
        Clause saved = clauses.save(clause.withEdit(no, title, text));
        log.info("조항 수정 clauseId={} owner={}", saved.id(), owner);
        return saved;
    }

    private void validateEdit(Integer no, String title, String text) {
        if (no != null && no < 1) {
            throw new ApiException(400, "VALIDATION_ERROR", "조항 번호는 1 이상이어야 합니다.");
        }
        if (title != null && title.length() > MAX_TITLE_LENGTH) {
            throw new ApiException(400, "VALIDATION_ERROR", "조항 제목은 200자 이내여야 합니다.");
        }
        if (text != null && text.isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "조항 본문은 비어 있을 수 없습니다.");
        }
    }
}
