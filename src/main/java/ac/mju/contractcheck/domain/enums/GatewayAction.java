package ac.mju.contractcheck.domain.enums;

/** 결제 대행사(토스)에 보낸 요청 종류. 이름이 DB(payment_gateway_logs.action)에 저장되므로 바꾸지 않는다. */
public enum GatewayAction {
    /** 결제 승인(POST /v1/payments/confirm). 조회·취소·웹훅은 FR-PAY-03·04에서 추가한다. */
    CONFIRM
}
