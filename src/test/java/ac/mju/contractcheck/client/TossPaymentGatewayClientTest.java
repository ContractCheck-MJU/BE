package ac.mju.contractcheck.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ac.mju.contractcheck.config.TossProperties;
import ac.mju.contractcheck.domain.GatewayPayment;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 토스 승인 API 호출 계약(URL·인증·본문·멱등 키)과 오류 변환. 실제 토스는 부르지 않고 MockRestServiceServer로 응답을 흉내 낸다. */
class TossPaymentGatewayClientTest {

    private static final String SECRET_KEY = "test_sk_secretForUnitTest";
    private static final String BASE_URL = "https://api.tosspayments.test";
    private static final String CONFIRM_URL = BASE_URL + "/v1/payments/confirm";
    private static final String SUCCESS_BODY = """
            {"version":"2022-11-16","paymentKey":"pay_key_1","type":"NORMAL","orderId":"order-1234","orderName":"분석 5회",
             "mId":"tosstest","currency":"KRW","method":"카드","totalAmount":4900,"balanceAmount":4900,"status":"DONE",
             "requestedAt":"2026-11-25T10:00:00+09:00","approvedAt":"2026-11-25T10:00:05+09:00",
             "card":{"number":"43301234****123*","amount":4900},"receipt":{"url":"https://example.test/r"}}
            """;

