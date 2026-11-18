package ac.mju.contractcheck.global.error;

/** 현재 상태와 충돌하는 요청(409). 예: 마지막 ADMIN 탈퇴(LAST_ADMIN). */
public class ConflictException extends RuntimeException {
    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
