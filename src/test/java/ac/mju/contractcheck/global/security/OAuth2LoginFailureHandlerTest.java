package ac.mju.contractcheck.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/** FR-AUTH-01 로그인 실패는 프론트 로그인 페이지로 302. 오류 값은 TC-AUTH-003·004 기준이다. */
class OAuth2LoginFailureHandlerTest {

    private final OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler("https://fe.example.com");

    @Test
    @DisplayName("TC-AUTH-003 사용자가 동의를 취소하면 {FE}/login?error=oauth_denied")
    void accessDeniedRedirectsWithOauthDenied() throws Exception {
        MockHttpServletResponse response = fail(new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("https://fe.example.com/login?error=oauth_denied");
    }

    @Test
    @DisplayName("TC-AUTH-004 state 불일치 등 그 밖의 실패는 {FE}/login?error=oauth_failed")
    void otherFailuresRedirectWithOauthFailed() throws Exception {
        MockHttpServletResponse stateMismatch =
                fail(new OAuth2AuthenticationException(new OAuth2Error("authorization_request_not_found")));
        MockHttpServletResponse generic = fail(new BadCredentialsException("bad"));

        assertThat(stateMismatch.getRedirectedUrl()).isEqualTo("https://fe.example.com/login?error=oauth_failed");
        assertThat(generic.getRedirectedUrl()).isEqualTo("https://fe.example.com/login?error=oauth_failed");
    }

    @Test
    @DisplayName("프론트 기준 URL 끝의 슬래시는 중복하지 않는다")
    void trailingSlashIsNormalized() throws Exception {
        OAuth2LoginFailureHandler withSlash = new OAuth2LoginFailureHandler("https://fe.example.com/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        withSlash.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(response.getRedirectedUrl()).isEqualTo("https://fe.example.com/login?error=oauth_denied");
    }

    private MockHttpServletResponse fail(org.springframework.security.core.AuthenticationException exception)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);
        return response;
    }
}
