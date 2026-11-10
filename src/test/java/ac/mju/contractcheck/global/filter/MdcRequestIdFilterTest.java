package ac.mju.contractcheck.global.filter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MdcRequestIdFilterTest {

    private final MdcRequestIdFilter filter = new MdcRequestIdFilter();

    @Test
    @DisplayName("허용된 X-Request-Id 헤더는 재사용하고 응답과 필터 안의 MDC에 넣는다")
    void reusesSafeIncomingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MdcRequestIdFilter.HEADER_NAME, "req-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.mdcValue).isEqualTo("req-abc-123");
        assertThat(response.getHeader(MdcRequestIdFilter.HEADER_NAME)).isEqualTo("req-abc-123");
        assertThat(MDC.get(MdcRequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("없거나 위험한 헤더는 UUID를 새로 발급한다")
    void generatesWhenMissingOrUnsafe() throws Exception {
        assertThat(MdcRequestIdFilter.resolveRequestId(null)).matches(
                "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        assertThat(MdcRequestIdFilter.resolveRequestId("bad id\nINFO spoof")).doesNotContain(" ");
        assertThat(MdcRequestIdFilter.resolveRequestId("short")).hasSize(36);
    }

    private static final class CapturingChain implements FilterChain {
        private String mdcValue;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            mdcValue = MDC.get(MdcRequestIdFilter.MDC_KEY);
        }
    }
}
