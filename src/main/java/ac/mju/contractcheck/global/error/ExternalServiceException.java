package ac.mju.contractcheck.global.error;

/**
 * 외부 시스템(추론 서버, Claude 등)을 쓸 수 없어 요청을 처리하지 못한 경우의 공통 부모.
 * 응답에 쓸 상태 코드·오류 코드·문구는 각 예외가 스스로 정하므로, 새 연동을 추가해도 {@link ApiExceptionHandler}는 고치지 않는다.
 *
 * <p>{@link #getMessage()}는 로그용 내부 원인이라 응답에 담지 않는다. 응답 문구는 {@link #publicMessage()}만 쓴다.
 * 스펙에 HTTP 오류 코드가 없는 연동(OCR은 비동기 상태 FAILED로 알린다)은 이 예외를 상속하지 않는다.
 */
public abstract class ExternalServiceException extends RuntimeException {

    protected ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    /** HTTP 상태 코드. */
    public abstract int status();

    /** API 오류 코드(스펙시트 오류 코드 표). */
    public abstract String code();

    /** 사용자에게 보이는 문구. 내부 주소·키·본문을 담지 않는다. */
    public abstract String publicMessage();
}
