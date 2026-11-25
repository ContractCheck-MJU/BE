package ac.mju.contractcheck.client;

/**
 * Claude가 제한 시간(contractcheck.llm.timeout) 안에 답하지 않았다(TC-EXP-013, LLM_TIMEOUT).
 * 스펙 미정: LLM_TIMEOUT의 HTTP 상태. 지금은 504를 제안값으로 쓴다.
 */
public class LlmTimeoutException extends LlmException {

    public LlmTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public int status() {
        return 504;
    }

    @Override
    public String code() {
        return "LLM_TIMEOUT";
    }

    @Override
    public String publicMessage() {
        return "설명 생성이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.";
    }
}
