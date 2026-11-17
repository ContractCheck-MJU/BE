package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.LedgerEntry;
import java.time.Instant;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * V1__init.sql의 {@code credit_wallets}, {@code credit_ledger}에 저장한다.
 * 무료 2회 GRANT는 조회 시점이 아니라 최초 가입 트랜잭션에서 {@link #grantSignup}으로 기록한다(TC-CRD-001).
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CreditRepository {

    private static final int SIGNUP_FREE_CREDITS = 2;
    private static final String TYPE_GRANT = "GRANT";

    @NonNull
    private final CreditWalletJpaRepository wallets;
    @NonNull
    private final CreditLedgerJpaRepository ledgers;

    /** 가입한 회원에게 무료 이용권 지갑과 GRANT 원장 1건을 만든다. 가입 트랜잭션 안에서 호출한다. */
    public void grantSignup(long userId) {
        wallets.save(new CreditWalletEntity(userId, SIGNUP_FREE_CREDITS, 0));
        ledgers.save(new CreditLedgerEntity(userId, TYPE_GRANT, SIGNUP_FREE_CREDITS, null, null, Instant.now()));
        log.info("무료 이용권 지급 userId={} amount={}", userId, SIGNUP_FREE_CREDITS);
    }

    /** 지갑이 없으면 조회 시 만들어 주지 않고 가입 오류로 본다. 전용 오류 코드는 스펙 미정이다. */
    public @NonNull CreditAccount account(String owner) {
        return OwnerId.parse(owner)
                .flatMap(wallets::findById)
                .map(wallet -> new CreditAccount(owner, wallet.getFreeRemaining(), wallet.getPaidRemaining()))
                .orElseThrow(() -> new IllegalStateException("이용권 지갑이 없는 회원이다"));
    }

    public @NonNull List<LedgerEntry> ledger(String owner) {
        return OwnerId.parse(owner)
                .map(userId -> ledgers.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                        .map(entry -> toLedgerEntry(owner, entry))
                        .toList())
                .orElse(List.of());
    }

    private LedgerEntry toLedgerEntry(String owner, CreditLedgerEntity entry) {
        String refId = entry.getRefId() == null ? "" : String.valueOf(entry.getRefId());
        return new LedgerEntry(owner, entry.getType(), entry.getAmount(), refId, entry.getCreatedAt().toString());
    }
}