    private MockRestServiceServer server;
    private TossPaymentGatewayClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossPaymentGatewayClient(
                new TossProperties(BASE_URL, SECRET_KEY, Duration.ofSeconds(5)), builder.build());
    }

    @Test
    @DisplayName("TC-PAY-003 승인 요청은 Basic 인증(시크릿 키:)·멱등 키·JSON 본문으로 가고, 응답을 결제 값으로 바꾼다")
    void confirmSendsContractAndMapsResponse() {
        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(CONFIRM_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", expectedAuth))
                .andExpect(header("Idempotency-Key", "confirm-pay_key_1"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("{\"paymentKey\":\"pay_key_1\",\"orderId\":\"order-1234\",\"amount\":4900}"))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        GatewayPayment payment = client.confirm("pay_key_1", "order-1234", 4900);

        assertThat(payment.paymentKey()).isEqualTo("pay_key_1");
        assertThat(payment.orderId()).isEqualTo("order-1234");
        assertThat(payment.status()).isEqualTo("DONE");
        assertThat(payment.method()).isEqualTo("카드");
        assertThat(payment.totalAmount()).isEqualTo(4900);
        assertThat(payment.approvedAt()).isEqualTo(Instant.parse("2026-11-25T01:00:05Z"));
        assertThat(payment.raw()).contains("\"paymentKey\":\"pay_key_1\"");
        server.verify();
    }

    @Test
    @DisplayName("FR-PAY-02 토스가 카드사 거절 등으로 4xx를 돌려주면 PAYMENT_REJECTED(422)이고 토스 오류 코드를 providerCode로 담는다")
    void clientErrorBecomesRejection() {
        server.expect(requestTo(CONFIRM_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"REJECT_CARD_COMPANY\",\"message\":\"카드사에서 거절했습니다.\"}"));

        assertThatThrownBy(() -> client.confirm("pay_key_1", "order-1234", 4900))
                .isInstanceOfSatisfying(PaymentRejectedException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(422);
                    assertThat(exception.code()).isEqualTo("PAYMENT_REJECTED");
                    assertThat(exception.providerCode()).isEqualTo("REJECT_CARD_COMPANY");
                    assertThat(exception.httpStatus()).isEqualTo(400);
                    assertThat(exception.responseBody()).as("응답 원문을 예외에 실어 이력에 남기게 한다")
                            .contains("REJECT_CARD_COMPANY", "카드사에서 거절했습니다.");
                });
    }

    @Test
    @DisplayName("FR-PAY-02 토스 오류 응답이 JSON이 아니어도 JSONB에 들어갈 수 있게 감싸고, 본문이 없으면 null이며 HTTP 상태는 남는다")
    void errorBodyIsKeptAsValidJson() {
        server.expect(requestTo(CONFIRM_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body("<html>bad gateway</html>"));
        server.expect(requestTo(CONFIRM_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.confirm("k1", "order-1234", 4900)).isInstanceOfSatisfying(PaymentGatewayException.class, exception -> {
            assertThat(exception.httpStatus()).isEqualTo(502);
            assertThat(exception.responseBody()).isEqualTo("{\"unparsedBody\":\"<html>bad gateway</html>\"}");
        });
        assertThatThrownBy(() -> client.confirm("k2", "order-1234", 4900)).isInstanceOfSatisfying(PaymentGatewayException.class, exception -> {
            assertThat(exception.httpStatus()).isEqualTo(500);
            assertThat(exception.responseBody()).isNull();
        });
    }

    @Test
    @DisplayName("FR-PAY-02 우리 쪽 키 설정 오류(UNAUTHORIZED_KEY)는 사용자 거절이 아니라 502 PAYMENT_GATEWAY_ERROR이고 키가 메시지에 남지 않는다")
    void invalidKeyIsGatewayError() {
        server.expect(requestTo(CONFIRM_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"UNAUTHORIZED_KEY\",\"message\":\"인증되지 않은 시크릿 키 혹은 클라이언트 키 입니다.\"}"));

        assertThatThrownBy(() -> client.confirm("pay_key_1", "order-1234", 4900))
                .isInstanceOfSatisfying(PaymentGatewayException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(502);
                    assertThat(exception.code()).isEqualTo("PAYMENT_GATEWAY_ERROR");
                    assertThat(exception.getMessage()).doesNotContain(SECRET_KEY);
                    assertThat(exception.publicMessage()).doesNotContain(SECRET_KEY, "UNAUTHORIZED_KEY");
                });
    }

    @Test
    @DisplayName("FR-PAY-02 토스 5xx와 429는 502 PAYMENT_GATEWAY_ERROR다")
    void serverErrorIsGatewayError() {
        server.expect(requestTo(CONFIRM_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(CONFIRM_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.confirm("k1", "order-1234", 4900)).isInstanceOf(PaymentGatewayException.class);
        assertThatThrownBy(() -> client.confirm("k2", "order-1234", 4900)).isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    @DisplayName("FR-PAY-02 읽기 시간 초과는 504 PAYMENT_GATEWAY_TIMEOUT, 연결 실패는 502다")
    void timeoutAndConnectionFailure() {
        server.expect(requestTo(CONFIRM_URL)).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });
        server.expect(requestTo(CONFIRM_URL)).andRespond(request -> {
            throw new ConnectException("Connection refused");
        });

        assertThatThrownBy(() -> client.confirm("k1", "order-1234", 4900)).isInstanceOfSatisfying(
                PaymentGatewayTimeoutException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(504);
                    assertThat(exception.code()).isEqualTo("PAYMENT_GATEWAY_TIMEOUT");
                });
        assertThatThrownBy(() -> client.confirm("k2", "order-1234", 4900))
                .isInstanceOf(PaymentGatewayException.class).isNotInstanceOf(PaymentGatewayTimeoutException.class);
    }

    @Test
    @DisplayName("FR-PAY-02 200이지만 필수 값이 빠졌거나 JSON이 아니면 승인 결과로 믿지 않고 502다")
    void malformedSuccessBodyIsGatewayError() {
        server.expect(requestTo(CONFIRM_URL)).andRespond(withSuccess("{\"status\":\"DONE\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(CONFIRM_URL)).andRespond(withSuccess("not json", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.confirm("k1", "order-1234", 4900)).isInstanceOf(PaymentGatewayException.class);
        assertThatThrownBy(() -> client.confirm("k2", "order-1234", 4900)).isInstanceOf(PaymentGatewayException.class);
    }
}
