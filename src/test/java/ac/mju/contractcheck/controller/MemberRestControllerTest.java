package ac.mju.contractcheck.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ac.mju.contractcheck.domain.Member;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.service.AuthService;
import jakarta.servlet.http.Cookie;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** FR-AUTH-03 내 정보 조회·수정. 회원은 테스트마다 새로 가입시킨다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberRestControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AuthService auth;
    @Autowired
    FindByIndexNameSessionRepository<? extends Session> sessions;

    @Test
    @DisplayName("TC-AUTH-013 role 필드를 보내도 무시되고 닉네임만 바뀐다")
    void patchIgnoresRoleField() throws Exception {
        Member member = signUp();
        String username = String.valueOf(member.id());

        mvc.perform(patch("/api/v1/members/me").with(user(username)).with(csrf())
                        .contentType("application/json")
                        .content("{\"nickname\":\"새닉\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새닉"));

        mvc.perform(get("/api/v1/auth/me").with(user(username)))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("API-MEM-03 탈퇴하면 204와 함께 SESSION 쿠키를 만료시키고 이후 내 정보 조회는 401이다")
    void withdrawExpiresSessionCookie() throws Exception {
        Member member = signUp();
        String username = String.valueOf(member.id());

        mvc.perform(delete("/api/v1/members/me").with(user(username)).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("SESSION=;"),
                        org.hamcrest.Matchers.containsString("Max-Age=0"),
                        org.hamcrest.Matchers.containsString("Path=/"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax"))));

        mvc.perform(get("/api/v1/members/me").with(user(username)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("TC-AUTH-014 실제 세션으로 탈퇴하면 모든 기기의 세션이 지워지고 다른 기기의 쿠키는 401이다")
    void withdrawEndsEverySessionOfTheMember() throws Exception {
        Member member = signUp();
        String username = String.valueOf(member.id());
        String deviceA = loginSession(username);
        String deviceB = loginSession(username);
        assertThat(sessions.findByPrincipalName(username)).hasSize(2);

        mvc.perform(delete("/api/v1/members/me").cookie(new Cookie("SESSION", deviceA)).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        assertThat(sessions.findByPrincipalName(username)).as("응답 뒤에 세션이 다시 저장되지 않는다").isEmpty();
        mvc.perform(get("/api/v1/members/me").cookie(new Cookie("SESSION", deviceB)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/members/me").cookie(new Cookie("SESSION", deviceA)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("API-MEM-03 로그인하지 않은 탈퇴 요청은 401 UNAUTHORIZED다")
    void withdrawRequiresLogin() throws Exception {
        mvc.perform(delete("/api/v1/members/me").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("API-MEM-03 CSRF 토큰이 없으면 403 CSRF_INVALID이고 회원은 그대로다")
    void withdrawRequiresCsrfToken() throws Exception {
        Member member = signUp();
        String username = String.valueOf(member.id());

        mvc.perform(delete("/api/v1/members/me").with(user(username)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        mvc.perform(get("/api/v1/members/me").with(user(username)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("API-MEM-02 닉네임이 50자를 넘으면 400 VALIDATION_ERROR를 일으킨다")
    void patchRejectsTooLongNickname() throws Exception {
        Member member = signUp();

        mvc.perform(patch("/api/v1/members/me").with(user(String.valueOf(member.id()))).with(csrf())
                        .contentType("application/json")
                        .content("{\"nickname\":\"" + "가".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("API-MEM-01 내 정보 조회는 id, email, nickname, provider, createdAt이어야 한다")
    void getReturnsProfile() throws Exception {
        Member member = signUp();

        mvc.perform(get("/api/v1/members/me").with(user(String.valueOf(member.id()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.id()))
                .andExpect(jsonPath("$.email").value(member.email()))
                .andExpect(jsonPath("$.provider").value("kakao"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    private Member signUp() {
        String providerUserId = UUID.randomUUID().toString();
        return auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임"));
    }

    /** 실제 세션 저장소에 로그인 상태 세션을 만들고 SESSION 쿠키 값(Base64 세션 ID)을 돌려준다. */
    private String loginSession(String username) {
        Session session = sessions.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
        saveSession(session);
        return Base64.getEncoder().encodeToString(session.getId().getBytes());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void saveSession(Session session) {
        ((FindByIndexNameSessionRepository) sessions).save(session);
    }
}
