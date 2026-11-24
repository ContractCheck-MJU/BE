package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.client.InferenceClient;
import ac.mju.contractcheck.client.OcrClient;
import ac.mju.contractcheck.client.StorageClient;
import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.domain.Classification;
import ac.mju.contractcheck.domain.ClauseClassification;
import ac.mju.contractcheck.domain.ClauseText;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.OcrResult;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.domain.enums.AnalysisStatus;
import ac.mju.contractcheck.domain.enums.ContractState;
import ac.mju.contractcheck.repository.ClauseEmbeddingRepository;
import java.util.ArrayList;
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
 * TC-UPL-015 마스킹 전 외부 전송 금지. 업로드 → OCR → 분할 → 분석을 실제 서비스·저장소(H2)로 돌리고,
 * 외부(추론 서버)가 받은 요청 본문과 contracts.masked_text에 원문 개인정보가 없는지 본다.
 * 원본 저장소·OCR·추론 서버·근거 검색·pgvector 쓰기만 가짜이고, 비동기 실행기는 호출한 스레드에서 바로 실행한다.
 */
@SpringBootTest(properties = {"contractcheck.ocr.enabled=true", "contractcheck.ocr.retry-backoff=0s"})
@ActiveProfiles("test")
class PiiMaskingPipelineIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7 계약서".getBytes();
    private static final String TEXT = "제1조(목적) 임대인 홍길동(800101-1234567) 연락처 010-1234-5678 이 임대차 계약을 맺는다.\n"
            + "제2조(보증금) 보증금은 10,000,000원이며 국민은행 123-45-678901 로 보낸다. 소재지는 서울시 마포구 연남로 12, 301호 이다.";
    private static final List<String> LEAKS = List.of("홍길동", "800101", "010-1234", "123-45-678901", "연남로", "301호");

    @Autowired ContractService contracts;
    @Autowired AnalysisService analyses;
    @Autowired ClauseService clauseService;
    @Autowired AuthService auth;
    @MockitoBean StorageClient storage;
    @MockitoBean OcrClient ocr;
    @MockitoBean InferenceClient inference;
    @MockitoBean EvidenceRetriever evidenceRetriever;
    @MockitoBean ClauseEmbeddingRepository embeddings;
    @MockitoBean(name = "ocrExecutor") ThreadPoolTaskExecutor ocrExecutor;
    @MockitoBean(name = "analysisExecutor") ThreadPoolTaskExecutor analysisExecutor;

    private final List<String> sentToExternal = new ArrayList<>();
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
        when(ocr.extract(any(), any())).thenReturn(new OcrResult(TEXT, 1));
        when(inference.classify(any())).thenAnswer(call -> {
            List<ClauseText> requests = call.getArgument(0);
            requests.forEach(request -> sentToExternal.add(request.text()));
            return requests.stream()
                    .map(request -> new ClauseClassification(request.id(), new Classification(false, 0.1), "", "mock-0.1")).toList();
        });
        when(inference.embed(any())).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            sentToExternal.addAll(texts);
            return texts.stream().map(text -> new float[] {0.1f}).toList();
        });
        String providerUserId = UUID.randomUUID().toString();
        owner = auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id();
    }

    @Test
    @DisplayName("TC-UPL-015 업로드→분석까지 외부로 나간 모든 요청 본문과 masked_text에 원문 개인정보가 없다")
    void noPersonalDataLeavesTheServer() {
        Contract uploaded = contracts.upload(owner, new MockMultipartFile("file", "c.pdf", "application/pdf", PDF), "신촌 원룸", "MONTHLY");
        Contract processed = contracts.status(owner, uploaded.id());
        Analysis analysis = analyses.start(owner, uploaded.id());

        assertThat(processed.status()).isEqualTo(ContractState.SPLIT);
        assertThat(analyses.get(owner, analysis.id()).status()).isEqualTo(AnalysisStatus.ANALYZED);
        assertThat(sentToExternal).as("분류와 임베딩이 각각 조항 2개를 받는다").hasSize(4);
        for (String leak : LEAKS) {
            assertThat(sentToExternal).as("외부 요청에 '" + leak + "'이 없어야 한다").noneMatch(body -> body.contains(leak));
            assertThat(processed.maskedText()).as("masked_text에 '" + leak + "'이 없어야 한다").doesNotContain(leak);
        }
        assertThat(sentToExternal).allSatisfy(body -> {
            assertThat(body).doesNotContainPattern("\\d{6}-?[1-4]\\d{6}");
            assertThat(body).doesNotContainPattern("01\\d-?\\d{3,4}-?\\d{4}");
        });
        assertThat(sentToExternal.get(0)).contains("[이름]", "[주민번호]", "[전화번호]");
        assertThat(processed.maskedText()).contains("보증금은 10,000,000원", "[계좌번호]", "[주소]");
    }
}
