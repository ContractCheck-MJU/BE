package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.EvidenceHit;
import ac.mju.contractcheck.global.error.ApiException;
import ac.mju.contractcheck.repository.memory.PrecedentRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * FR-PRC-01 근거 검색
 */
@Service
@RequiredArgsConstructor
public class PrecedentService {

    public static final int MIN_LIMIT = 3;
    public static final int MAX_LIMIT = 5;
    private static final Set<String> ALLOWED_DOC_TYPES = Set.of("LAW", "PRECEDENT");

    @NonNull
    private final EvidenceRetriever evidenceRetriever;
    @NonNull
    private final PrecedentRepository precedents;

    /**
     * FR-PRC-01 근거 검색
     * 질의어를 추론 서버(/embed)로 임베딩해 legal_chunks에서 pgvector 코사인 유사도 순으로 검색한다(1차 순수 벡터 검색).
     * q는 공백일 수 없고, k는 3~5 범위여야 하며, type을 지정하면 LAW 또는 PRECEDENT만 허용한다.
     * 외부 추론 호출이 포함되어 트랜잭션을 열지 않으며, 추론 서버 실패는 503 INFERENCE_UNAVAILABLE이다.
     */
    public List<EvidenceHit> searchPrecedents(String query, String type, int k) {
        if (query == null || query.isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "검색어(q)는 비어 있을 수 없습니다.");
        }
        if (k < MIN_LIMIT || k > MAX_LIMIT) {
            throw new ApiException(400, "VALIDATION_ERROR", "k는 3 이상 5 이하여야 합니다.");
        }
        String docType = normalizeDocType(type);
        return evidenceRetriever.search(query.trim(), docType, k);
    }

    private static String normalizeDocType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        if (!ALLOWED_DOC_TYPES.contains(type)) {
            throw new ApiException(400, "VALIDATION_ERROR", "유형(type)은 LAW 또는 PRECEDENT여야 합니다.");
        }
        return type;
    }

    /**
     * FR-PRC-01 근거 검색
     *
     * TODO(FR-PRC-01): 공정위 시정 사례를 벡터 검색한다.
     * 1. 입력 검증: query 또는 clauseId, unfairType, k.
     * 2. 권한/소유자 확인: 로그인 사용자. clauseId가 있으면 해당 조항의 계약 소유자를 확인한다. 아니면 CLAUSE_NOT_FOUND(404).
     * 3. DB/외부 호출: clauseId가 있으면 clauses.embedding을 쓰고, 없으면 query를 POST /embed한다.
     *    kftc_cases에서 {@code ORDER BY embedding <=> ? LIMIT k}, unfair_type 필터.
     *    재적재 후 결과가 반영되어야 한다(TC-ADM-014, TC-PRC).
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: CLAUSE_NOT_FOUND, INFERENCE_UNAVAILABLE.
     * 6. 반환: press_title, unfair_type, before_text, after_text, after_is_deletion, score.
     */
    public List<Map<String, Object>> searchCases(String query, Long clauseId, String unfairType, int k) {
        return precedents.searchCases(query, clauseId, unfairType, k);
    }
}
