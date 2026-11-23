package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.CreditUseResult;
import ac.mju.contractcheck.domain.LedgerEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
    private static final String TYPE_USE = "USE";
    private static final String REF_ANALYSIS = "ANALYSIS";

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

    /**
     * 분석 1건에 대해 이용권 1회를 차감하고 USE 원장을 남긴다. 차감 규칙(무료 우선 → 유료)은 {@link CreditAccount#useOne()}이 정하고,
     * 저장소는 지갑을 읽어 도메인에 맡긴 결과를 저장한다. 지갑 version으로 낙관적 락을 건다.
     * 동시에 같은 지갑을 바꾼 요청이 있으면 {@link ObjectOptimisticLockingFailureException}이 나므로 호출자가 다시 시도한다.
     */
    @Transactional
    public @NonNull CreditUseResult use(String owner, long analysisId) {
        long userId = OwnerId.parse(owner).orElseThrow(() -> new IllegalStateException("이용권 지갑이 없는 회원이다"));
        CreditWalletEntity wallet = wallets.findById(userId)
                .orElseThrow(() -> new IllegalStateException("이용권 지갑이 없는 회원이다"));
        Optional<CreditAccount> after = toAccount(owner, wallet).useOne();
        if (after.isEmpty()) {
            return CreditUseResult.INSUFFICIENT;
        }
        wallet.changeRemaining(after.get().freeRemaining(), after.get().paidRemaining());
        wallets.saveAndFlush(wallet);
        ledgers.save(new CreditLedgerEntity(userId, TYPE_USE, 1, REF_ANALYSIS, analysisId, Instant.now()));
        log.info("이용권 차감 userId={} analysisId={}", userId, analysisId);
        return CreditUseResult.USED;
    }

    /** 지갑이 없으면 조회 시 만들어 주지 않고 가입 오류로 본다. 전용 오류 코드는 스펙 미정이다. */
    public @NonNull CreditAccount account(String owner) {
        return OwnerId.parse(owner)
                .flatMap(wallets::findById)
                .map(wallet -> toAccount(owner, wallet))
                .orElseThrow(() -> new IllegalStateException("이용권 지갑이 없는 회원이다"));
    }

    public @NonNull List<LedgerEntry> ledger(String owner) {
        return OwnerId.parse(owner)
                .map(userId -> ledgers.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                        .map(entry -> toLedgerEntry(owner, entry))
                        .toList())
                .orElse(List.of());
    }

    private CreditAccount toAccount(String owner, CreditWalletEntity wallet) {
        return new CreditAccount(owner, wallet.getFreeRemaining(), wallet.getPaidRemaining());
    }

    private LedgerEntry toLedgerEntry(String owner, CreditLedgerEntity entry) {
        String refId = entry.getRefId() == null ? "" : String.valueOf(entry.getRefId());
        return new LedgerEntry(owner, entry.getType(), entry.getAmount(), refId, entry.getCreatedAt().toString());
    }
}
