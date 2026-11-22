package ac.mju.contractcheck.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 소유자 확인은 clauses.contract_id가 가리키는 contracts.user_id로 한다. */
interface ClauseJpaRepository extends JpaRepository<ClauseEntity, Long> {

    @Query("select c from ClauseEntity c where c.contractId = :contractId "
            + "and c.contractId in (select k.id from ContractEntity k where k.userId = :userId) "
            + "order by c.clauseNo asc, c.id asc")
    List<ClauseEntity> findByContractOwned(@Param("contractId") Long contractId, @Param("userId") Long userId);

    @Query("select c from ClauseEntity c where c.id = :id "
            + "and c.contractId in (select k.id from ContractEntity k where k.userId = :userId)")
    Optional<ClauseEntity> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ClauseEntity c where c.contractId = :contractId")
    void deleteByContractId(@Param("contractId") Long contractId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ClauseEntity c where c.contractId in (select k.id from ContractEntity k where k.userId = :userId)")
    void deleteAllByUserId(@Param("userId") Long userId);
}
