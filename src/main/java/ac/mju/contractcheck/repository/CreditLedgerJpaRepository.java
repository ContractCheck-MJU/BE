package ac.mju.contractcheck.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface CreditLedgerJpaRepository extends JpaRepository<CreditLedgerEntity, Long> {

    List<CreditLedgerEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    boolean existsByUserIdAndTypeAndRefTypeAndRefId(Long userId, String type, String refType, Long refId);

    Optional<CreditLedgerEntity> findFirstByUserIdAndTypeAndRefTypeAndRefIdOrderByIdDesc(
            Long userId, String type, String refType, Long refId);
}
