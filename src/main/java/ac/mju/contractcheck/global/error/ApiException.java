package ac.mju.contractcheck.global.error;

/**
 * 상태 코드와 오류 코드를 함께 가진 요청 거절. 404·409·402처럼 전용 예외가 있는 경우를 뺀
 * 400(VALIDATION_ERROR), 413(FILE_TOO_LARGE), 415(UNSUPPORTED_FILE_TYPE) 등에 쓴다.
 */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
