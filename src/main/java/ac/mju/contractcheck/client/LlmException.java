package ac.mju.contractcheck.client;

import ac.mju.contractcheck.global.error.ExternalServiceException;

/**
 * Claude 호출 실패(거절, 잘못된 응답, 인증·서버 오류). 시간 초과는 {@link LlmTimeoutException}이다.
 * 메시지는 로그용이라 조항 본문을 담지 않고, 응답에는 {@link #publicMessage()}만 나간다.
 * 스펙 미정: 시간 초과 말고는 오류 코드가 없어 503 LLM_UNAVAILABLE을 제안값으로 쓴다.
 */
public class LlmException extends ExternalServiceException {

    public LlmException(String message) {
        super(message, null);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public int status() {
        return 503;
    }

    @Override
    public String code() {
        return "LLM_UNAVAILABLE";
    }

    @Override
    public String publicMessage() {
        return "설명을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.";
    }
}
