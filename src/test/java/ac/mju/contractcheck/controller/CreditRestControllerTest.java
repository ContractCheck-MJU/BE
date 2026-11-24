package ac.mju.contractcheck.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.service.AuthService;
import ac.mju.contractcheck.service.CreditService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** FR-CRD-01 무료 횟수 조회, FR-CRD-02 이용권 원장 페이지 조회. 회원은 테스트마다 새로 가입시킨다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreditRestControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AuthService auth;

    @Autowired
    CreditService credits;

    @Test
    @DisplayName("TC-CRD-001 가입 직후에는 freeRemaining이 2, paidRemaining이 0이고 원장에 GRANT(2)가 1건 남는다")
    void newMemberHasTwoFreeCredits() throws Exception {
        String username = String.valueOf(signUp().id());

        mvc.perform(get("/api/v1/credits/me").with(user(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeRemaining").value(2))
                .andExpect(jsonPath("$.paidRemaining").value(0));

        mvc.perform(get("/api/v1/credits/me/ledger").with(user(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("GRANT"))
                .andExpect(jsonPath("$.items[0].amount").value(2))
                .andExpect(jsonPath("$.items[0].refType").value(""))
                .andExpect(jsonPath("$.items[0].refId").value(""));
    }

    @Test
    @DisplayName("FR-CRD-02 원장 목록은 최신순으로 page·size만큼 잘리고 total과 refType·refId를 반환하며 타인 원장은 섞이지 않는다")
    void ledgerReturnsPagedNewestFirstForOwnerOnly() throws Exception {
        Member owner = signUp();
        Member other = signUp();
        credits.use(owner.id(), 10L);
        credits.use(other.id(), 99L);
        credits.use(owner.id(), 20L);
        String username = String.valueOf(owner.id());

        mvc.perform(get("/api/v1/credits/me/ledger").param("page", "0").param("size", "2").with(user(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("USE"))
                .andExpect(jsonPath("$.items[0].amount").value(1))
                .andExpect(jsonPath("$.items[0].refType").value("ANALYSIS"))
                .andExpect(jsonPath("$.items[0].refId").value("20"))
                .andExpect(jsonPath("$.items[1].type").value("USE"))
                .andExpect(jsonPath("$.items[1].refType").value("ANALYSIS"))
                .andExpect(jsonPath("$.items[1].refId").value("10"));

        mvc.perform(get("/api/v1/credits/me/ledger").param("page", "1").param("size", "2").with(user(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("GRANT"))
                .andExpect(jsonPath("$.items[0].amount").value(2))
                .andExpect(jsonPath("$.items[0].refType").value(""))
                .andExpect(jsonPath("$.items[0].refId").value(""));

        mvc.perform(get("/api/v1/credits/me/ledger").param("page", "2").param("size", "2").with(user(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("FR-CRD-02 원장 조회에서 page가 0 미만이거나 size가 1 미만이면 400 VALIDATION_ERROR를 응답한다")
    void ledgerRejectsInvalidPaginationParams() throws Exception {
        String username = String.valueOf(signUp().id());

        mvc.perform(get("/api/v1/credits/me/ledger").param("page", "-1").with(user(username)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/v1/credits/me/ledger").param("size", "0").with(user(username)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("TC-CRD-012 비회원이 조회하면 401을 응답한다")
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/v1/credits/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(get("/api/v1/credits/me/ledger"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private Member signUp() {
        String providerUserId = UUID.randomUUID().toString();
        return auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임"));
    }
}
