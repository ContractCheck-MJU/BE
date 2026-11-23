package ac.mju.contractcheck.client;

/** OCR 호출 실패(파일 오류, 인식 실패, 제한 시간 초과, 실행 환경 문제). 메시지에 계약서 내용을 담지 않는다. */
public class OcrException extends RuntimeException {

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
