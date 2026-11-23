package ac.mju.contractcheck.client;

import ac.mju.contractcheck.global.error.ExternalServiceException;

/**
 * 추론 서버 호출 실패(연결·시간 초과·5xx·잘못된 응답·인증 오류). API로는 503 INFERENCE_UNAVAILABLE이다(스펙시트).
 * 메시지는 로그용이라 조항 본문을 담지 않고, 응답에는 {@link #publicMessage()}만 나간다.
 */
public class InferenceException extends ExternalServiceException {

    public InferenceException(String message) {
        super(message, null);
    }

    public InferenceException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public int status() {
        return 503;
    }

    @Override
    public String code() {
        return "INFERENCE_UNAVAILABLE";
    }

    @Override
    public String publicMessage() {
        return "분석 서버 점검 중입니다. 잠시 후 다시 시도해 주세요.";
    }
}
