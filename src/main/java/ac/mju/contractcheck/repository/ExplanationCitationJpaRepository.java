package ac.mju.contractcheck.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ExplanationCitationJpaRepository extends JpaRepository<ExplanationCitationEntity, Long> {

    List<ExplanationCitationEntity> findByExplanationIdOrderById(Long explanationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ExplanationCitationEntity t where t.explanationId in "
            + "(select e.id from ExplanationEntity e where e.clauseId in "
            + "(select c.id from ClauseEntity c where c.contractId in "
            + "(select k.id from ContractEntity k where k.userId = :userId)))")
    void deleteAllByUserId(@Param("userId") Long userId);
}
