package ac.mju.contractcheck.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface CreditLedgerJpaRepository extends JpaRepository<CreditLedgerEntity, Long> {

    Page<CreditLedgerEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    boolean existsByUserIdAndTypeAndRefTypeAndRefId(Long userId, String type, String refType, Long refId);

    Optional<CreditLedgerEntity> findFirstByUserIdAndTypeAndRefTypeAndRefIdOrderByIdDesc(
            Long userId, String type, String refType, Long refId);
}
