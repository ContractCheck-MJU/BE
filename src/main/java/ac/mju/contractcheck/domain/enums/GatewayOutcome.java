package ac.mju.contractcheck.domain.enums;

/** 결제 대행사 호출의 결과 분류. 이름이 DB(payment_gateway_logs.outcome)에 저장되므로 바꾸지 않는다. */
public enum GatewayOutcome {
    /** 승인에 성공하고 이용권을 지급했다. */
    SUCCESS,
    /** 토스가 승인을 거절했다(4xx). */
    REJECTED,
    /** 토스가 오류 응답을 돌려줬거나 응답을 읽지 못했다(5xx·429·키 오류·형식 오류). */
    ERROR,
    /** 2xx 응답이지만 DONE이 아니거나 주문 번호·금액이 달라 지급하지 않았다. */
    UNEXPECTED,
    /** 응답을 받지 못했다(연결 실패·시간 초과). response는 null이다. */
    NO_RESPONSE
}
