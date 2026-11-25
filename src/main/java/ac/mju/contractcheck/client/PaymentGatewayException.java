package ac.mju.contractcheck.client;

import ac.mju.contractcheck.global.error.ExternalServiceException;

/** 결제 대행사(토스)를 쓸 수 없다: 연결 실패, 5xx·429, 우리 쪽 키 오류, 응답 형식 오류. 502 PAYMENT_GATEWAY_ERROR. */
public class PaymentGatewayException extends ExternalServiceException {

    public PaymentGatewayException(String message) {
        super(message, null);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
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
