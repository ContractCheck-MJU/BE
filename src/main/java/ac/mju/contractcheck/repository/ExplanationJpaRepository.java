package ac.mju.contractcheck.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 소유자 확인은 explanations.clause_id → clauses.contract_id → contracts.user_id로 한다. */
interface ExplanationJpaRepository extends JpaRepository<ExplanationEntity, Long> {

    @Query("select e from ExplanationEntity e where e.id = :id and e.clauseId in "
            + "(select c.id from ClauseEntity c where c.contractId in "
            + "(select k.id from ContractEntity k where k.userId = :userId))")
    Optional<ExplanationEntity> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    long countByClauseId(Long clauseId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ExplanationEntity e where e.clauseId in "
            + "(select c.id from ClauseEntity c where c.contractId in "
            + "(select k.id from ContractEntity k where k.userId = :userId))")
    void deleteAllByUserId(@Param("userId") Long userId);
}
