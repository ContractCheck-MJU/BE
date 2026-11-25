package ac.mju.contractcheck.client;

/**
 * 결제 대행사(토스)가 승인을 거절했다(카드 한도 초과, 카드사 거절 등). 422 PAYMENT_REJECTED.
 * 토스의 오류 코드는 {@link #providerCode()}로 전달한다. 외부 시스템을 쓸 수 없는 경우가 아니라서
 * {@code ExternalServiceException}을 상속하지 않고 전용 처리기가 응답한다.
 */
public class PaymentRejectedException extends RuntimeException {

    private final String providerCode;
    private final Integer httpStatus;
    private final String responseBody;

    public PaymentRejectedException(String providerCode, String message) {
        this(providerCode, message, null, null);
    }

    /**
     * @param httpStatus 토스가 돌려준 HTTP 상태
     * @param responseBody 토스 응답 원문(JSON). payment_gateway_logs에 보존한다. 없으면 null
     */
    public PaymentRejectedException(String providerCode, String message, Integer httpStatus, String responseBody) {
        super(message);
        this.providerCode = providerCode;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
    }

    /** 토스가 돌려준 HTTP 상태. 없으면 null. */
    public Integer httpStatus() {
        return httpStatus;
    }

    /** 토스 응답 원문(JSON). 없으면 null. 로그에는 남기지 않는다. */
    public String responseBody() {
        return responseBody;
    }

    public int status() {
        return 422;
    }

    public String code() {
        return "PAYMENT_REJECTED";
    }

    /** 토스가 돌려준 오류 코드(예: REJECT_CARD_COMPANY). */
    public String providerCode() {
        return providerCode;
    }

    /** 사용자에게 보이는 문구. 토스 메시지를 그대로 노출하지 않는다. */
    public String publicMessage() {
        return "결제가 승인되지 않았습니다. 결제 수단을 확인한 뒤 다시 시도해 주세요.";
    }
}
