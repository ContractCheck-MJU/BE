package ac.mju.contractcheck.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AnalysisJpaRepository extends JpaRepository<AnalysisEntity, Long> {

    @Query("select a from AnalysisEntity a where a.id = :id "
            + "and a.contractId in (select c.id from ContractEntity c where c.userId = :userId)")
    Optional<AnalysisEntity> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByContractIdAndStatus(Long contractId, String status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AnalysisEntity a where a.contractId in (select c.id from ContractEntity c where c.userId = :userId)")
    void deleteAllByUserId(@Param("userId") Long userId);
}
