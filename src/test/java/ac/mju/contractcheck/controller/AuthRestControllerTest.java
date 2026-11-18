package ac.mju.contractcheck.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** FR-AUTH-02 로그인 상태 조회·로그아웃. 회원은 테스트마다 새로 가입시킨다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRestControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AuthService auth;

    @Test
    @DisplayName("TC-AUTH-016 로그인 상태면 id, nickname, role=USER, provider를 돌려준다")
    void meReturnsCurrentMember() throws Exception {
        Member member = signUp();

        mvc.perform(get("/api/v1/auth/me").with(user(String.valueOf(member.id()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.id()))
                .andExpect(jsonPath("$.nickname").value(member.nickname()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.provider").value("kakao"));
    }

    @Test
    @DisplayName("TC-AUTH-016 비로그인이면 401 UNAUTHORIZED")
    void meWithoutSessionIs401() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("세션은 있으나 users 행이 없으면 회원을 만들어 주지 않고 401 UNAUTHORIZED")
    void meWithoutMemberRowIs401() throws Exception {
        mvc.perform(get("/api/v1/auth/me").with(user("987654321")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("TC-AUTH-011 CSRF 토큰과 함께 로그아웃하면 204")
    void logoutReturnsNoContent() throws Exception {
        Member member = signUp();

        mvc.perform(post("/api/v1/auth/logout").with(user(String.valueOf(member.id()))).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("TC-AUTH-009 CSRF 토큰 없는 로그아웃은 403 CSRF_INVALID")
    void logoutWithoutCsrfIsForbidden() throws Exception {
        Member member = signUp();

        mvc.perform(post("/api/v1/auth/logout").with(user(String.valueOf(member.id()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    private Member signUp() {
        String providerUserId = UUID.randomUUID().toString();
        return auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임"));
    }
}
