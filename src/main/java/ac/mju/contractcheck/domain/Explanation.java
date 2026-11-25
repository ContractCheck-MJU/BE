package ac.mju.contractcheck.domain;

import java.time.Instant;
import java.util.List;

/**
 * 조항 하나에 대한 쉬운말 설명(FR-EXP-01)과 수정 문구 제안(FR-EXP-03), 근거 인용(FR-EXP-02).
 * explanations에는 소유자 컬럼이 없어 owner는 clauses.contract_id → contracts.user_id로 정해진다.
 *
 * @param llmModel 설명을 만든 Claude 모델 ID
 * @param promptVersion 프롬프트 버전. 프롬프트를 바꾸면 올려서 어떤 지시로 만든 설명인지 구분한다.
 */
public record Explanation(
        long id,
        long clauseId,
        UserId owner,
        String text,
        String suggestion,
        String llmModel,
        String promptVersion,
        Instant createdAt,
        List<ExplanationCitation> citations) {

    /** 같은 조항의 재생성 허용 횟수(TC-EXP-010). 최초 생성 1회는 세지 않는다. */
    public static final int MAX_REGENERATIONS = 3;

    public Explanation {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    /** 이미 만든 설명 개수로 한 번 더 만들 수 있는지 본다. 최초 1회 + 재생성 {@value #MAX_REGENERATIONS}회까지다. */
    public static boolean canCreate(long existingCount) {
        return existingCount < 1 + MAX_REGENERATIONS;
    }
}
