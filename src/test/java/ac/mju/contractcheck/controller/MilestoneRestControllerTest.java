package ac.mju.contractcheck.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ac.mju.contractcheck.client.StorageClient;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.service.AuthService;
import ac.mju.contractcheck.service.ContractService;
import ac.mju.contractcheck.service.CreditService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** FR-MIL-01·02 일정 생성·재생성·구간 조회·CRUD HTTP 계약. 저장소(SeaweedFS)는 mock한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MilestoneRestControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AuthService auth;

    @Autowired
    ContractService contracts;

    @Autowired
    CreditService credits;

    @MockitoBean
    StorageClient storage;

    @Test
    @DisplayName("TC-MIL-004·TC-CRD-009 자동 생성은 5건을 만들고 이용권은 차감하지 않으며 재생성 시 수동 일정은 보존한다")
    void generateAndRegeneratePreservesManualMilestonesWithoutUsingCredits() throws Exception {
        UserId owner = newOwner();
        Contract contract = uploadContract(owner);
        String username = owner.principalName();

        mvc.perform(post("/api/v1/contracts/{id}/milestones/generate", contract.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractDate":"2026-04-01","balanceDate":"2026-04-20","moveInDate":"2026-05-01","endDate":"2027-05-01"}
                                """)
                        .with(user(username)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[3].type").value("RENEWAL_NOTICE"))
                .andExpect(jsonPath("$.items[3].dueDate").value("2027-03-01"));

        assertThat(credits.me(owner).freeRemaining()).isEqualTo(2);

        mvc.perform(post("/api/v1/milestones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractId":%d,"type":"CUSTOM","title":"전입신고","dueDate":"2026-05-02","remindDays":3}
                                """.formatted(contract.id()))
                        .with(user(username)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CUSTOM"));

        mvc.perform(post("/api/v1/contracts/{id}/milestones/generate", contract.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractDate":"2026-04-02","balanceDate":"2026-04-21","moveInDate":"2026-05-05","endDate":"2027-05-05"}
                                """)
                        .with(user(username)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5));

        mvc.perform(get("/api/v1/milestones").with(user(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(6));

        mvc.perform(get("/api/v1/milestones")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-05")
                        .with(user(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("전입신고"))
                .andExpect(jsonPath("$.items[1].type").value("MOVE_IN"));
    }

    @Test
    @DisplayName("TC-MIL-006 타인 계약에 수동 일정을 추가하면 404이고 contractId가 없으면 400이다")
    void createRejectsMissingOrOtherContract() throws Exception {
        UserId owner = newOwner();
        UserId stranger = newOwner();
        Contract contract = uploadContract(owner);

        mvc.perform(post("/api/v1/milestones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractId":%d,"type":"CUSTOM","title":"타인 계약","dueDate":"2026-05-02","remindDays":3}
                                """.formatted(contract.id()))
                        .with(user(stranger.principalName())).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));

        mvc.perform(post("/api/v1/milestones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractId":0,"type":"CUSTOM","title":"계약 없음","dueDate":"2026-05-02","remindDays":3}
                                """)
                        .with(user(owner.principalName())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-MIL-02 본인 일정 수정·삭제와 타인 일정 404 은닉 및 비회원 401을 검증한다")
    void updateDeleteAndAuthorization() throws Exception {
        UserId owner = newOwner();
        UserId stranger = newOwner();
        Contract contract = uploadContract(owner);

        String createdBody = mvc.perform(post("/api/v1/milestones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contractId":%d,"type":"CUSTOM","title":"확정일자","dueDate":"2026-05-03","remindDays":2}
                                """.formatted(contract.id()))
                        .with(user(owner.principalName())).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long milestoneId = Long.parseLong(createdBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(patch("/api/v1/milestones/{id}", milestoneId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"done\":true}")
                        .with(user(owner.principalName())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("확정일자"))
                .andExpect(jsonPath("$.done").value(true));

        mvc.perform(patch("/api/v1/milestones/{id}", milestoneId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"done\":false}")
                        .with(user(stranger.principalName())).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MILESTONE_NOT_FOUND"));

        mvc.perform(delete("/api/v1/milestones/{id}", milestoneId)
                        .with(user(owner.principalName())).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/milestones"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private UserId newOwner() {
        String providerUserId = UUID.randomUUID().toString();
        return auth.signIn(
                new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id();
    }

    private Contract uploadContract(UserId owner) {
        byte[] content = new byte[1024];
        content[0] = '%';
        content[1] = 'P';
        content[2] = 'D';
        content[3] = 'F';
        MockMultipartFile file = new MockMultipartFile("file", "lease.pdf", "application/pdf", content);
        return contracts.upload(owner, file, "신촌 원룸", "MONTHLY");
    }
}
