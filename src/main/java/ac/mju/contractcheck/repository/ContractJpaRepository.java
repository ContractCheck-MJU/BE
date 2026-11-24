package ac.mju.contractcheck.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ContractJpaRepository extends JpaRepository<ContractEntity, Long> {

    Optional<ContractEntity> findByIdAndUserId(Long id, Long userId);

    Page<ContractEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    @Query("select c.s3Key from ContractEntity c where c.userId = :userId and c.s3Key is not null")
    List<String> findS3KeysByUserId(@Param("userId") Long userId);

    List<ContractEntity> findByStatusInAndCreatedAtBefore(Collection<String> statuses, Instant before);

    void deleteByUserId(Long userId);
}
