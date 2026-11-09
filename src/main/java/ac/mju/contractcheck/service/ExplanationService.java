package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.Explanation;
import ac.mju.contractcheck.repository.ExplanationRepository;
import ac.mju.contractcheck.global.error.NotFoundException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExplanationService {
    @NonNull
    private final ExplanationRepository explanations;


    /**
     * FR-EXP-01 쉬운말 설명 생성, FR-EXP-03 수정 문구 제안
     *
     * TODO(FR-EXP-01, FR-EXP-03): 조항의 쉬운말 설명과 수정 문구를 만들고 근거를 첨부한다. 이용권은 차감하지 않는다(TC-CRD-009).
     * 1. 입력 검증: clauseId, tone. 해당 조항이 속한 계약이 분석 완료여야 한다(TC-EXP-008, ANALYSIS_REQUIRED).
     * 2. 권한/소유자 확인: clauses.contract_id → contracts.user_id. 아니면 CLAUSE_NOT_FOUND(404).
     * 3. DB/외부 호출: 동일 조항 재생성은 3회 제한(TC-EXP-010, REGENERATE_LIMIT).
     *    마스킹 텍스트만 Claude에 보내고(목표 10s, TC-EXP-013), legal_chunks·kftc_cases RAG 결과를
     *    explanation_citations(source_type, source_id, score)에 저장한다.
     *    explanations(clause_id, text, suggestion, llm_model, prompt_version, created_at)를 insert한다.
     * 4. 상태 전이: 설명 행 추가. 계약·분석 상태는 바꾸지 않는다.
     * 5. 예외/에러코드: ANALYSIS_REQUIRED, REGENERATE_LIMIT, LLM_TIMEOUT, CLAUSE_NOT_FOUND(404).
     * 6. 반환: 설명·수정 문구·citations. 현재 DTO from()이 인용을 빈 목록으로 넣으므로 매핑을 수정해야 한다.
     *    스펙 미정: tone 허용 값, tone·재생성 횟수 저장 컬럼(explanations에 없음).
     */
    public Explanation create(String owner, long clauseId, String tone) {
        Explanation saved = explanations.save(new Explanation(0, clauseId, owner, tone == null ? "기본" : tone, "", ""));
        log.info("설명 생성 explanationId={} clauseId={} owner={}", saved.id(), clauseId, owner);
        return saved;
    }

    /**
     * FR-EXP-02 근거 첨부, FR-EXP-03 수정 문구 제안
     *
     * TODO(FR-EXP-02, FR-EXP-03): 설명과 인용을 조회한다.
     * 1. 입력 검증: id 필수.
     * 2. 권한/소유자 확인: 설명의 조항이 속한 계약 소유자. 아니면 EXPLANATION_NOT_FOUND(404).
     * 3. DB/외부 호출: explanations와 explanation_citations를 조인한다.
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: EXPLANATION_NOT_FOUND(404).
     * 6. 반환: text, suggestion, citations(source_type, source_id, score).
     */
    public Explanation get(String owner, long id) {
        return explanations.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("EXPLANATION_NOT_FOUND", "설명을 찾을 수 없습니다."));
    }
}
