package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.ClauseDraft;
import ac.mju.contractcheck.domain.UserId;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * V1__init.sql의 {@code clauses}에 저장한다. 소유자 확인은 clauses.contract_id → contracts.user_id로 하며,
 * 다른 회원의 조항은 없는 것과 같게 비어 있는 결과를 돌려준다.
 * 위험 등급(risk_results 조인)은 분석 영속화(FR-ANL-02) 전이라 빈 문자열이다.
 */
@Repository
@RequiredArgsConstructor
public class ClauseRepository {

    private static final String NO_RISK_LEVEL = "";

    @NonNull
    private final ClauseJpaRepository clauses;

    /** 한 계약의 조항을 모두 지우고 분할 결과로 바꾼다. 번호는 drafts의 no를 그대로 쓴다. 삭제와 저장을 묶는 트랜잭션은 호출하는 service가 연다. */
    public @NonNull List<Clause> replaceAll(long contractId, UserId owner, @NonNull List<ClauseDraft> drafts) {
        clauses.deleteByContractId(contractId);
        List<ClauseEntity> entities = drafts.stream()
                .map(draft -> new ClauseEntity(contractId, draft.no(), draft.title(), draft.text()))
                .toList();
        return clauses.saveAll(entities).stream().map(entity -> toClause(entity, owner)).toList();
    }

    public @NonNull List<Clause> findByContract(long contractId, UserId owner) {
        return clauses.findByContractOwned(contractId, owner.value()).stream()
                .map(entity -> toClause(entity, owner))
                .toList();
    }

    public @NonNull Optional<Clause> findOwned(long id, UserId owner) {
        return clauses.findOwned(id, owner.value()).map(entity -> toClause(entity, owner));
    }

    /** 번호·제목·본문을 저장한다. 소유자가 다르거나 없는 조항이면 예외가 난다(호출 전에 findOwned로 확인한다). 조회와 수정이 한 트랜잭션이도록 호출하는 service가 트랜잭션을 연다. */
    public @NonNull Clause save(@NonNull Clause clause) {
        ClauseEntity entity = clauses.findOwned(clause.id(), clause.owner().value()).orElseThrow();
        entity.edit(clause.no(), clause.title(), clause.text());
        return toClause(clauses.save(entity), clause.owner());
    }

    /** 계약 하나의 조항을 모두 지운다. 벌크 삭제라 호출하는 쪽이 트랜잭션을 열어야 한다. */
    public void deleteByContract(long contractId) {
        clauses.deleteByContractId(contractId);
    }

    /** 회원이 가진 모든 계약의 조항을 지운다. 벌크 삭제라 호출하는 쪽이 트랜잭션을 열어야 한다. */
    public void deleteAllByOwner(UserId owner) {
        clauses.deleteAllByUserId(owner.value());
    }

    private Clause toClause(ClauseEntity entity, UserId owner) {
        return new Clause(
                entity.getId(),
                entity.getContractId(),
                owner,
                entity.getClauseNo(),
                entity.getTitle() == null ? "" : entity.getTitle(),
                entity.getText() == null ? "" : entity.getText(),
                NO_RISK_LEVEL);
    }
}
