package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Explanation;
import ac.mju.contractcheck.domain.ExplanationCitation;
import ac.mju.contractcheck.domain.UserId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * V1__init.sql의 {@code explanations}, {@code explanation_citations}에 저장한다. explanations에는 소유자 컬럼이 없어
 * clause_id → clauses.contract_id → contracts.user_id로 확인하며, 다른 회원의 설명은 없는 것과 같게 {@link Optional#empty()}다.
 * 인용 점수는 스키마(NUMERIC(5,4))에 맞춰 소수 넷째 자리로 저장한다.
 * 트랜잭션은 열지 않는다. 설명과 인용을 함께 저장하는 트랜잭션은 호출하는 service가 연다.
 */
@Repository
@RequiredArgsConstructor
public class ExplanationRepository implements OwnedDataEraser {

    @NonNull
    private final ExplanationJpaRepository explanations;
    @NonNull
    private final ExplanationCitationJpaRepository citations;

    /** 새 설명과 인용을 저장한다. 소유자 확인은 호출하는 service가 조항으로 먼저 한다. */
    public @NonNull Explanation save(@NonNull Explanation explanation) {
        ExplanationEntity saved = explanations.save(new ExplanationEntity(
                explanation.clauseId(),
                explanation.text(),
                explanation.suggestion(),
                explanation.llmModel(),
                explanation.promptVersion(),
                explanation.createdAt()));
        citations.saveAll(explanation.citations().stream()
                .map(citation -> new ExplanationCitationEntity(
                        saved.getId(),
                        citation.sourceType(),
                        citation.sourceId(),
                        BigDecimal.valueOf(citation.score()).setScale(4, RoundingMode.HALF_UP)))
                .toList());
        return toExplanation(saved, explanation.owner(), explanation.citations());
    }

    public @NonNull Optional<Explanation> findOwned(long id, UserId owner) {
        return explanations.findOwned(id, owner.value())
                .map(entity -> toExplanation(entity, owner, findCitations(entity.getId())));
    }

    /** 조항 하나에 지금까지 만든 설명 수. 재생성 제한(TC-EXP-010)에 쓴다. */
    public long countByClause(long clauseId) {
        return explanations.countByClauseId(clauseId);
    }

    /** 탈퇴 시 소유자의 설명과 인용을 모두 지운다(FR-AUTH-03). 인용을 먼저 지운다. */
    @Override
    public void deleteAllByOwner(UserId owner) {
        citations.deleteAllByUserId(owner.value());
        explanations.deleteAllByUserId(owner.value());
    }

    private List<ExplanationCitation> findCitations(long explanationId) {
        return citations.findByExplanationIdOrderById(explanationId).stream()
                .map(entity -> new ExplanationCitation(
                        entity.getSourceType(),
                        entity.getSourceId(),
                        entity.getScore() == null ? 0.0 : entity.getScore().doubleValue()))
                .toList();
    }

    private static Explanation toExplanation(ExplanationEntity entity, UserId owner, List<ExplanationCitation> citations) {
        return new Explanation(
                entity.getId(),
                entity.getClauseId(),
                owner,
                entity.getText() == null ? "" : entity.getText(),
                entity.getSuggestion() == null ? "" : entity.getSuggestion(),
                entity.getLlmModel() == null ? "" : entity.getLlmModel(),
                entity.getPromptVersion() == null ? "" : entity.getPromptVersion(),
                entity.getCreatedAt(),
                citations);
    }
}
