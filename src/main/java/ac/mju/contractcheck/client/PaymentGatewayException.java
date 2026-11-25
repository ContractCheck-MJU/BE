package ac.mju.contractcheck.client;

import ac.mju.contractcheck.global.error.ExternalServiceException;

/** 결제 대행사(토스)를 쓸 수 없다: 연결 실패, 5xx·429, 우리 쪽 키 오류, 응답 형식 오류. 502 PAYMENT_GATEWAY_ERROR. */
public class PaymentGatewayException extends ExternalServiceException {

    private final Integer httpStatus;
    private final String responseBody;

    public PaymentGatewayException(String message) {
        super(message, null);
        this.httpStatus = null;
        this.responseBody = null;
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = null;
        this.responseBody = null;
    }

    private PaymentGatewayException(String message, Integer httpStatus, String responseBody) {
        super(message, null);
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
    }

    /** 토스가 응답은 돌려줬지만 쓸 수 없는 경우. HTTP 상태와 응답 원문(JSON, 없으면 null)을 이력에 남기도록 싣는다. */
    public static PaymentGatewayException withResponse(String message, int httpStatus, String responseBody) {
        return new PaymentGatewayException(message, httpStatus, responseBody);
    }

    /** 토스가 돌려준 HTTP 상태. 응답을 받지 못했으면(연결 실패·시간 초과) null. */
    public Integer httpStatus() {
        return httpStatus;
    }

    /** 토스 응답 원문(JSON). 없으면 null. 로그에는 남기지 않는다. */
    public String responseBody() {
        return responseBody;
    }

    @Override
    public int status() {
        return 502;
    }

    @Override
    public String code() {
        return "PAYMENT_GATEWAY_ERROR";
    }

    @Override
    public String publicMessage() {
        return "결제 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.";
    }
}
