package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Explanation;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

/**
 * 인메모리 저장소. 영속화 시 V1__init.sql의 {@code explanations}, {@code explanation_citations} 테이블에 매핑한다.
 * 소유자 확인은 explanations.clause_id → contracts.user_id로 한다.
 */
@Repository
public class ExplanationRepository {
    private final AtomicLong sequence = new AtomicLong(1);
    private final ConcurrentHashMap<Long, Explanation> store = new ConcurrentHashMap<>();

    public Explanation save(Explanation explanation) {
        Explanation stored = explanation.id() == 0
                ? new Explanation(sequence.getAndIncrement(), explanation.clauseId(), explanation.owner(), explanation.tone(), explanation.text(), explanation.suggestion())
                : explanation;
        store.put(stored.id(), stored);
        return stored;
    }

    public @NonNull Optional<Explanation> findOwned(long id, String owner) {
        return Presence.owned(store.get(id), owner, Explanation::owner);
    }
}
