package ac.mju.contractcheck.domain;

import java.time.Instant;

/**
 * 결제 대행사(토스)가 승인 때 돌려준 결제 정보 중 서버가 쓰는 값. 응답 형식은 외부 형식이라 이 값으로 바꿔 서비스에 넘긴다.
 *
 * @param status 대행사의 결제 상태 문자열(DONE 등)
 * @param method 결제 수단. 응답에 없으면 빈 문자열이다.
 * @param approvedAt 승인 시각. 응답에 없으면 null이다.
 * @param raw 응답 원문(JSON). payments.raw에 저장하며 카드 정보가 들어 있을 수 있어 로그에 남기지 않는다.
 */
public record GatewayPayment(
        String paymentKey, String orderId, String status, String method, int totalAmount, Instant approvedAt, String raw) {}
