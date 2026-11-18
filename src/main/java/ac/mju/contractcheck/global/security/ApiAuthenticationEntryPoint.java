package ac.mju.contractcheck.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

/**
 * FR-AUTH-02 세션이 없거나 만료·위조된 요청은 로그인 페이지로 보내지 않고 401 JSON(code=UNAUTHORIZED)으로 답한다.
 */
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @NonNull
    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ApiErrorWriter.write(objectMapper, response, HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHORIZED", "로그인이 필요합니다.");
    }
}
