package ac.mju.contractcheck.client;

/** 추론 서버 호출 실패(연결·시간 초과·5xx·잘못된 응답·인증 오류). 메시지에 조항 본문을 담지 않는다. */
public class InferenceException extends RuntimeException {

    public InferenceException(String message) {
        super(message);
    }

    public InferenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
