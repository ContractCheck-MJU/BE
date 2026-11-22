package ac.mju.contractcheck.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.service.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** FR-CRD-01 무료 횟수 조회. 회원은 테스트마다 새로 가입시킨다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreditRestControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AuthService auth;

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
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("GRANT"))
                .andExpect(jsonPath("$.items[0].amount").value(2));
    }

    @Test
    @DisplayName("TC-CRD-012 비회원이 조회하면 401을 응답한다")
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/v1/credits/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private Member signUp() {
        String providerUserId = UUID.randomUUID().toString();
        return auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임"));
    }
}
