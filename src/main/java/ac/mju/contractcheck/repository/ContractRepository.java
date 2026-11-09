package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.Contract;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

/**
 * 인메모리 저장소. 영속화 시 V1__init.sql의 {@code contracts} 테이블에 매핑한다
 * (s3_key, status, masked_text, ocr_raw). 소유자 확인은 contracts.user_id로 한다.
 */
@Repository
public class ContractRepository {

    private final AtomicLong sequence = new AtomicLong(1);
    private final ConcurrentHashMap<Long, Contract> store = new ConcurrentHashMap<>();

    public Contract save(Contract contract) {
        Contract stored = contract.id() == 0
                ? new Contract(sequence.getAndIncrement(), contract.owner(), contract.title(), contract.contractType(),
                contract.status(), contract.maskedText(), contract.createdAt(), contract.errorMessage())
                : contract;
        store.put(stored.id(), stored);
        return stored;
    }

    public @NonNull Optional<Contract> findOwned(long id, String owner) {
        return Presence.owned(store.get(id), owner, Contract::owner);
    }

    public List<Contract> findByOwner(String owner) {
        return store.values().stream()
                .filter(contract -> contract.owner().equals(owner))
                .sorted(Comparator.comparing(Contract::createdAt).reversed())
                .toList();
    }

    public void delete(long id) {
        store.remove(id);
    }
}
