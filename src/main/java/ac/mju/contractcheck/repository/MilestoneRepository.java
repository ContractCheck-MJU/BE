package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Milestone;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

/**
 * 인메모리 저장소. 영속화 시 V1__init.sql의 {@code milestones} 테이블에 매핑한다
 * (due_date, remind_days INT[], done). 소유자 확인은 milestones.user_id로 한다.
 */
@Repository
public class MilestoneRepository {
    private final AtomicLong sequence = new AtomicLong(1);
    private final ConcurrentHashMap<Long, Milestone> store = new ConcurrentHashMap<>();

    public Milestone save(Milestone milestone) {
        Milestone stored = milestone.id() == 0
                ? new Milestone(sequence.getAndIncrement(), milestone.owner(), milestone.contractId(), milestone.type(), milestone.title(), milestone.dueDate(), milestone.remindDays(), milestone.done())
                : milestone;
        store.put(stored.id(), stored);
        return stored;
    }

    public List<Milestone> findByOwner(String owner) {
        return store.values().stream().filter(item -> item.owner().equals(owner)).toList();
    }

    public List<Milestone> findByContract(String owner, long contractId) {
        return store.values().stream()
                .filter(item -> item.owner().equals(owner) && item.contractId() == contractId)
                .toList();
    }

    public @NonNull Optional<Milestone> findOwned(long id, String owner) {
        return Presence.owned(store.get(id), owner, Milestone::owner);
    }

    public void delete(long id) {
        store.remove(id);
    }
}
