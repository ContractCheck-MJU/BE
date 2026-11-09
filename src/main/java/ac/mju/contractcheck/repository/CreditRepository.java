package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.LedgerEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * 인메모리 저장소. 영속화 시 V1__init.sql의 {@code credit_wallets}, {@code credit_ledger} 테이블에 매핑한다.
 * 무료 2회 GRANT는 조회 시점이 아니라 최초 가입 트랜잭션에서 기록한다(TC-CRD-001).
 */
@Slf4j
@Repository
public class CreditRepository {
    private final ConcurrentHashMap<String, CreditAccount> accounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<LedgerEntry>> ledger = new ConcurrentHashMap<>();

    public CreditAccount account(String owner) {
        return accounts.computeIfAbsent(owner, key -> {
            ledger.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new LedgerEntry(key, "GRANT", 2, "signup", Instant.now().toString()));
            log.info("무료 이용권 지급 owner={} amount=2", key);
            return new CreditAccount(key, 2, 0);
        });
    }

    public List<LedgerEntry> ledger(String owner) {
        account(owner);
        return List.copyOf(ledger.getOrDefault(owner, List.of()));
    }
}
