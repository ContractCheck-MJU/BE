package ac.mju.contractcheck.service;

import ac.mju.contractcheck.repository.PrecedentRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PrecedentService {

    @NonNull
    private final PrecedentRepository precedents;


    /**
     * FR-PRC-01 근거 검색
     *
     * TODO(FR-PRC-01): 법령·판례 청크를 벡터 검색한다.
     * 1. 입력 검증: query, type, k(상한은 시트/설정). 빈 query면 빈 목록 또는 검증 오류.
     * 2. 권한/소유자 확인: 로그인 사용자. 관리자 전용 데이터는 아니다.
     * 3. DB/외부 호출: InferenceClient POST /embed로 query를 1024차원 임베딩한 뒤
     *    legal_chunks를 legal_documents와 조인해 {@code ORDER BY embedding <=> :q LIMIT k}로 검색한다.
     *    doc_type=type 필터. 파이프라인은 BM25/ILIKE 하이브리드를 권고한다.
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: INFERENCE_UNAVAILABLE. 적재 전이면 빈 목록(현재 스텁과 동일).
     * 6. 반환: title, ref, text, score, url 등. 스펙 미정: 키워드 점수와 벡터 점수의 결합 가중치, k 기본·상한.
     */
    public List<Map<String, Object>> searchPrecedents(String query, String type, int k) {
        return precedents.searchPrecedents(query, type, k);
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
