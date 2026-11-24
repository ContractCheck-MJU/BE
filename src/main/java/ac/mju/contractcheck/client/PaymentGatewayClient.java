package ac.mju.contractcheck.client;

import ac.mju.contractcheck.domain.GatewayPayment;

/**
 * 결제 대행사(토스페이먼츠)를 부르는 유일한 통로다(FR-PAY-02). 서비스는 이 인터페이스만 부르고, 테스트에서는 mock한다.
 * 호출은 트랜잭션 밖에서 한다. 구현체는 시크릿 키와 결제 응답 원문(카드 정보 포함 가능)을 로그에 남기지 않는다.
 */
public interface PaymentGatewayClient {

    /**
     * 결제를 승인한다(토스 {@code POST /v1/payments/confirm}). 같은 결제 키로 다시 불러도 같은 결과가 오도록 멱등 키를 붙인다.
     *
     * @return 승인 응답. 상태가 DONE인지는 호출하는 쪽이 확인한다.
     * @throws PaymentRejectedException 토스가 승인을 거절했을 때(카드 한도 초과·카드사 거절 등)
     * @throws PaymentGatewayTimeoutException 응답을 시간 안에 받지 못해 결과를 알 수 없을 때
     * @throws PaymentGatewayException 연결 실패·5xx·키 오류·응답 형식 오류 등 토스를 쓸 수 없을 때
     */
    GatewayPayment confirm(String paymentKey, String orderId, int amount);
}
