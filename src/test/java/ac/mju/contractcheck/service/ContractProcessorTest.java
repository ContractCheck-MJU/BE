package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.client.OcrClient;
import ac.mju.contractcheck.client.OcrException;
import ac.mju.contractcheck.client.OcrTimeoutException;
import ac.mju.contractcheck.client.StorageClient;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.ContractFileType;
import ac.mju.contractcheck.domain.ContractUploaded;
import ac.mju.contractcheck.domain.OcrResult;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.repository.ContractRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 업로드 뒤 OCR → 조항 분할 → 상태 전이(UPLOADED → OCR → SPLIT, 실패는 FAILED와 사유)를 외부 서비스를 가짜로 두고 확인한다.
 * 비동기 실행기는 호출한 스레드에서 바로 실행하고 재시도 대기는 0이라 결과가 결정적이다.
 */
class ContractProcessorTest {

    private static final UserId OWNER = new UserId(7);
    private static final long ID = 3;
    private static final byte[] PDF = "%PDF-1.7 test".getBytes();

    private final ContractRepository contracts = mock(ContractRepository.class);
    private final StorageClient storage = mock(StorageClient.class);
    private final OcrClient ocr = mock(OcrClient.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<OcrClient> ocrProvider = mock(ObjectProvider.class);
    private final ClauseService clauseService = mock(ClauseService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private Executor executor = Runnable::run;
    private ContractProcessor processor;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(ocrProvider.getIfAvailable()).thenReturn(ocr);
        when(contracts.findOwned(ID, OWNER)).thenAnswer(call -> Optional.of(stored));
        when(contracts.save(any())).thenAnswer(call -> {
            stored = call.getArgument(0);
            return stored;
        });
        when(storage.get("contracts/7/k")).thenReturn(PDF);
        processor = newProcessor();
    }

    private Contract stored = new Contract(ID, OWNER, "계약", "MONTHLY", "contracts/7/k", "UPLOADED", "", Instant.now(), null);

    private ContractProcessor newProcessor() {
        return new ContractProcessor(contracts, storage, ocrProvider, clauseService,
                new TransactionTemplate(transactionManager), executor, 3, Duration.ZERO);
    }

    private void upload() {
        processor.onUploaded(new ContractUploaded(OWNER, ID));
    }

    @Test
    @DisplayName("TC-UPL-006 정상: OCR 시작 표시 → 원본 읽기 → 텍스트 추출 → 조항 분할 → SPLIT 순서다")
    void successFlowEndsInSplit() {
        when(ocr.extract(PDF, ContractFileType.PDF)).thenReturn(new OcrResult("제1조(목적) 임대차에 관한 계약이다.", 1));

        upload();

        InOrder order = inOrder(contracts, storage, ocr, clauseService);
        ArgumentCaptor<Contract> saved = ArgumentCaptor.forClass(Contract.class);
        order.verify(contracts).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo("OCR");
        order.verify(storage).get("contracts/7/k");
        order.verify(ocr).extract(PDF, ContractFileType.PDF);
        order.verify(clauseService).split(OWNER, ID, "제1조(목적) 임대차에 관한 계약이다.");
        order.verify(contracts).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo("SPLIT");
        assertThat(saved.getValue().errorMessage()).isNull();
    }

    @Test
    @DisplayName("TC-UPL-009 텍스트가 없으면 FAILED이고 사유를 남기며 재시도·분할을 하지 않는다")
    void emptyTextFails() {
        when(ocr.extract(any(), any())).thenReturn(new OcrResult("  ", 1));

        upload();

        assertThat(stored.status()).isEqualTo("FAILED");
        assertThat(stored.errorMessage()).isEqualTo("계약서 텍스트를 찾지 못했습니다");
        verify(ocr, times(1)).extract(any(), any());
        verify(clauseService, never()).split(any(), anyLong(), any());
    }

    @Test
    @DisplayName("TC-UPL-007 OCR이 계속 시간 초과면 3회 시도 뒤 FAILED이고 사유는 '처리 시간 초과'다")
    void timeoutAfterRetriesFails() {
        when(ocr.extract(any(), any())).thenThrow(new OcrTimeoutException("OCR 처리 시간 초과"));

        upload();

        verify(ocr, times(3)).extract(any(), any());
        assertThat(stored.status()).isEqualTo("FAILED");
        assertThat(stored.errorMessage()).isEqualTo("OCR 처리 시간 초과");
        verify(clauseService, never()).split(any(), anyLong(), any());
    }

    @Test
    @DisplayName("TC-UPL-008 한 번 실패해도 재시도로 성공하면 SPLIT까지 진행한다")
    void retrySucceeds() {
        when(ocr.extract(any(), any()))
                .thenThrow(new OcrException("OCR 처리에 실패했습니다: 500"))
                .thenReturn(new OcrResult("제1조 본문", 1));

        upload();

        verify(ocr, times(2)).extract(any(), any());
        assertThat(stored.status()).isEqualTo("SPLIT");
    }

    @Test
    @DisplayName("시간 초과가 아닌 OCR 실패가 계속되면 FAILED이고 일반 실패 사유를 남긴다")
    void persistentOcrFailure() {
        when(ocr.extract(any(), any())).thenThrow(new OcrException("OCR 처리에 실패했습니다: boom"));

        upload();

        assertThat(stored.status()).isEqualTo("FAILED");
        assertThat(stored.errorMessage()).isEqualTo("OCR 처리에 실패했습니다");
    }

    @Test
    @DisplayName("조항 분할 저장이 실패하면 SPLIT으로 바뀌지 않고 FAILED가 된다")
    void splitFailureMarksFailed() {
        when(ocr.extract(any(), any())).thenReturn(new OcrResult("본문", 1));
        org.mockito.Mockito.doThrow(new IllegalStateException("저장 실패")).when(clauseService).split(any(), anyLong(), any());

        upload();

        assertThat(stored.status()).isEqualTo("FAILED");
        assertThat(stored.errorMessage()).isEqualTo("계약서 처리에 실패했습니다");
    }

    @Test
    @DisplayName("OCR 구현체가 없으면(운영의 upstage 미구현) 앱은 뜨고 해당 계약만 FAILED가 된다")
    void missingOcrClientFails() {
        when(ocrProvider.getIfAvailable()).thenReturn(null);

        upload();

        assertThat(stored.status()).isEqualTo("FAILED");
        assertThat(stored.errorMessage()).isEqualTo("OCR 서비스를 사용할 수 없습니다");
        verify(storage, never()).get(any());
    }

    @Test
    @DisplayName("이미 처리 중이거나 끝난 계약은 다시 처리하지 않는다")
    void nonUploadedContractIsSkipped() {
        stored = stored.withStatus("SPLIT", null);

        upload();

        verify(ocr, never()).extract(any(), any());
        verify(contracts, never()).save(any());
    }

    @Test
    @DisplayName("처리 중 계약이 삭제됐으면 아무것도 하지 않는다")
    void deletedContractIsIgnored() {
        when(contracts.findOwned(ID, OWNER)).thenReturn(Optional.empty());

        upload();

        verify(storage, never()).get(any());
        verify(contracts, never()).save(any());
    }

    @Test
    @DisplayName("작업 대기열이 가득 차면 FAILED로 바꾸고 사유를 남긴다")
    void rejectedExecutionFails() {
        executor = command -> {
            throw new RejectedExecutionException("가득 참");
        };
        processor = newProcessor();

        upload();

        assertThat(stored.status()).isEqualTo("FAILED");
        assertThat(stored.errorMessage()).isEqualTo("요청이 많아 처리하지 못했습니다. 잠시 뒤 다시 올려 주세요");
        verify(ocr, never()).extract(any(), any());
    }

    @Test
    @DisplayName("PDF·이미지 종류는 원본의 매직바이트로 판별해 OCR에 넘긴다")
    void fileTypeIsDetectedFromContent() {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
        when(storage.get("contracts/7/k")).thenReturn(png);
        when(ocr.extract(eq(png), eq(ContractFileType.PNG))).thenReturn(new OcrResult("제1조 본문", 1));

        upload();

        assertThat(stored.status()).isEqualTo("SPLIT");
    }
}
