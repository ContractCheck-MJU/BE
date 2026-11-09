package ac.mju.contractcheck.global.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
    @DisplayName("일반 회원(USER)의 관리자 API 접근은 403")
    void adminApiRequiresAdminRole() throws Exception {
        mvc.perform(post("/api/v1/admin/ingest/laws").with(user("u").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
