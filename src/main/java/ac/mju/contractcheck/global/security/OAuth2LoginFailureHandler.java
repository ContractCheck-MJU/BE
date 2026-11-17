package ac.mju.contractcheck.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * FR-AUTH-01 소셜 로그인 실패는 프론트 로그인 페이지로 302한다. 오류 값은 TC-AUTH-003·004를 따른다.
 * 사용자가 동의를 취소하면 {@code oauth_denied}, 그 밖의 실패는 {@code oauth_failed}다.
 * 로그에는 오류 코드만 남기고 제공자 응답 본문과 토큰은 남기지 않는다.
 */
@Slf4j
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final String ACCESS_DENIED = "access_denied";

    private final String loginUrl;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    public OAuth2LoginFailureHandler(String frontendBaseUrl) {
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        this.loginUrl = base + "/login?error=";
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String errorCode = exception instanceof OAuth2AuthenticationException oauth2Exception
                ? oauth2Exception.getError().getErrorCode()
                : exception.getClass().getSimpleName();
        log.warn("소셜 로그인 실패 error={}", errorCode);
        String reason = ACCESS_DENIED.equals(errorCode) ? "oauth_denied" : "oauth_failed";
        redirectStrategy.sendRedirect(request, response, loginUrl + reason);
    }
}
