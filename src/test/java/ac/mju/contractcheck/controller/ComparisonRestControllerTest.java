package ac.mju.contractcheck.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.domain.ClauseDraft;
import ac.mju.contractcheck.domain.ClauseSimilarity;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.RiskResult;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.domain.enums.AnalysisStatus;
import ac.mju.contractcheck.domain.enums.ContractState;
import ac.mju.contractcheck.domain.enums.RiskLevel;
import ac.mju.contractcheck.repository.AnalysisRepository;
import ac.mju.contractcheck.repository.ClauseEmbeddingRepository;
import ac.mju.contractcheck.repository.ClauseRepository;
import ac.mju.contractcheck.repository.ContractRepository;
import ac.mju.contractcheck.service.AuthService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FR-CMP-01 비교 생성·FR-CMP-02 비교 조회 HTTP 계약. H2에는 pgvector가 없어 유사도 조회(ClauseEmbeddingRepository)만 mock하고,
 * 계약·조항·분석은 실제 저장소에 만든다. 유사도 SQL 자체는 ClauseSimilarityPostgresTest가 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComparisonRestControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AuthService auth;

    @Autowired
    ContractRepository contracts;

    @Autowired
    ClauseRepository clauses;

    @Autowired
    AnalysisRepository analyses;

    @MockitoBean
    ClauseEmbeddingRepository embeddings;

    @Test
    @DisplayName("FR-CMP-01 분석을 마친 두 계약서를 비교하면 201과 비교 ID, Location을 돌려준다")
    void createsComparison() throws Exception {
        UserId owner = newOwner();
        long before = analyzedContract(owner, "보증금은 1,000만 원으로 한다.");
        long after = analyzedContract(owner, "보증금은 500만 원으로 한다.");
        long beforeClause = clauses.findByContract(before, owner).getFirst().id();
        long afterClause = clauses.findByContract(after, owner).getFirst().id();
        when(embeddings.findSimilarities(before, after))
                .thenReturn(List.of(new ClauseSimilarity(beforeClause, afterClause, 0.91)));

        mvc.perform(create(owner, before, after))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/comparisons/")))
                .andExpect(jsonPath("$.comparisonId").isNumber());
    }

    @Test
    @DisplayName("FR-CMP-01 같은 계약서끼리 비교하면 400 SAME_CONTRACT다")
    void rejectsSameContract() throws Exception {
        UserId owner = newOwner();
        long contract = analyzedContract(owner, "보증금 조항");

        mvc.perform(create(owner, contract, contract))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_CONTRACT"));
    }

    @Test
    @DisplayName("FR-CMP-01 남의 계약서를 고르면 404 CONTRACT_NOT_FOUND다")
    void rejectsOtherOwnersContract() throws Exception {
        UserId owner = newOwner();
        long mine = analyzedContract(owner, "보증금 조항");
        long theirs = analyzedContract(newOwner(), "보증금 조항");

        mvc.perform(create(owner, mine, theirs))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));
    }

    @Test
    @DisplayName("TC-CMP-008 분석을 마치지 않은 계약서가 있으면 409 ANALYSIS_REQUIRED다")
    void requiresAnalysis() throws Exception {
        UserId owner = newOwner();
        long analyzed = analyzedContract(owner, "보증금 조항");
        long notAnalyzed = contract(owner, "보증금 조항");

        mvc.perform(create(owner, analyzed, notAnalyzed))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ANALYSIS_REQUIRED"));
    }

    @Test
    @DisplayName("FR-AUTH-02·04 로그인하지 않으면 401, CSRF 토큰이 없으면 403이다")
    void requiresLoginAndCsrf() throws Exception {
        mvc.perform(post("/api/v1/comparisons").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"beforeContractId\":1,\"afterContractId\":2}").with(csrf()))
                .andExpect(status().isUnauthorized());

        UserId owner = newOwner();
        mvc.perform(post("/api/v1/comparisons").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"beforeContractId\":1,\"afterContractId\":2}").with(user(owner.principalName())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-CMP-02 비교를 조회하면 요약과 조항 쌍(양쪽 조항 번호·본문·위험 등급, 변경 유형, 유사도, 위험 변화)을 돌려준다")
    void getsComparisonWithPairs() throws Exception {
        UserId owner = newOwner();
        long before = analyzedContract(owner, "보증금은 1,000만 원으로 한다.");
        long after = analyzedContract(owner, "보증금은 500만 원으로 한다.");
        long beforeClause = clauses.findByContract(before, owner).getFirst().id();
        long afterClause = clauses.findByContract(after, owner).getFirst().id();
        when(embeddings.findSimilarities(before, after))
                .thenReturn(List.of(new ClauseSimilarity(beforeClause, afterClause, 0.91)));
        long comparisonId = createdId(mvc.perform(create(owner, before, after)).andReturn());

        mvc.perform(get("/api/v1/comparisons/{id}", comparisonId).with(user(owner.principalName())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparisonId").value(comparisonId))
                .andExpect(jsonPath("$.beforeContractId").value(before))
                .andExpect(jsonPath("$.afterContractId").value(after))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.summary.modified").value(1))
                .andExpect(jsonPath("$.summary.added").value(0))
                .andExpect(jsonPath("$.pairs.length()").value(1))
                .andExpect(jsonPath("$.pairs[0].changeType").value("MODIFIED"))
                .andExpect(jsonPath("$.pairs[0].similarity").value(0.91))
                .andExpect(jsonPath("$.pairs[0].riskDelta").value(0))
                .andExpect(jsonPath("$.pairs[0].before.clauseId").value(beforeClause))
                .andExpect(jsonPath("$.pairs[0].before.no").value(1))
                .andExpect(jsonPath("$.pairs[0].before.text").value("보증금은 1,000만 원으로 한다."))
                .andExpect(jsonPath("$.pairs[0].before.riskLevel").value("CAUTION"))
                .andExpect(jsonPath("$.pairs[0].after.text").value("보증금은 500만 원으로 한다."));
    }

    @Test
    @DisplayName("FR-CMP-02 남의 비교는 404 COMPARISON_NOT_FOUND, 로그인하지 않으면 401이다")
    void getRejectsOtherOwnerAndAnonymous() throws Exception {
        UserId owner = newOwner();
        long before = analyzedContract(owner, "보증금 조항");
        long after = analyzedContract(owner, "월세 조항");
        long comparisonId = createdId(mvc.perform(create(owner, before, after)).andReturn());

        mvc.perform(get("/api/v1/comparisons/{id}", comparisonId).with(user(newOwner().principalName())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPARISON_NOT_FOUND"));
        mvc.perform(get("/api/v1/comparisons/{id}", comparisonId))
                .andExpect(status().isUnauthorized());
    }

    private static long createdId(org.springframework.test.web.servlet.MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private org.springframework.test.web.servlet.RequestBuilder create(UserId owner, long before, long after) {
        return post("/api/v1/comparisons")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"beforeContractId\":" + before + ",\"afterContractId\":" + after + "}")
                .with(user(owner.principalName())).with(csrf());
    }

    private UserId newOwner() {
        String providerUserId = UUID.randomUUID().toString();
        return auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id();
    }

    /** 조항 하나짜리 계약을 만든다(분석 없음). */
    private long contract(UserId owner, String clauseText) {
        long contractId = contracts.save(new Contract(0, owner, "계약", "MONTHLY",
                "contracts/cmp/" + UUID.randomUUID(), ContractState.SPLIT, "", Instant.now(), null)).id();
        clauses.saveAll(contractId, owner, List.of(new ClauseDraft(1, "제1조", clauseText)));
        return contractId;
    }

    /** 조항 하나짜리 계약과, 그 조항을 CAUTION으로 판정한 분석 완료 건을 만든다. */
    private long analyzedContract(UserId owner, String clauseText) {
        long contractId = contract(owner, clauseText);
        long clauseId = clauses.findByContract(contractId, owner).getFirst().id();
        long analysisId = analyses.save(new Analysis(
                0, contractId, owner, AnalysisStatus.ANALYZED, 50, "요약", "test-model", Instant.now())).id();
        analyses.saveResults(analysisId, List.of(new RiskResult(clauseId, true, RiskLevel.CAUTION, "DEPOSIT", 0.7)));
        return contractId;
    }
}
