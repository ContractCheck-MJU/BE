package ac.mju.contractcheck.client;

import ac.mju.contractcheck.config.TossProperties;
import ac.mju.contractcheck.domain.GatewayPayment;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 토스페이먼츠 승인 API({@code POST /v1/payments/confirm}) 구현체. 시크릿 키를 {@code Basic base64(키:)}로 보낸다.
 * {@code contractcheck.toss.secret-key}가 있을 때만 빈으로 등록된다.
 *
 * <p>Idempotency-Key는 결제 키로 만든다(같은 결제 키를 다시 보내면 토스가 같은 결과를 돌려주고, 새 결제 키는 새 요청이 된다).
 * 로그에는 주문 번호와 상태 코드만 남기고 결제 키·시크릿 키·응답 본문은 남기지 않는다.
 *
 * <p>TODO(FR-PAY-03·04): 결제 조회(GET /v1/payments/{paymentKey})와 취소(POST /v1/payments/{paymentKey}/cancel).
 */
@Slf4j
@Component
@ConditionalOnExpression("'${contractcheck.toss.secret-key:}'.length() > 0")
public class TossPaymentGatewayClient implements PaymentGatewayClient {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    /** 우리 쪽 키 설정이 틀렸을 때 토스가 돌려주는 코드. 사용자의 결제 문제가 아니라 서버 문제라 502로 본다. */
    private static final Set<String> KEY_ERROR_CODES = Set.of("UNAUTHORIZED_KEY", "INVALID_API_KEY");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** JSON이 아닌 응답 본문을 감싸 저장할 때의 최대 길이. */
    private static final int MAX_UNPARSED_BODY = 2000;

    private final RestClient restClient;
    private final String authorization;

    public TossPaymentGatewayClient(@NonNull TossProperties properties, @NonNull RestClient tossRestClient) {
        this.restClient = tossRestClient;
        this.authorization = "Basic " + Base64.getEncoder()
                .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public GatewayPayment confirm(String paymentKey, String orderId, int amount) {
        Response response;
        try {
            response = restClient.post()
                    .uri(CONFIRM_PATH)
                    .header("Authorization", authorization)
                    .header("Idempotency-Key", "confirm-" + paymentKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ConfirmRequest(paymentKey, orderId, amount))
                    .exchange((request, result) -> new Response(result.getStatusCode().value(), result.bodyTo(String.class)));
        } catch (ResourceAccessException exception) {
            throw translate(exception, orderId);
        }
        if (response.status() / 100 == 2) {
            return toPayment(response, orderId);
        }
        throw toFailure(response, orderId);
    }

    private static PaymentGatewayException translate(ResourceAccessException exception, String orderId) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                log.warn("토스 승인 응답 시간 초과 orderId={}", orderId);
                return new PaymentGatewayTimeoutException("토스 승인 응답을 시간 안에 받지 못했습니다.", exception);
            }
        }
        log.warn("토스 승인 호출 실패 orderId={} type={}", orderId, exception.getClass().getSimpleName());
        return new PaymentGatewayException("토스 승인 API에 연결하지 못했습니다.", exception);
    }

    private static RuntimeException toFailure(Response response, String orderId) {
        String providerCode = errorCode(response.body());
        String raw = asJson(response.body());
        log.warn("토스 승인 실패 orderId={} status={} providerCode={}", orderId, response.status(), providerCode);
        boolean ourFault = response.status() == 429 || response.status() / 100 == 5 || KEY_ERROR_CODES.contains(providerCode);
        if (ourFault || providerCode.isEmpty()) {
            return PaymentGatewayException.withResponse(
                    "토스 승인 API가 오류를 돌려줬습니다. status=" + response.status() + " code=" + providerCode, response.status(), raw);
        }
        return new PaymentRejectedException(
                providerCode, "토스가 승인을 거절했습니다. status=" + response.status() + " code=" + providerCode, response.status(), raw);
    }

    /**
     * 응답 본문을 JSONB 컬럼에 넣을 수 있는 JSON으로 만든다. 본문이 JSON이면 그대로, 아니면(프록시의 HTML 오류 등)
     * {@code {"unparsedBody": "..."}}로 감싼다. 본문이 없으면 null이다.
     */
    private static String asJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JSON.readTree(body);
            return body;
        } catch (JacksonException exception) {
            String clipped = body.length() > MAX_UNPARSED_BODY ? body.substring(0, MAX_UNPARSED_BODY) : body;
            return JSON.writeValueAsString(Map.of("unparsedBody", clipped));
        }
    }

    private static String errorCode(String body) {
        try {
            JsonNode code = body == null ? null : JSON.readTree(body).get("code");
            return code == null || code.isNull() ? "" : code.asString();
        } catch (JacksonException exception) {
            return "";
        }
    }

    private static GatewayPayment toPayment(Response response, String orderId) {
        String body = response.body();
        try {
            JsonNode node = JSON.readTree(body == null ? "" : body);
            String paymentKey = text(node, "paymentKey");
            String confirmedOrderId = text(node, "orderId");
            String status = text(node, "status");
            if (paymentKey.isEmpty() || confirmedOrderId.isEmpty() || status.isEmpty() || !node.hasNonNull("totalAmount")) {
                throw PaymentGatewayException.withResponse("토스 승인 응답에 필수 값이 없습니다. orderId=" + orderId, response.status(), asJson(body));
            }
            return new GatewayPayment(paymentKey, confirmedOrderId, status, text(node, "method"),
                    node.get("totalAmount").asInt(), approvedAt(text(node, "approvedAt")), body);
        } catch (JacksonException exception) {
            throw PaymentGatewayException.withResponse("토스 승인 응답을 읽지 못했습니다. orderId=" + orderId, response.status(), asJson(body));
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString();
    }

    private static Instant approvedAt(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private record ConfirmRequest(String paymentKey, String orderId, int amount) {}

    private record Response(int status, String body) {}
}
