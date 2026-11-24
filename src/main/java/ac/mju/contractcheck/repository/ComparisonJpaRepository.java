package ac.mju.contractcheck.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ComparisonJpaRepository extends JpaRepository<ComparisonEntity, Long> {

    Optional<ComparisonEntity> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
