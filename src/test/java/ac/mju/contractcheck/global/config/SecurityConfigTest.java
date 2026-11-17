package ac.mju.contractcheck.global.config;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("CSRF 토큰 발급은 비로그인도 가능")
    void csrfEndpointIsPublic() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty());
        // 참고: spring-security-test의 MockMvc 지원이 CSRF 저장소를 테스트용으로 감싸므로
        // XSRF-TOKEN 쿠키/헤더 이름은 여기서 검증하지 않는다. 실제 서버(브라우저)에서 확인.
    }

    @Test
    @DisplayName("미인증 API 요청은 리다이렉트가 아니라 401")
    void unauthenticatedApiIs401() throws Exception {
        mvc.perform(get("/api/v1/contracts")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인해도 CSRF 토큰 없는 변경 요청은 403")
    void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/contracts").with(user("u"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-AUTH-01 소셜 로그인 시작은 제공자 인가 페이지로 302")
    void oauth2AuthorizationRedirectsToProvider() throws Exception {
        mvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", startsWith("https://kauth.kakao.com/oauth/authorize")));
    }

    @Test
    @DisplayName("FR-AUTH-01 oauth2Login이 생성 로그인 페이지(/login)를 공개하지 않는다")
    void generatedLoginPageIsNotExposed() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-AUTH-003 인가 시작 뒤 사용자가 동의를 취소한 콜백은 로그인 세션 없이 {FE}/login?error=oauth_denied로 302")
    void deniedCallbackRedirectsToFrontendLogin() throws Exception {
        MvcResult started = mvc.perform(get("/oauth2/authorization/kakao")).andReturn();
        String state = UriUtils.decode(
                UriComponentsBuilder.fromUriString(started.getResponse().getHeader("Location"))
                        .build().getQueryParams().getFirst("state"),
                StandardCharsets.UTF_8);

        mvc.perform(get("/login/oauth2/code/kakao")
                        .cookie(started.getResponse().getCookie("SESSION"))
                        .param("error", "access_denied")
                        .param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3000/login?error=oauth_denied"));
    }

    @Test
    @DisplayName("TC-AUTH-004 state가 맞지 않는 콜백은 {FE}/login?error=oauth_failed로 302")
    void stateMismatchCallbackRedirectsToFrontendLogin() throws Exception {
        mvc.perform(get("/login/oauth2/code/kakao").param("code", "c").param("state", "forged"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3000/login?error=oauth_failed"));
    }

    @Test
    @DisplayName("일반 회원(USER)의 관리자 API 접근은 403")
    void adminApiRequiresAdminRole() throws Exception {
        mvc.perform(post("/api/v1/admin/ingest/laws").with(user("u").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
