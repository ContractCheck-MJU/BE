package ac.mju.contractcheck.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ac.mju.contractcheck.global.interceptor.AnthropicCallLoggingInterceptor;
import ac.mju.contractcheck.global.interceptor.CapturedExternalLog;
import ac.mju.contractcheck.global.interceptor.ExternalCallLog;
import ac.mju.contractcheck.repository.PiiRuleRepository;
import ac.mju.contractcheck.service.PiiMaskingService;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 설정이 만드는 외부 클라이언트의 호출이 외부 호출 기록에 남는지 확인한다. 상대는 이 테스트가 띄운 로컬 HTTP 서버라
 * 실제 외부 API는 부르지 않는다.
 */
class ExternalClientLoggingTest {

    private static final String PHONE = "010-0000-0000";

    private HttpServer server;
    private String baseUrl;
    private CapturedExternalLog logs;
    private final AtomicReference<Reply> reply = new AtomicReference<>(new Reply(200, "{\"text\":\"전화 " + PHONE + "\"}"));

    private record Reply(int status, String body) {}

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = reply.get().body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.get().status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        logs = new CapturedExternalLog();
    }

    @AfterEach
    void stop() {
        logs.close();
        server.stop(0);
    }

    private ApplicationContextRunner runner(String profiles) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
                    context.getEnvironment().setActiveProfiles(profiles.split(","));
                })
                .withBean(PiiMaskingService.class, () -> new PiiMaskingService(
                        new PiiRuleRepository(new DefaultResourceLoader(), "classpath:masking/pii-rules.yml")))
                .withUserConfiguration(TossConfig.class, UpstageConfig.class, InferenceConfig.class, StorageConfig.class)
                .withPropertyValues(
                        "contractcheck.toss.base-url=" + baseUrl,
                        "contractcheck.ocr.upstage.base-url=" + baseUrl,
                        "contractcheck.inference.classify-base-url=" + baseUrl,
                        "contractcheck.inference.embed-base-url=" + baseUrl,
                        "contractcheck.inference.api-key=internal-key",
                        "contractcheck.storage.endpoint=" + baseUrl,
                        "contractcheck.storage.region=us-east-1",
                        "contractcheck.storage.access-key=access",
                        "contractcheck.storage.secret-key=secret");
    }

    private static String post(RestClient client, String path) {
        return client.post().uri(path).header("Authorization", "Bearer header-secret")
                .contentType(MediaType.APPLICATION_JSON).body("{\"query\":\"전화 " + PHONE + "\"}")
                .retrieve().body(String.class);
    }

    @ParameterizedTest
    @CsvSource({"tossRestClient, toss", "upstageRestClient, upstage",
            "inferenceClassifyRestClient, inference-classify", "inferenceEmbedRestClient, inference-embed"})
    @DisplayName("RestClient 호출은 대상·상태·가린 본문을 남기고, 호출한 쪽은 응답을 그대로 읽는다")
    void restClientCallsAreLogged(String bean, String target) {
        runner("local").run(context -> {
            String body = post(context.getBean(bean, RestClient.class), "/v1/payments/pay_key_1/cancel?token=query-secret");

            assertThat(body).contains(PHONE);
            assertThat(logs.text()).contains("target=" + target, "method=POST", "status=200", "elapsedMs=",
                            "requestBody=", "responseBody=", "[전화번호]", "/v1/payments/[제거]/cancel")
                    .doesNotContain(PHONE, "header-secret", "query-secret", "pay_key_1");
        });
    }

    @ParameterizedTest
    @CsvSource({"prod", "test", "'local,prod'", "'dev,prod'"})
    @DisplayName("local·dev가 아니거나 prod가 켜져 있으면 값을 비식별화한 본문을 남긴다")
    void bodiesAreDeidentifiedOutsideLocalAndDev(String profiles) {
        runner(profiles).run(context -> {
            post(context.getBean("upstageRestClient", RestClient.class), "/v1/document-digitization");

            assertThat(logs.text()).contains("target=upstage", "status=200",
                            "requestBody={\"query\":\"(문자열 16자)\"}", "responseBody={\"text\":\"(문자열 16자)\"}")
                    .doesNotContain("전화", PHONE);
        });
    }

    @Test
    @DisplayName("dev에서는 본문을 남기고, 오류 응답은 WARN이며 호출한 쪽의 예외는 그대로다")
    void errorResponseIsLoggedAsWarning() {
        reply.set(new Reply(401, "{\"error\":{\"code\":\"invalid_api_key\"}}"));
        runner("dev").run(context -> {
            assertThatThrownBy(() -> post(context.getBean("upstageRestClient", RestClient.class), "/v1/document-digitization"))
                    .hasMessageContaining("invalid_api_key");

            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel().toString()).isEqualTo("WARN");
                assertThat(event.getFormattedMessage()).contains("status=401", "invalid_api_key");
            });
        });
    }

    @Test
    @DisplayName("연결에 실패해도 한 줄을 남기고 원래 예외를 던진다")
    void connectionFailureIsLogged() {
        runner("local").run(context -> {
            RestClient client = context.getBean("tossRestClient", RestClient.class);
            server.stop(0);

            assertThatThrownBy(() -> post(client, "/v1/payments/confirm")).isInstanceOf(ResourceAccessException.class);
            assertThat(logs.text()).contains("target=toss", "status=-", "error=", "/v1/payments/confirm");
        });
    }

    @Test
    @DisplayName("저장소 호출은 작업 이름·상태·크기를 남기고 파일 본문은 남기지 않는다")
    void storageCallsAreLogged() {
        runner("local").run(context -> {
            S3Client s3 = context.getBean(S3Client.class);
            reply.set(new Reply(200, ""));
            s3.putObject(PutObjectRequest.builder().bucket("contractcheck").key("contracts/1/file").build(),
                    RequestBody.fromString("계약서 파일 내용"));
            reply.set(new Reply(403, "<Error><Code>AccessDenied</Code></Error>"));
            assertThatThrownBy(() -> s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket("contractcheck").key("contracts/1/file").build()))
                    .isInstanceOf(S3Exception.class);

            assertThat(logs.text()).contains("target=storage.PutObject", "status=200", "requestBytes=",
                            "target=storage.GetObject", "status=403", "/contractcheck/contracts/1/file")
                    .doesNotContain("계약서 파일 내용", "requestBody", "responseBody");
        });
    }

    @Test
    @DisplayName("Claude SDK 호출은 가린 본문과 함께 남고 SDK는 응답을 그대로 해석한다")
    void claudeCallsAreLogged() {
        reply.set(new Reply(200, """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-5-5",
                 "content":[{"type":"text","text":"전화 010-0000-0000"}],"stop_reason":"end_turn","stop_sequence":null,
                 "usage":{"input_tokens":3,"output_tokens":5}}"""));
        ExternalCallLog log = new ExternalCallLogConfig().externalCallLog(
                environment("local"), runnerMasking());
        AnthropicClient client = AnthropicOkHttpClient.builder().baseUrl(baseUrl).apiKey("sk-ant-unit-test")
                .timeout(Duration.ofSeconds(5)).maxRetries(0)
                .addInterceptor(new AnthropicCallLoggingInterceptor(log))
                .build();

        Message message = client.messages().create(MessageCreateParams.builder()
                .model("claude-sonnet-5-5").maxTokens(16).addUserMessage("주민등록번호 900101-1234567").build());

        assertThat(message.content().get(0).text().orElseThrow().text()).contains(PHONE);
        assertThat(logs.text()).contains("target=claude", "status=200", "[주민번호]", "[전화번호]", "\"input_tokens\":3")
                .doesNotContain(PHONE, "900101-1234567", "sk-ant-unit-test");
        client.close();
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    @DisplayName("Claude 응답 본문을 읽다 시간이 초과되면 인터셉터가 있어도 SDK의 예외(원인은 InterruptedIOException)다")
    void claudeBodyReadTimeoutKeepsSdkExceptionType(boolean logged) throws Exception {
        HttpServer stalled = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stalled.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 500);
            exchange.getResponseBody().write("{\"id\":".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        stalled.start();
        var builder = AnthropicOkHttpClient.builder().baseUrl("http://127.0.0.1:" + stalled.getAddress().getPort())
                .apiKey("sk-ant-unit-test").timeout(Duration.ofSeconds(1)).maxRetries(0);
        if (logged) {
            builder.addInterceptor(new AnthropicCallLoggingInterceptor(
                    new ExternalCallLogConfig().externalCallLog(environment("local"), runnerMasking())));
        }
        AnthropicClient client = builder.build();
        try {
            assertThatThrownBy(() -> client.messages().create(MessageCreateParams.builder()
                    .model("claude-sonnet-5-5").maxTokens(16).addUserMessage("질문").build()))
                    .isExactlyInstanceOf(com.anthropic.errors.AnthropicInvalidDataException.class)
                    .hasRootCauseInstanceOf(java.io.IOException.class)
                    .satisfies(error -> assertThat(causes(error)).anyMatch(cause -> cause instanceof java.io.InterruptedIOException));
            if (logged) {
                assertThat(logs.text()).contains("target=claude", "status=200", "error=");
            }
        } finally {
            client.close();
            stalled.stop(0);
        }
    }

    private static java.util.List<Throwable> causes(Throwable error) {
        java.util.List<Throwable> causes = new java.util.ArrayList<>();
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            causes.add(cause);
        }
        return causes;
    }

    private static org.springframework.mock.env.MockEnvironment environment(String profile) {
        var environment = new org.springframework.mock.env.MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }

    private static org.springframework.beans.factory.ObjectProvider<PiiMaskingService> runnerMasking() {
        var factory = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        factory.registerSingleton("masking", new PiiMaskingService(
                new PiiRuleRepository(new DefaultResourceLoader(), "classpath:masking/pii-rules.yml")));
        return factory.getBeanProvider(PiiMaskingService.class);
    }
}
