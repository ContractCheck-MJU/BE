package ac.mju.contractcheck.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ContractJpaRepository extends JpaRepository<ContractEntity, Long> {

    Optional<ContractEntity> findByIdAndUserId(Long id, Long userId);

    Page<ContractEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
}
