package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.IngestJob;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

/**
 * 인메모리 저장소. 영속화 시 V1__init.sql의 {@code ingest_jobs} 테이블에 매핑한다.
 */
@Repository
public class IngestJobRepository {
    private final AtomicLong sequence = new AtomicLong(1);
    private final ConcurrentHashMap<Long, IngestJob> store = new ConcurrentHashMap<>();

    public IngestJob save(IngestJob job) {
        IngestJob stored = job.id() == 0
                ? new IngestJob(sequence.getAndIncrement(), job.status(), job.processed(), job.failed(), job.inserted())
                : job;
        store.put(stored.id(), stored);
        return stored;
    }

    public @NonNull Optional<IngestJob> find(long id) {
        return Presence.present(store.get(id));
    }
}
