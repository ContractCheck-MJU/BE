package ac.mju.contractcheck.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RiskResultJpaRepository extends JpaRepository<RiskResultEntity, Long> {

    List<RiskResultEntity> findByAnalysisIdOrderByClauseId(Long analysisId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    void deleteByAnalysisId(Long analysisId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RiskResultEntity r where r.analysisId in (select a.id from AnalysisEntity a "
            + "where a.contractId in (select c.id from ContractEntity c where c.userId = :userId))")
    void deleteAllByUserId(@Param("userId") Long userId);
}
