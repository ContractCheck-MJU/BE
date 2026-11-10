package ac.mju.contractcheck.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 상관 ID를 MDC에 넣는다. Micrometer Tracing 의존성은 추가하지 않는다.
 * 헤더 {@code X-Request-Id}가 허용 형식이면 재사용하고, 없으면 UUID를 발급한다.
 * 등록은 {@code config.LoggingConfig}에서만 한다.
 */
public class MdcRequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER_NAME));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    static String resolveRequestId(String incoming) {
        if (incoming != null && isSafe(incoming)) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    private static boolean isSafe(String value) {
        int length = value.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char ch = value.charAt(i);
            boolean allowed = (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '.' || ch == '_' || ch == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
