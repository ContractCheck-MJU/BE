package ac.mju.contractcheck.dto.explanation;

import ac.mju.contractcheck.domain.Explanation;
import java.util.List;

/**
 * 설명 생성·조회 응답.
 *
 * @param text 쉬운말 설명(FR-EXP-01)
 * @param suggestion 수정 문구 제안(FR-EXP-03)
 * @param citations 근거 인용(FR-EXP-02). 유사도 높은 순이다.
 */
public record ExplanationResponse(long explanationId, String text, String suggestion, List<Citation> citations) {

    /**
     * @param sourceType 근거 종류(LAW·PRECEDENT)
     * @param sourceId 근거 문서 id(legal_documents.id)
     * @param score 코사인 유사도(0~1)
     */
    public record Citation(String sourceType, long sourceId, double score) {}

    public static ExplanationResponse from(Explanation explanation) {
        return new ExplanationResponse(
                explanation.id(),
                explanation.text(),
                explanation.suggestion(),
                explanation.citations().stream()
                        .map(citation -> new Citation(citation.sourceType(), citation.sourceId(), citation.score()))
                        .toList());
    }
}
