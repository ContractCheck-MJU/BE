package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.repository.AnalysisRepository;
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
public class AnalysisService {
    @NonNull
    private final AnalysisRepository analyses;
    @NonNull
    private final ContractService contracts;


    /**
     * FR-ANL-01 조항 위험 분류
     *
     * TODO(FR-ANL-01): 조항을 배치 분류·임베딩하고, 위험 조항만 Claude에 병렬 질의한 뒤 분석을 비동기로 접수한다.
     * 1. 입력 검증: contracts.id가 존재하고 조항 분할까지 끝났는지 확인한다. 아니면 CONTRACT_NOT_READY(409). 이미 진행 중이면 ANALYSIS_IN_PROGRESS(409).
     * 2. 권한/소유자 확인: contracts.user_id가 현재 사용자인지 본다. 아니면 CONTRACT_NOT_FOUND(404). 소유자 문자열(username)이 아니라 users.id를 쓴다.
     * 3. DB/외부 호출: credit_wallets에서 free_remaining을 먼저, 모자라면 paid_remaining을 차감하고 version으로 낙관적 락을 건다.
     *    credit_ledger에 type=USE, amount=1, ref_type=ANALYSIS, ref_id=analyses.id를 남긴다. 잔여 0이면 CREDIT_INSUFFICIENT(402, TC-CRD-002/004).
     *    동일 계약 재분석은 TC-CRD-006을 따른다. 설명·비교·일정 생성은 차감하지 않는다(TC-CRD-009).
     *    analyses 행을 넣고(status=ANALYZING, model_version, created_at) analysis_id를 즉시 반환한다(폴링).
     *    비동기 파이프라인: clauses.text를 InferenceClient POST /classify 배치(RiskPolicy.confidenceThreshold=0.6, TC-ANL-017),
     *    POST /embed 배치 후 clauses.embedding(VECTOR 1024) 저장, 위험 후보만 legal_chunks·kftc_cases에서
     *    {@code ORDER BY embedding <=> ? LIMIT k}로 RAG(evidenceScoreThreshold=0.5, TC-ANL-018),
     *    위험 조항만 Claude 병렬 호출(마스킹 텍스트만, 제한 30s, LLM_TIMEOUT). RiskLevelResolver로 HIGH/CAUTION/SAFE를 부여하고
     *    risk_results(is_risky, risk_level, risk_type, probability, similar_case_ids)를 저장한다.
     * 4. 상태 전이: 성공 시 analyses.status=ANALYZED와 overall_score·summary 기록. 실패 시 FAILED, credit_ledger type=RESTORE로 복구(TC-CRD-005).
     * 5. 예외/에러코드: INFERENCE_UNAVAILABLE, LLM_TIMEOUT 등 시트 코드. NFR-02/TC-ANL-020: 30조항 60초 이내.
     * 6. 반환: 접수된 Analysis(id, status). 스펙 미정: overall_score·summary 산출 공식, 재분석 시 기존 analyses/risk_results 보존 여부,
     *    FR-UPL-01 업로드 '차감 예약'과 본 단계 USE의 원장 경계, contracts.status(UPLOADED/OCR/SPLIT)와 분석 상태의 동기화,
     *    분석 시작·종료 시각 컬럼(analyses에는 created_at만 있음), risk_type 허용 값 목록.
     */
    public Analysis start(String owner, long contractId) {
        contracts.get(owner, contractId);
        Analysis saved = analyses.save(new Analysis(0, contractId, owner, "ACCEPTED", 0, ""));
        log.info("분석 시작 analysisId={} contractId={} owner={}", saved.id(), contractId, owner);
        return saved;
    }

    /**
     * FR-ANL-02 분석 결과 조회
     *
     * TODO(FR-ANL-02): analyses를 폴링 조회한다.
     * 1. 입력 검증: id 필수.
     * 2. 권한/소유자 확인: analyses.contract_id → contracts.user_id가 현재 사용자. 아니면 ANALYSIS_NOT_FOUND(404).
     * 3. DB/외부 호출: analyses 조회(status, overall_score, summary, model_version, created_at).
     * 4. 상태 전이: 없음(조회 전용).
     * 5. 예외/에러코드: ANALYSIS_NOT_FOUND(404).
     * 6. 반환: 분석 헤더. 조항별 결과는 results()에서 채운다.
     */
    public Analysis get(String owner, long id) {
        return analyses.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("ANALYSIS_NOT_FOUND", "분석 결과를 찾을 수 없습니다."));
    }

    /**
     * FR-ANL-02 분석 결과 조회
     *
     * TODO(FR-ANL-02): 해당 분석의 조항별 위험 결과를 반환한다.
     * 1. 입력 검증: 컨트롤러 경로의 analysisId를 받아야 한다. 현재 시그니처가 ID를 받지 않아 항상 빈 목록이다.
     * 2. 권한/소유자 확인: get()과 동일하게 계약 소유자를 확인한다.
     * 3. DB/외부 호출: risk_results를 analysis_id로 조회하고 clauses(clause_no, title, text)와 조인한다.
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: ANALYSIS_NOT_FOUND(404).
     * 6. 반환: is_risky, risk_level, risk_type, probability, similar_case_ids 목록.
     */
    public List<Object> results() {
        return List.of();
    }
}
