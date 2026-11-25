package ac.mju.contractcheck.config;

import ac.mju.contractcheck.global.interceptor.ExternalCallLog;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class InferenceConfigTest {

    @Test
    @DisplayName("평문 HTTP에서 h2c 업그레이드를 시도하지 않는다(uvicorn은 업그레이드 요청을 400으로 거절한다)")
    void doesNotRequestHttp2Upgrade() throws Exception {
        AtomicReference<String> upgrade = new AtomicReference<>("(요청 없음)");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embed", exchange -> {
            upgrade.set(String.valueOf(exchange.getRequestHeaders().getFirst("Upgrade")));
            byte[] body = "{\"vectors\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            InferenceProperties properties = new InferenceProperties(
                    "http://unused.test", url, "key", Duration.ofSeconds(2), 1, Duration.ZERO, 16, 64);

            new InferenceConfig().inferenceEmbedRestClient(properties, new ExternalCallLog(null)).post().uri("/embed")
                    .contentType(MediaType.APPLICATION_JSON).body("{\"texts\":[\"a\"]}").retrieve().toBodilessEntity();

            assertThat(upgrade.get()).isEqualTo("null");
        } finally {
            server.stop(0);
        }
    }
}
