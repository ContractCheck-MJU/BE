package ac.mju.contractcheck.domain;

import ac.mju.contractcheck.domain.enums.GatewayAction;
import ac.mju.contractcheck.domain.enums.GatewayOutcome;
import java.time.Instant;

/**
 * 결제 대행사(토스) 호출 한 번의 결과. payment_gateway_logs의 한 행이며 한 번 쌓으면 바꾸지 않는다.
 *
 * @param httpStatus 토스가 돌려준 HTTP 상태. 응답을 받지 못했으면 null
 * @param response 토스 응답 원문(JSON). 응답 본문이 없거나 받지 못했으면 null. 카드 정보가 들어 있을 수 있어 로그에 남기지 않는다.
 */
public record PaymentGatewayLog(
        long id, long paymentId, GatewayAction action, GatewayOutcome outcome, Integer httpStatus, String response, Instant createdAt) {}
