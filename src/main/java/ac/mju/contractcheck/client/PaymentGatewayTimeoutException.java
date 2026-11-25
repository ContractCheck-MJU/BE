package ac.mju.contractcheck.client;

/** 결제 대행사 응답을 시간 안에 받지 못했다. 승인이 됐는지 알 수 없다. 504 PAYMENT_GATEWAY_TIMEOUT. */
public class PaymentGatewayTimeoutException extends PaymentGatewayException {

    public PaymentGatewayTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public int status() {
        return 504;
    }

    @Override
    public String code() {
        return "PAYMENT_GATEWAY_TIMEOUT";
    }

    @Override
    public String publicMessage() {
        return "결제 확인이 지연되고 있습니다. 잠시 후 결제 내역을 확인해 주세요.";
    }
}
