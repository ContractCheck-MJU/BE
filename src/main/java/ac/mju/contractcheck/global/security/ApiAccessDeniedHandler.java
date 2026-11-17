package ac.mju.contractcheck.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import tools.jackson.databind.ObjectMapper;

/**
 * 403 JSON. X-XSRF-TOKEN이 없거나 틀린 변경 요청은 CSRF_INVALID(FR-AUTH-04),
 * 로그인했지만 권한이 부족한 요청(USER → 관리자 API 등)은 FORBIDDEN이다.
 */
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    @NonNull
    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        if (accessDeniedException instanceof CsrfException) {
            ApiErrorWriter.write(objectMapper, response, HttpServletResponse.SC_FORBIDDEN,
                    "CSRF_INVALID", "CSRF 토큰이 없거나 올바르지 않습니다.");
            return;
        }
        ApiErrorWriter.write(objectMapper, response, HttpServletResponse.SC_FORBIDDEN,
                "FORBIDDEN", "접근 권한이 없습니다.");
    }
}
