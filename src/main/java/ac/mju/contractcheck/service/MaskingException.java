package ac.mju.contractcheck.service;

/**
 * 개인정보 마스킹에 실패했다(FR-UPL-04). 이 예외가 난 텍스트는 외부(추론 서버·Claude)로 보내지 않는다.
 * 메시지에는 어느 규칙에서 실패했는지만 담고 원문은 담지 않는다.
 *
 * 스펙 미정: 마스킹 실패 때의 API 오류 코드. 파이프라인 안에서만 쓰여 지금은 사용자에게 코드로 나가지 않는다.
 */
public class MaskingException extends RuntimeException {

    public MaskingException(String ruleId) {
        super("개인정보 마스킹에 실패했습니다. rule=" + ruleId);
    }

    public MaskingException(String ruleId, Throwable cause) {
        super("개인정보 마스킹에 실패했습니다. rule=" + ruleId, cause);
    }
}
