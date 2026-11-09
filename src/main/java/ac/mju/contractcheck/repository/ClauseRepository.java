package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Clause;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

/**
 * 인메모리 저장소. 영속화 시 V1__init.sql의 {@code clauses} 테이블에 매핑한다(embedding VECTOR 1024 포함).
 * 소유자 확인은 clauses.contract_id → contracts.user_id로 한다.
 */
@Repository
public class ClauseRepository {
    private final AtomicLong sequence = new AtomicLong(1);
    private final ConcurrentHashMap<Long, Clause> store = new ConcurrentHashMap<>();

    public Clause save(Clause clause) {
        Clause stored = clause.id() == 0
                ? new Clause(sequence.getAndIncrement(), clause.contractId(), clause.owner(), clause.no(), clause.title(), clause.text(), clause.riskLevel())
                : clause;
        store.put(stored.id(), stored);
        return stored;
    }

    public List<Clause> findByContract(long contractId, String owner) {
        return store.values().stream()
                .filter(clause -> clause.contractId() == contractId && clause.owner().equals(owner))
                .sorted(Comparator.comparingInt(Clause::no))
                .toList();
    }

    public @NonNull Optional<Clause> findOwned(long id, String owner) {
        return Presence.owned(store.get(id), owner, Clause::owner);
    }
}
