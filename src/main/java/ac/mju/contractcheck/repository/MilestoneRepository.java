package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Milestone;
import ac.mju.contractcheck.domain.UserId;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * V1__init.sql의 {@code milestones} 테이블에 저장한다. 소유자 확인은 milestones.user_id로 하며,
 * 다른 회원의 일정은 없는 것과 같게 {@link Optional#empty()}를 돌려준다.
 * 단일 remindDays는 스키마의 {@code remind_days INT[]}에 1원소 배열로 저장한다.
 * 트랜잭션은 열지 않는다. 여러 쓰기를 묶는 트랜잭션은 호출하는 service가 연다.
 */
@Repository
@RequiredArgsConstructor
public class MilestoneRepository implements OwnedDataEraser {

    @NonNull
    private final MilestoneJpaRepository milestones;

    public @NonNull Milestone save(@NonNull Milestone milestone) {
        return toDomain(milestones.save(toEntity(milestone)));
    }

    public @NonNull List<Milestone> saveAll(@NonNull List<Milestone> items) {
        List<MilestoneEntity> entities = items.stream().map(MilestoneRepository::toEntity).toList();
        return milestones.saveAll(entities).stream().map(MilestoneRepository::toDomain).toList();
    }

    public @NonNull List<Milestone> findByOwner(UserId owner) {
        return findByOwner(owner, null, null);
    }

    public @NonNull List<Milestone> findByOwner(UserId owner, LocalDate from, LocalDate to) {
        List<MilestoneEntity> rows;
        if (from != null && to != null) {
            rows = milestones.findByUserIdAndDueDateBetweenOrderByDueDateAscIdAsc(owner.value(), from, to);
        } else if (from != null) {
            rows = milestones.findByUserIdAndDueDateGreaterThanEqualOrderByDueDateAscIdAsc(owner.value(), from);
        } else if (to != null) {
            rows = milestones.findByUserIdAndDueDateLessThanEqualOrderByDueDateAscIdAsc(owner.value(), to);
        } else {
            rows = milestones.findByUserIdOrderByDueDateAscIdAsc(owner.value());
        }
        return rows.stream().map(MilestoneRepository::toDomain).toList();
    }

    public @NonNull List<Milestone> findByContract(UserId owner, long contractId) {
        return milestones.findByUserIdAndContractIdOrderByDueDateAscIdAsc(owner.value(), contractId).stream()
                .map(MilestoneRepository::toDomain)
                .toList();
    }

    public @NonNull Optional<Milestone> findOwned(long id, UserId owner) {
        return milestones.findByIdAndUserId(id, owner.value()).map(MilestoneRepository::toDomain);
    }

    public void delete(long id) {
        milestones.deleteById(id);
    }

    public void deleteByContractAndTypes(UserId owner, long contractId, @NonNull Collection<String> types) {
        milestones.deleteByUserIdAndContractIdAndTypeIn(owner.value(), contractId, types);
    }

    /** 탈퇴 시 소유자의 일정을 모두 지운다(FR-AUTH-03). */
    @Override
    public void deleteAllByOwner(UserId owner) {
        milestones.deleteByUserId(owner.value());
    }

    private static MilestoneEntity toEntity(Milestone milestone) {
        Long entityId = milestone.id() == 0 ? null : milestone.id();
        LocalDate dueDate = milestone.dueDate() == null || milestone.dueDate().isBlank()
                ? null
                : LocalDate.parse(milestone.dueDate());
        return new MilestoneEntity(
                entityId,
                milestone.owner().value(),
                milestone.contractId(),
                milestone.type(),
                milestone.title(),
                dueDate,
                new Integer[] {milestone.remindDays()},
                milestone.done());
    }

    private static Milestone toDomain(MilestoneEntity entity) {
        String dueDate = entity.getDueDate() == null ? "" : entity.getDueDate().toString();
        Integer[] remindDays = entity.getRemindDays();
        int primaryRemindDays = (remindDays != null && remindDays.length > 0 && remindDays[0] != null)
                ? remindDays[0]
                : 0;
        return new Milestone(
                entity.getId(),
                new UserId(entity.getUserId()),
                entity.getContractId(),
                entity.getType(),
                entity.getTitle(),
                dueDate,
                primaryRemindDays,
                entity.isDone());
    }
}
