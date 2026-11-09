package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Analysis;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

/**
 * 인메모리 저장소. 영속화 시 V1__init.sql의 {@code analyses}, {@code risk_results} 테이블에 매핑한다.
 * 소유자 확인은 analyses.contract_id → contracts.user_id로 한다.
 */
@Repository
public class AnalysisRepository {
    private final AtomicLong sequence = new AtomicLong(1);
    private final ConcurrentHashMap<Long, Analysis> store = new ConcurrentHashMap<>();

    public Analysis save(Analysis analysis) {
        Analysis stored = analysis.id() == 0
                ? new Analysis(sequence.getAndIncrement(), analysis.contractId(), analysis.owner(), analysis.status(), analysis.overallScore(), analysis.summary())
                : analysis;
        store.put(stored.id(), stored);
        return stored;
    }

    public @NonNull Optional<Analysis> findOwned(long id, String owner) {
        return Presence.owned(store.get(id), owner, Analysis::owner);
    }
}
