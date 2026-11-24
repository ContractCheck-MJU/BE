package ac.mju.contractcheck.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ComparisonPairJpaRepository extends JpaRepository<ComparisonPairEntity, Long> {

    List<ComparisonPairEntity> findByComparisonIdOrderById(Long comparisonId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ComparisonPairEntity p where p.comparisonId in "
            + "(select c.id from ComparisonEntity c where c.userId = :userId)")
    void deleteAllByUserId(@Param("userId") Long userId);
}
