package ac.mju.contractcheck.domain;

/**
 * Claude가 만든 설명 초안. 저장 전 값이라 id·인용이 없다.
 *
 * @param text 쉬운말 설명(FR-EXP-01)
 * @param suggestion 수정 문구 제안(FR-EXP-03)
 * @param llmModel 응답한 모델 ID. 거절 시 대체 모델이 답하면 그 모델이다.
 * @param promptVersion 요청에 쓴 프롬프트 버전
 */
public record ExplanationDraft(String text, String suggestion, String llmModel, String promptVersion) {

    public ExplanationDraft {
        if (text == null || text.isBlank() || suggestion == null || suggestion.isBlank()) {
            throw new IllegalArgumentException("설명과 수정 문구는 비어 있을 수 없습니다.");
        }
    }
}
