package ac.mju.contractcheck.service;

import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.dto.contract.FileUrl;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.repository.ContractRepository;

import java.time.Instant;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    @NonNull
    private final ContractRepository contracts;

    /*
     * TODO(FR-UPL-04): 개인정보 마스킹. 조항 분할이 끝난 직후, classify/embed/Claude 등 외부 전송 전에 수행한다.
     * 1. 입력 검증: 분할된 조항 텍스트와 OCR 원문을 대상으로 한다.
     * 2. 권한/소유자 확인: 업로드 파이프라인 내부 단계이므로 해당 contracts.user_id 작업 범위에서만 실행한다.
     * 3. DB/외부 호출: 주민등록번호, 계좌, 전화번호, 주소, 성명 등을 마스킹한 결과를 contracts.masked_text에 저장하고, 외부 전송본은 이 텍스트만 사용한다.
     *    원문(ocr_raw, S3 원본)은 별도 보관한다.
     * 4. 상태 전이: 마스킹 완료 후 분석 대기에 들어간다. 별도 status 값은 시트에 없다.
     * 5. 예외/에러코드: 스펙 미정: 마스킹 실패 시 오류 코드.
     * 6. 반환: 없음(파이프라인 내부). 스펙 미정: 마스킹 대상의 구체 정규식·치환 문자,
     *    clauses.text를 마스킹본으로 덮을지 여부(스키마에는 contracts.masked_text만 있음).
     */

    /**
     * FR-UPL-01 계약서 업로드
     *
     * TODO(FR-UPL-01): 파일을 검증해 S3에 올리고 contracts 행을 만든 뒤 OCR을 비동기로 시작한다.
     * 1. 입력 검증: PDF/JPG/PNG, 최대 20MB. 아니면 UNSUPPORTED_FILE_TYPE(415), FILE_TOO_LARGE(413). title 길이(200자) 확인.
     * 2. 권한/소유자 확인: 로그인 사용자(users.id).
     * 3. DB/외부 호출: credit_wallets 잔여(free_remaining+paid_remaining)가 없으면 CREDIT_INSUFFICIENT(402, TC-UPL-004).
     *    스펙 미정: FR-UPL-01 '차감 예약'을 원장에 남길지, 잔액 확인만 할지(실제 USE는 분석 시작, TC-CRD-002).
     *    S3에 SSE로 업로드하고 contracts(user_id, title, contract_type, s3_key, status=UPLOADED, created_at)를 insert한다. 원본 보관 90일.
     * 4. 상태 전이: UPLOADED → 비동기 OCR(FR-UPL-02) 시작.
     * 5. 예외/에러코드: 위 4xx 및 시트 업로드 한도(TC-UPL).
     * 6. 반환: 계약 메타(파일 바이너리 제외). 스펙 미정: contract_type 허용 값.
     */
    public Contract upload(String owner, MultipartFile file, String title, String contractType) {
        String name = file == null ? "" : file.getOriginalFilename();
        Contract saved = contracts.save(new Contract(
                0, owner, title, contractType, "UPLOADED", name == null ? "" : name, Instant.now(), null));
        log.info("계약서 업로드 contractId={} owner={}", saved.id(), owner);
        return saved;
    }

    /**
     * FR-UPL-01 계약서 업로드
     *
     * TODO(FR-UPL-01): 내 계약 목록을 페이지로 조회한다.
     * 1. 입력 검증: page/size는 컨트롤러에서 서비스로 전달한다. 현재는 전체 목록을 가져와 총건수를 잘못 계산한다.
     * 2. 권한/소유자 확인: contracts.user_id = 현재 사용자.
     * 3. DB/외부 호출: created_at 내림차순, OFFSET/LIMIT.
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: 없음(빈 목록 허용).
     * 6. 반환: 목록과 총건수.
     */
    public List<Contract> list(String owner) {
        return contracts.findByOwner(owner);
    }

    /**
     * FR-UPL-01 계약서 업로드
     *
     * TODO(FR-UPL-01): 계약 상세를 조회한다.
     * 1. 입력 검증: id 필수.
     * 2. 권한/소유자 확인: contracts.user_id. 아니면 CONTRACT_NOT_FOUND(404, 존재 여부 비공개).
     * 3. DB/외부 호출: contracts 행. 필요 시 최신 분석 상태를 붙인다.
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: CONTRACT_NOT_FOUND(404).
     * 6. 반환: 메타데이터. 원본 파일은 fileUrl로만 제공한다.
     */
    public Contract get(String owner, long id) {
        return owned(owner, id);
    }

    /**
     * FR-UPL-02 OCR 변환
     *
     * TODO(FR-UPL-02): OCR·조항 분할 진행 상태를 조회하고, 미완료면 파이프라인을 이어간다.
     * 1. 입력 검증: id 필수.
     * 2. 권한/소유자 확인: CONTRACT_NOT_FOUND(404).
     * 3. DB/외부 호출: status=UPLOADED이면 Upstage Document Digitization
     *    (POST https://api.upstage.ai/v1/document-digitization, model=document-parse)을 호출하고, 실패 시 3회 백오프한다.
     *    성공 시 ocr_raw(JSONB) 저장, status=OCR. 이어서 조항 분할(FR-UPL-03)로 clauses(clause_no, title, text)를 insert하고 status=SPLIT.
     *    그다음 FR-UPL-04 마스킹으로 masked_text를 채운다.
     * 4. 상태 전이: UPLOADED → OCR → SPLIT. OCR 실패 시 실패 상태, 예약·차감한 이용권이 있으면 RESTORE.
     * 5. 예외/에러코드: 시트 OCR 실패 코드. 스펙 미정: 실패 시 contracts.status 값(스키마에 FAILED 제약이 없음).
     * 6. 반환: 현재 status. 스펙 미정: 분할 완료 후 contracts.status와 API의 ANALYZING/ANALYZED/FAILED 사용 범위.
     */
    public Contract status(String owner, long id) {
        return owned(owner, id);
    }

    /**
     * FR-UPL-01 계약서 업로드
     *
     * TODO(FR-UPL-01): 원본 열람용 사전 서명 URL을 발급한다. 현재 플레이스홀더 도메인을 교체한다.
     * 1. 입력 검증: id 필수.
     * 2. 권한/소유자 확인: CONTRACT_NOT_FOUND(404).
     * 3. DB/외부 호출: contracts.s3_key로 S3 presigned GET을 만든다. 만료 5분(TC-UPL-017).
     * 4. 상태 전이: 없음.
     * 5. 예외/에러코드: 원본 보관 90일이 지났으면 스펙 미정: 오류 코드.
     * 6. 반환: url, expiresAt.
     */
    public FileUrl fileUrl(String owner, long id) {
        Contract contract = owned(owner, id);
        return new FileUrl("https://example.invalid/contracts/" + contract.id(), Instant.now().plusSeconds(300).toString());
    }

    /**
     * FR-UPL-01 계약서 업로드
     *
     * TODO(FR-UPL-01): 계약과 종속 데이터를 삭제한다. 이용권은 복구하지 않는다(TC-CRD-011).
     * 1. 입력 검증: id 필수.
     * 2. 권한/소유자 확인: CONTRACT_NOT_FOUND(404).
     * 3. DB/외부 호출: S3 객체 삭제 후 contracts 및 FK 종속(clauses, analyses, risk_results, comparisons, explanations, milestones 등)을 삭제한다.
     * 4. 상태 전이: 행 제거. 스키마에 deleted_at이 없다.
     * 5. 예외/에러코드: CONTRACT_NOT_FOUND(404).
     * 6. 반환: 204. 스펙 미정: 물리 삭제 vs 상태 변경, 비교·설명 등 타 테이블 보존 범위.
     */
    public void delete(String owner, long id) {
        owned(owner, id);
        contracts.delete(id);
        log.info("계약서 삭제 contractId={} owner={}", id, owner);
    }

    private Contract owned(String owner, long id) {
        return contracts.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "계약서를 찾을 수 없습니다."));
    }
}
