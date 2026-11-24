package ac.mju.contractcheck.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ac.mju.contractcheck.client.InferenceClient;
import ac.mju.contractcheck.client.InferenceException;
import ac.mju.contractcheck.domain.EvidenceHit;
import ac.mju.contractcheck.repository.EvidenceRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** FR-PRC-01 법령·판례 근거 검색 HTTP 계약. 추론 클라이언트와 pgvector 저장소는 mock한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrecedentRestControllerTest {

    private static final float[] VECTOR = {0.1f, 0.2f};
    private static final EvidenceHit HIT = new EvidenceHit(
            12, 120, "LAW", "민법 제640조(차임연체와 해지)", "제640조",
            "임차인의 차임연체액이 2기의 차임액에 달하는 때", "https://law.go.kr/640", 0.85);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InferenceClient inference;

    @MockitoBean
    EvidenceRepository evidence;

    @Test
    @DisplayName("FR-PRC-01 질의어·유형·k로 법령·판례를 검색하면 문서·청크·점수·출처 URL을 응답한다")
    void searchReturnsMatchedItems() throws Exception {
        when(inference.embed(List.of("차임 연체 해지"))).thenReturn(List.of(VECTOR));
        when(evidence.findSimilar(VECTOR, "LAW", 3)).thenReturn(List.of(HIT));

        mvc.perform(get("/api/v1/precedents/search")
                        .param("q", "차임 연체 해지")
                        .param("type", "LAW")
                        .param("k", "3")
                        .with(user("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].documentId").value(12))
                .andExpect(jsonPath("$.items[0].chunkId").value(120))
                .andExpect(jsonPath("$.items[0].type").value("LAW"))
                .andExpect(jsonPath("$.items[0].title").value("민법 제640조(차임연체와 해지)"))
                .andExpect(jsonPath("$.items[0].ref").value("제640조"))
                .andExpect(jsonPath("$.items[0].text").value("임차인의 차임연체액이 2기의 차임액에 달하는 때"))
                .andExpect(jsonPath("$.items[0].score").value(0.85))
                .andExpect(jsonPath("$.items[0].url").value("https://law.go.kr/640"));
    }

    @Test
    @DisplayName("FR-PRC-01 type과 k를 생략하면 전체 유형(null)과 기본 k=5로 검색한다")
    void searchUsesDefaultLimitFiveAndAllTypes() throws Exception {
        when(inference.embed(List.of("원상복구"))).thenReturn(List.of(VECTOR));
        when(evidence.findSimilar(eq(VECTOR), isNull(), eq(5))).thenReturn(List.of());

        mvc.perform(get("/api/v1/precedents/search").param("q", "원상복구").with(user("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));

        verify(evidence).findSimilar(VECTOR, null, 5);
    }

    @Test
    @DisplayName("FR-PRC-01 검색어가 없거나 k가 3~5 범위를 벗어나거나 유형이 잘못되면 400 VALIDATION_ERROR다")
    void invalidParametersReturnBadRequest() throws Exception {
        mvc.perform(get("/api/v1/precedents/search").with(user("1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/v1/precedents/search").param("q", "   ").with(user("1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/v1/precedents/search").param("q", "보증금").param("k", "2").with(user("1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/v1/precedents/search").param("q", "보증금").param("k", "6").with(user("1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/v1/precedents/search").param("q", "보증금").param("type", "UNKNOWN").with(user("1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-PRC-01 추론 서버 임베딩 호출이 실패하면 503 INFERENCE_UNAVAILABLE을 응답한다")
    void inferenceFailureReturns503() throws Exception {
        when(inference.embed(any())).thenThrow(new InferenceException("http://internal:8000 timeout"));

        mvc.perform(get("/api/v1/precedents/search").param("q", "보증금 반환").with(user("1")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INFERENCE_UNAVAILABLE"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("FR-PRC-01 비회원이 검색하면 401 UNAUTHORIZED를 응답한다")
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/v1/precedents/search").param("q", "보증금"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
