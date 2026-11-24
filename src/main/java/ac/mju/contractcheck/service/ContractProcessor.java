package ac.mju.contractcheck.service;

import ac.mju.contractcheck.client.OcrClient;
import ac.mju.contractcheck.client.OcrException;
import ac.mju.contractcheck.client.OcrTimeoutException;
import ac.mju.contractcheck.client.StorageClient;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.enums.ContractFileType;
import ac.mju.contractcheck.domain.enums.ContractState;
import ac.mju.contractcheck.domain.ContractUploaded;
import ac.mju.contractcheck.domain.OcrResult;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.repository.ContractRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * FR-UPL-02 OCR 변환. 업로드가 끝나면({@link ContractUploaded}) 비동기로 원본을 읽어 OCR을 돌리고, 조항으로 나눠 저장해
 * contracts.status를 {@code UPLOADED → OCR → SPLIT}으로 옮긴다. 어느 단계든 실패하면 {@code FAILED}와 사용자에게 보일 사유를 남긴다.
 *
 * <p>OCR·저장소 호출은 트랜잭션 밖이고, 조항 저장과 SPLIT 전환만 한 트랜잭션으로 묶는다. OCR이 실패하면 설정한 횟수까지
 * 지수 백오프로 다시 시도한다(TC-UPL-007·008). 이용권은 분석 단계에서 차감하므로 여기서는 건드리지 않는다.
 * {@code contractcheck.ocr.enabled=false}이면 등록되지 않는다(테스트가 업로드마다 OCR을 돌리지 않도록).
 *
 * <p>TODO(FR-UPL-04): 개인정보 마스킹. 지금은 OCR 원문으로 조항을 나눠 저장하고 contracts.masked_text는 비워 둔다.
 * 스펙 미정: OCR 원문(ocr_raw JSONB) 보관 여부와 형식, Upstage 구현체(운영 기본값인데 없어 운영에서는 이 처리가 FAILED로 끝난다),
 * 서버가 중간에 죽어 OCR 상태로 남은 계약의 자동 재처리(지금은 StuckJobRecovery가 FAILED로만 정리한다).
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "contractcheck.ocr", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ContractProcessor {

    static final String MESSAGE_NO_TEXT = "계약서 텍스트를 찾지 못했습니다";
    static final String MESSAGE_TIMEOUT = "OCR 처리 시간 초과";
    static final String MESSAGE_OCR_FAILED = "OCR 처리에 실패했습니다";
    static final String MESSAGE_UNAVAILABLE = "OCR 서비스를 사용할 수 없습니다";
    static final String MESSAGE_BUSY = "요청이 많아 처리하지 못했습니다. 잠시 뒤 다시 올려 주세요";
    static final String MESSAGE_FAILED = "계약서 처리에 실패했습니다";

    private final ContractRepository contracts;
    private final StorageClient storage;
    private final ObjectProvider<OcrClient> ocrProvider;
    private final ClauseService clauseService;
    private final TransactionTemplate transaction;
    private final Executor executor;
    private final int maxAttempts;
    private final Duration retryBackoff;

    public ContractProcessor(
            @NonNull ContractRepository contracts,
            @NonNull StorageClient storage,
            @NonNull ObjectProvider<OcrClient> ocrProvider,
            @NonNull ClauseService clauseService,
            @NonNull TransactionTemplate transaction,
            @NonNull @Qualifier("ocrExecutor") Executor executor,
            @Value("${contractcheck.ocr.max-attempts:3}") int maxAttempts,
            @Value("${contractcheck.ocr.retry-backoff:1s}") Duration retryBackoff) {
        this.contracts = contracts;
        this.storage = storage;
        this.ocrProvider = ocrProvider;
        this.clauseService = clauseService;
        this.transaction = transaction;
        this.executor = executor;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoff = retryBackoff;
    }

    /** 업로드 요청 스레드에서는 작업만 접수한다. 대기열이 가득 차 접수하지 못하면 그 계약을 FAILED로 바꾼다. */
    @EventListener
    public void onUploaded(ContractUploaded event) {
        try {
            executor.execute(() -> process(event.owner(), event.contractId()));
        } catch (RuntimeException exception) {
            log.warn("계약서 처리 접수 실패 contractId={}", event.contractId());
            fail(event.owner(), event.contractId(), MESSAGE_BUSY);
        }
    }

    /** 처리 스레드의 본체. 어떤 예외도 밖으로 내보내지 않고 FAILED와 사유로 끝낸다. */
    void process(UserId owner, long contractId) {
        Optional<Contract> found = contracts.findOwned(contractId, owner);
        if (found.isEmpty() || found.get().status() != ContractState.UPLOADED) {
            return; // 그 사이 삭제됐거나 이미 처리 중·완료다.
        }
        try {
            OcrClient ocr = ocrProvider.getIfAvailable();
            if (ocr == null) {
                fail(owner, contractId, MESSAGE_UNAVAILABLE);
                return;
            }
            Contract contract = found.get();
            update(owner, contractId, ContractState.OCR, null);
            byte[] content = storage.get(contract.s3Key());
            ContractFileType type = ContractFileType.detect(content).orElse(null);
            if (type == null) {
                fail(owner, contractId, MESSAGE_FAILED);
                return;
            }
            OcrResult result = extractWithRetry(ocr, content, type, contractId);
            if (result.empty()) {
                fail(owner, contractId, MESSAGE_NO_TEXT);
                return;
            }
            transaction.executeWithoutResult(status -> {
                clauseService.split(owner, contractId, result.text());
                update(owner, contractId, ContractState.SPLIT, null);
            });
            log.info("계약서 처리 완료 contractId={} pages={}", contractId, result.pageCount());
        } catch (OcrTimeoutException exception) {
            log.warn("OCR 시간 초과 contractId={}", contractId);
            fail(owner, contractId, MESSAGE_TIMEOUT);
        } catch (OcrException exception) {
            log.warn("OCR 실패 contractId={} 원인={}", contractId, exception.getMessage());
            fail(owner, contractId, MESSAGE_OCR_FAILED);
        } catch (RuntimeException exception) {
            log.warn("계약서 처리 실패 contractId={} 원인={}", contractId, exception.toString());
            fail(owner, contractId, MESSAGE_FAILED);
        }
    }

    /** OCR이 실패하면 지수 백오프로 최대 maxAttempts번 시도한다. 글자가 없는 결과는 실패가 아니므로 다시 시도하지 않는다. */
    private OcrResult extractWithRetry(OcrClient ocr, byte[] content, ContractFileType type, long contractId) {
        OcrException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return ocr.extract(content, type);
            } catch (OcrException exception) {
                last = exception;
                log.info("OCR 재시도 contractId={} attempt={}/{}", contractId, attempt, maxAttempts);
                if (attempt < maxAttempts) {
                    pause(retryBackoff.multipliedBy(1L << (attempt - 1)));
                }
            }
        }
        throw last;
    }

    private static void pause(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OcrException("OCR 처리가 중단되었습니다.", exception);
        }
    }

    private void update(UserId owner, long contractId, ContractState state, String errorMessage) {
        contracts.findOwned(contractId, owner)
                .ifPresent(contract -> contracts.save(contract.withStatus(state, errorMessage)));
    }

    /** FAILED와 사유를 남긴다. 저장 자체가 실패해도 예외를 내보내지 않는다(처리 스레드의 마지막 방어선). */
    private void fail(UserId owner, long contractId, String message) {
        try {
            update(owner, contractId, ContractState.FAILED, message);
        } catch (RuntimeException exception) {
            log.error("계약서 FAILED 저장 실패 contractId={}", contractId, exception);
        }
    }
}
