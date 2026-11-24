package ac.mju.contractcheck.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CreditLedgerJpaRepository extends JpaRepository<CreditLedgerEntity, Long> {

    List<CreditLedgerEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    boolean existsByUserIdAndTypeAndRefTypeAndRefId(Long userId, String type, String refType, Long refId);
}
