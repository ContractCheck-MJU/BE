package ac.mju.contractcheck.service;

import ac.mju.contractcheck.client.StorageClient;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.ContractFileType;
import ac.mju.contractcheck.domain.ContractState;
import ac.mju.contractcheck.domain.ContractUploaded;
import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.PageSlice;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.dto.contract.FileUrl;
import ac.mju.contractcheck.global.error.ApiException;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.global.error.CreditInsufficientException;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.repository.AnalysisRepository;
import ac.mju.contractcheck.repository.ClauseRepository;
import ac.mju.contractcheck.repository.ContractRepository;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    static final String MESSAGE_INTERRUPTED = "처리가 중단되었습니다. 다시 올려 주세요";

    private static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final Duration FILE_URL_VALIDITY = Duration.ofMinutes(5);
    private static final Set<String> ALLOWED_CONTRACT_TYPES = Set.of("MONTHLY", "JEONSE");

    @NonNull
    private final ContractRepository contracts;
    @NonNull
    private final ClauseRepository clauses;
    @NonNull
    private final AnalysisRepository analyses;
    @NonNull
    private final CreditService credits;
    @NonNull
    private final StorageClient storage;
    @NonNull
    private final TransactionTemplate transaction;
    @NonNull
    private final ApplicationEventPublisher events;

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
     * 검증(제목 200자·계약 유형 MONTHLY/JEONSE·20MB·PDF/JPG/PNG 매직바이트)과 이용권 잔여 확인을 통과한 파일만 저장소에 올리고
     * contracts 행(status=UPLOADED)을 만든다. 이용권은 차감하지 않고 잔여만 확인한다(실제 USE는 분석 시작, TC-UPL-001·TC-CRD-002).
     * 저장소 호출은 트랜잭션 밖이며, 행 저장이 실패하면 올린 객체를 지운다.
     * 저장이 끝나면 ContractUploaded 이벤트를 발행하고, OCR·조항 분할은 ContractProcessor가 비동기로 이어 간다(FR-UPL-02).
     *
     * 스펙 미정: 업로드 한도 429 RATE_LIMITED(시간당 10회, TC-UPL-005), 원본 보관 90일 만료 삭제 배치.
     */
    public Contract upload(UserId owner, MultipartFile file, String title, String contractType) {
        validateMetadata(title, contractType);
        byte[] content = read(file);
        if (content.length > MAX_UPLOAD_BYTES) {
            throw new ApiException(413, "FILE_TOO_LARGE", "파일은 20MB 이하여야 합니다.");
        }
        ContractFileType fileType = ContractFileType.detect(content)
                .orElseThrow(() -> new ApiException(415, "UNSUPPORTED_FILE_TYPE", "PDF, JPG, PNG 파일만 올릴 수 있습니다."));
        CreditAccount account = credits.me(owner);
        if (account.freeRemaining() + account.paidRemaining() <= 0) {
            throw new CreditInsufficientException();
        }

        String key = "contracts/" + owner + "/" + UUID.randomUUID();
        storage.put(key, content, fileType.contentType());
        try {
            Contract saved = contracts.save(new Contract(
                    0, owner, title, contractType, key, ContractState.UPLOADED, "", Instant.now(), null));
            log.info("계약서 업로드 contractId={} userId={}", saved.id(), owner);
            events.publishEvent(new ContractUploaded(owner, saved.id()));
            return saved;
        } catch (RuntimeException exception) {
            deleteQuietly(key);
            throw exception;
        }
    }

    /**
     * FR-UPL-01 계약서 업로드
     * 내 계약 목록을 최신순으로 한 페이지 돌려준다(OFFSET/LIMIT, 전체 건수 포함). 빈 목록은 허용한다.
     */
    public PageSlice<Contract> list(UserId owner, int page, int size) {
        if (page < 0 || size < 1) {
            throw new ApiException(400, "VALIDATION_ERROR", "page는 0 이상, size는 1 이상이어야 합니다.");
        }
        return contracts.findPage(owner, page, size);
    }

    /**
     * FR-UPL-01 계약서 업로드
     * 내 계약의 메타데이터를 조회한다. 없는 계약과 타인 계약은 모두 404 CONTRACT_NOT_FOUND다.
     * 원본 파일은 fileUrl로만 제공한다.
     */
    public Contract get(UserId owner, long id) {
        return owned(owner, id);
    }

    /**
     * FR-UPL-02 OCR 변환
     * 계약서의 처리 상태를 돌려준다. 업로드 뒤 OCR·조항 분할은 ContractProcessor가 비동기로 진행하며
     * UPLOADED → OCR → SPLIT 순서로 바뀌고, 실패하면 FAILED와 사유(errorMessage)가 담긴다. 없는 계약과 타인 계약은 404다.
     *
     * TODO(FR-UPL-02): Upstage Document Parse 구현체(운영 기본값인데 아직 없다), OCR 원문(ocr_raw) 보관.
     * 스펙 미정: OCR 원문 저장 위치·형식, 분할 완료 후 contracts.status와 API의 ANALYZING/ANALYZED/FAILED 사용 범위.
     */
    public Contract status(UserId owner, long id) {
        return owned(owner, id);
    }

    /**
     * FR-UPL-01 계약서 업로드
     * 원본 열람용 사전서명 URL을 5분 유효로 발급한다(TC-UPL-017). 없는 계약과 타인 계약은 404다.
     * 스펙 미정: 원본 보관 90일이 지난 계약의 오류 코드.
     */
    public FileUrl fileUrl(UserId owner, long id) {
        Contract contract = owned(owner, id);
        String url = storage.presignGet(contract.s3Key(), FILE_URL_VALIDITY);
        return new FileUrl(url, Instant.now().plus(FILE_URL_VALIDITY).toString());
    }

    /**
     * FR-UPL-01 계약서 업로드
     * 저장소 객체를 먼저 지운 뒤, 한 트랜잭션에서 조항과 contracts 행을 지운다. 쓴 이용권은 복구하지 않는다(TC-CRD-011).
     * 객체 삭제가 실패하면 행을 남겨 다시 시도할 수 있게 한다. 분석이 진행 중이면 409 ANALYSIS_IN_PROGRESS다.
     *
     * TODO(FR-UPL-01): 분석·비교·설명·일정이 영속화되면 DB cascade(V2)로 함께 지워지는지 확인한다.
     * 스펙 미정: 물리 삭제 vs 상태 변경, 비교·설명 등 타 테이블 보존 범위.
     */
    public void delete(UserId owner, long id) {
        Contract contract = owned(owner, id);
        if (analyses.existsInProgress(id)) {
            throw new ConflictException("ANALYSIS_IN_PROGRESS", "분석이 진행 중인 계약서는 삭제할 수 없습니다.");
        }
        storage.delete(contract.s3Key());
        transaction.executeWithoutResult(status -> {
            clauses.deleteByContract(id);
            contracts.delete(id);
        });
        log.info("계약서 삭제 contractId={} userId={}", id, owner);
    }

    /**
     * 올린 지 {@code before}보다 오래됐는데 아직 UPLOADED나 OCR에 머문 계약을 FAILED와 사유로 바꾼다.
     * 서버가 OCR 처리 중에 죽으면 그 계약은 영원히 처리 중으로 보이기 때문에, 사용자가 다시 올릴 수 있게 실패로 알려 준다.
     * 이용권은 분석 단계에서 차감하므로 복구할 것이 없다. 찾은 뒤 그 사이 처리가 끝난 계약은 다시 읽어 건너뛴다.
     */
    public int failStuck(Instant before) {
        Set<ContractState> processing = Set.of(ContractState.UPLOADED, ContractState.OCR);
        int failed = 0;
        for (Contract stuck : contracts.findInStatusBefore(processing, before)) {
            boolean stillProcessing = contracts.findOwned(stuck.id(), stuck.owner())
                    .filter(current -> processing.contains(current.status())).isPresent();
            if (!stillProcessing) {
                continue;
            }
            log.warn("멈춘 계약서 처리 정리 contractId={} 상태={} 업로드={}", stuck.id(), stuck.status(), stuck.createdAt());
            contracts.save(stuck.withStatus(ContractState.FAILED, MESSAGE_INTERRUPTED));
            failed++;
        }
        return failed;
    }

    private void validateMetadata(String title, String contractType) {
        if (title == null || title.isBlank() || title.length() > MAX_TITLE_LENGTH) {
            throw new ApiException(400, "VALIDATION_ERROR", "제목은 1~200자여야 합니다.");
        }
        if (!ALLOWED_CONTRACT_TYPES.contains(contractType)) {
            throw new ApiException(400, "VALIDATION_ERROR", "계약 유형은 MONTHLY 또는 JEONSE여야 합니다.");
        }
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(400, "VALIDATION_ERROR", "파일이 비어 있습니다.");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("업로드 파일을 읽지 못했다", exception);
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException exception) {
            log.warn("업로드 롤백 중 저장소 객체 삭제 실패 key={}", key);
        }
    }

    private Contract owned(UserId owner, long id) {
        return contracts.findOwned(id, owner)
                .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "계약서를 찾을 수 없습니다."));
    }
}
