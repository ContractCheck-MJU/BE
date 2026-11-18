package ac.mju.contractcheck.global.error;

/** 무료·유료 이용권이 모두 없을 때(402 CREDIT_INSUFFICIENT). */
public class CreditInsufficientException extends RuntimeException {
    private static final String CODE = "CREDIT_INSUFFICIENT";

    public CreditInsufficientException() {
        super("남은 이용권이 없습니다.");
    }

    public String code() {
        return CODE;
    }
}
