package ac.mju.contractcheck.global.security;

import ac.mju.contractcheck.global.error.ApiExceptionHandler.ApiError;
import ac.mju.contractcheck.global.filter.MdcRequestIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

/**
 * 필터 단계(컨트롤러 앞)에서 나가는 인증·인가 오류를 공통 에러 포맷({@code code, message, traceId})으로 쓴다.
 * 컨트롤러 안에서 나는 예외는 ApiExceptionHandler가 같은 포맷으로 처리한다.
 */
final class ApiErrorWriter {

    private ApiErrorWriter() {
    }

    static void write(ObjectMapper objectMapper, HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(new ApiError(code, message, traceId())));
    }

    private static String traceId() {
        String requestId = MDC.get(MdcRequestIdFilter.MDC_KEY);
        return requestId != null ? requestId : UUID.randomUUID().toString();
    }
}
