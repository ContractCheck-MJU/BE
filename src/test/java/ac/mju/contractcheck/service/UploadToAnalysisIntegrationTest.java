package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.client.OcrClient;
import ac.mju.contractcheck.client.OcrTimeoutException;
import ac.mju.contractcheck.client.StorageClient;
import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.domain.AnalysisStatus;
import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.ClauseRisk;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.OcrResult;
import ac.mju.contractcheck.domain.RiskAssessment;
import ac.mju.contractcheck.domain.RiskLevel;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.repository.ClauseEmbeddingRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 업로드부터 분석까지가 한 줄로 이어지는지 실제 서비스·저장소(H2)로 확인한다(FR-UPL-01~03, FR-ANL-01).
 * 원본 저장소·OCR 구현체·추론 서버·pgvector 쓰기만 가짜이고, 비동기 실행기는 호출한 스레드에서 바로 실행한다.
 */
@SpringBootTest(properties = {"contractcheck.ocr.enabled=true", "contractcheck.ocr.retry-backoff=0s"})
@ActiveProfiles("test")
class UploadToAnalysisIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7 계약서".getBytes();
    private static final String TEXT = "제1조(목적) 이 계약은 임대차에 관한 것이다.\n제2조(보증금) 보증금은 1천만 원으로 한다.";

    @Autowired ContractService contracts;
    @Autowired ClauseService clauseService;
    @Autowired AnalysisService analyses;
    @Autowired CreditService credits;
    @Autowired AuthService auth;
    @MockitoBean StorageClient storage;
    @MockitoBean OcrClient ocr;
    @MockitoBean ClauseRiskService riskService;
    @MockitoBean ClauseEmbeddingRepository embeddings;
    @MockitoBean(name = "ocrExecutor") ThreadPoolTaskExecutor ocrExecutor;
    @MockitoBean(name = "analysisExecutor") ThreadPoolTaskExecutor analysisExecutor;

    private UserId owner;

    @BeforeEach
    void setUp() {
        for (ThreadPoolTaskExecutor executor : new ThreadPoolTaskExecutor[] {ocrExecutor, analysisExecutor}) {
            doAnswer(call -> {
                ((Runnable) call.getArgument(0)).run();
                return null;
            }).when(executor).execute(any());
        }
        when(storage.get(any())).thenReturn(PDF);
        String providerUserId = UUID.randomUUID().toString();
        owner = auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id();
    }

    private Contract upload() {
        return contracts.upload(owner, new MockMultipartFile("file", "c.pdf", "application/pdf", PDF), "신촌 원룸", "MONTHLY");
    }

    @Test
    @DisplayName("TC-UPL-006 업로드하면 OCR → 조항 분할을 거쳐 SPLIT이 되고 조항이 저장되며, 이어서 분석이 끝까지 돈다")
    void uploadFlowsIntoAnalysis() {
        when(ocr.extract(any(), any())).thenReturn(new OcrResult(TEXT, 1));
        when(riskService.assess(any())).thenAnswer(call -> {
            List<Clause> clauses = call.getArgument(0);
            return clauses.stream().map(clause -> new ClauseRisk(clause.id(), null, 0.97,
                    new RiskAssessment(RiskLevel.SAFE, false, false), "mock-0.1", new float[] {0.1f}, List.of())).toList();
        });

        Contract uploaded = upload();

        assertThat(uploaded.status()).as("업로드 응답은 접수 시점의 상태다").isEqualTo("UPLOADED");
        Contract processed = contracts.status(owner, uploaded.id());
        assertThat(processed.status()).isEqualTo("SPLIT");
        assertThat(processed.errorMessage()).isNull();
        assertThat(clauseService.list(owner, uploaded.id())).hasSize(2);
        assertThat(credits.me(owner).freeRemaining()).as("OCR 단계에서는 이용권을 차감하지 않는다").isEqualTo(2);

        Analysis analysis = analyses.start(owner, uploaded.id());

        assertThat(analyses.get(owner, analysis.id()).status()).isEqualTo(AnalysisStatus.ANALYZED);
        assertThat(analyses.results(owner, analysis.id())).hasSize(2);
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-UPL-007 OCR이 계속 시간 초과면 FAILED와 사유가 저장되고 이용권은 그대로이며 분석은 409다")
    void ocrTimeoutFailsAndBlocksAnalysis() {
        when(ocr.extract(any(), any())).thenThrow(new OcrTimeoutException("OCR 처리 시간 초과"));

        Contract uploaded = upload();

        Contract processed = contracts.status(owner, uploaded.id());
        assertThat(processed.status()).isEqualTo("FAILED");
        assertThat(processed.errorMessage()).isEqualTo("OCR 처리 시간 초과");
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(2);
        assertThatThrownBy(() -> analyses.start(owner, uploaded.id()))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo("CONTRACT_NOT_READY"));
    }

    @Test
    @DisplayName("TC-UPL-009 텍스트가 없는 파일은 FAILED와 '텍스트를 찾지 못했습니다' 사유가 된다")
    void emptyTextFails() {
        when(ocr.extract(any(), any())).thenReturn(new OcrResult("", 1));

        Contract uploaded = upload();

        Contract processed = contracts.status(owner, uploaded.id());
        assertThat(processed.status()).isEqualTo("FAILED");
        assertThat(processed.errorMessage()).isEqualTo("계약서 텍스트를 찾지 못했습니다");
        assertThat(clauseService.list(owner, uploaded.id())).isEmpty();
    }
}
