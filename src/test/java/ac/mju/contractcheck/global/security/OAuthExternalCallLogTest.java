package ac.mju.contractcheck.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ac.mju.contractcheck.global.interceptor.CapturedExternalLog;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 실제 {@code oauth2Login()} 흐름에서 토큰 교환과 사용자 정보 조회가 외부 호출 기록에 남는지 확인한다.
 * 제공자는 로컬 HTTP 서버라 실제 카카오는 부르지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuthExternalCallLogTest {

    private static final HttpServer PROVIDER = start();
    private static final String PROVIDER_ID = UUID.randomUUID().toString();

    @Autowired
    MockMvc mvc;

    @DynamicPropertySource
    static void provider(DynamicPropertyRegistry registry) {
        // 로그인이 남기는 세션·회원이 다른 테스트에 섞이지 않게 전용 DB를 쓴다.
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:oauth_call_log;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        String base = "http://127.0.0.1:" + PROVIDER.getAddress().getPort();
        registry.add("spring.security.oauth2.client.provider.kakao.token-uri", () -> base + "/oauth/token");
        registry.add("spring.security.oauth2.client.provider.kakao.user-info-uri", () -> base + "/v2/user/me");
    }

    @AfterAll
    static void stop() {
        PROVIDER.stop(0);
    }

    @Test
    @DisplayName("FR-AUTH-01 소셜 로그인의 토큰 교환과 사용자 정보 조회는 본문 없이 호출 기록에 남는다")
    void loginCallsAreLoggedWithoutBodies() throws Exception {
        try (CapturedExternalLog logs = new CapturedExternalLog()) {
            MockHttpServletResponse start = mvc.perform(get("/oauth2/authorization/kakao")).andReturn().getResponse();
            String state = URLDecoder.decode(UriComponentsBuilder.fromUriString(start.getRedirectedUrl()).build()
                    .getQueryParams().getFirst("state"), StandardCharsets.UTF_8);
            Cookie session = start.getCookie("SESSION");

            MockHttpServletResponse callback = mvc.perform(get("/login/oauth2/code/kakao")
                    .param("code", "authorization-code").param("state", state).cookie(session)).andReturn().getResponse();

            assertThat(callback.getRedirectedUrl()).doesNotContain("error");
            assertThat(logs.text())
                    .contains("target=oauth-token method=POST", "/oauth/token status=200",
                            "target=oauth-userinfo method=GET", "/v2/user/me status=200")
                    .doesNotContain("authorization-code", "access-token-value", "시험닉네임", "requestBody", "responseBody");
        }
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                exchange.getRequestBody().readAllBytes();
                String json = exchange.getRequestURI().getPath().endsWith("token")
                        ? "{\"access_token\":\"access-token-value\",\"token_type\":\"bearer\",\"expires_in\":100,\"scope\":\"profile_nickname\"}"
                        : "{\"id\":\"" + PROVIDER_ID + "\",\"kakao_account\":{\"profile\":{\"nickname\":\"시험닉네임\"}}}";
                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
