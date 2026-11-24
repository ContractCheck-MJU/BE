package ac.mju.contractcheck.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface MilestoneJpaRepository extends JpaRepository<MilestoneEntity, Long> {

    Optional<MilestoneEntity> findByIdAndUserId(Long id, Long userId);

    List<MilestoneEntity> findByUserIdOrderByDueDateAscIdAsc(Long userId);

    List<MilestoneEntity> findByUserIdAndDueDateGreaterThanEqualOrderByDueDateAscIdAsc(Long userId, LocalDate from);

    List<MilestoneEntity> findByUserIdAndDueDateLessThanEqualOrderByDueDateAscIdAsc(Long userId, LocalDate to);

    List<MilestoneEntity> findByUserIdAndDueDateBetweenOrderByDueDateAscIdAsc(Long userId, LocalDate from, LocalDate to);

    List<MilestoneEntity> findByUserIdAndContractIdOrderByDueDateAscIdAsc(Long userId, Long contractId);

    void deleteByUserIdAndContractIdAndTypeIn(Long userId, Long contractId, Collection<String> types);

    void deleteByUserId(Long userId);
}
