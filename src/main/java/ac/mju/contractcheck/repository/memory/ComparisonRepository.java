package ac.mju.contractcheck.repository.memory;

import ac.mju.contractcheck.domain.Comparison;
import ac.mju.contractcheck.repository.OwnedDataEraser;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

/**
 * 인메모리 저장소. 영속화 시 V1__init.sql의 {@code comparisons}, {@code comparison_pairs} 테이블에 매핑한다.
 * 소유자 확인은 comparisons.user_id로 한다.
 */
@Repository
public class ComparisonRepository implements OwnedDataEraser {
    private final AtomicLong sequence = new AtomicLong(1);
    private final ConcurrentHashMap<Long, Comparison> store = new ConcurrentHashMap<>();

    public Comparison save(Comparison comparison) {
        Comparison stored = comparison.id() == 0
                ? new Comparison(sequence.getAndIncrement(), comparison.owner(), comparison.beforeContractId(), comparison.afterContractId())
                : comparison;
        store.put(stored.id(), stored);
        return stored;
    }

    public @NonNull Optional<Comparison> findOwned(long id, String owner) {
        return Presence.owned(store.get(id), owner, Comparison::owner);
    }

    /** 탈퇴 시 소유자의 데이터를 모두 지운다(FR-AUTH-03). */
    @Override
    public void deleteAllByOwner(String owner) {
        store.values().removeIf(item -> item.owner().equals(owner));
    }
}
