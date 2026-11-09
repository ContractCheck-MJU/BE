package ac.mju.contractcheck.dto.explanation;

import ac.mju.contractcheck.domain.Explanation;
import java.util.List;

public record ExplanationResponse(long explanationId, String text, String suggestion, List<String> citations) {
    /**
     * TODO(FR-EXP-02): explanation_citations를 함께 받아 citations에 넣는다. 현재는 항상 빈 목록이다.
     */
    public static ExplanationResponse from(Explanation explanation) {
        return new ExplanationResponse(explanation.id(), explanation.text(), explanation.suggestion(), List.of());
    }
}
