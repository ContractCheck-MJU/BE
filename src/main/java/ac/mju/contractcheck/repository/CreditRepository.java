package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.LedgerEntry;
import ac.mju.contractcheck.domain.LedgerRefType;
import ac.mju.contractcheck.domain.LedgerType;
import ac.mju.contractcheck.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * V1__init.sql의 {@code credit_wallets}, {@code credit_ledger} 행을 읽고 쓴다. 무엇을 얼마나 지급·차감할지, 차감 순서,
 * 지갑이 없을 때의 판단 같은 업무 규칙은 {@code CreditService}와 {@code domain}이 정하고 이 클래스는 저장만 한다.
 * 트랜잭션은 열지 않는다. 지갑 갱신과 원장 기록을 묶는 일은 호출하는 service가 한다.
 */
@Repository
@RequiredArgsConstructor
public class CreditRepository {

    @NonNull
    private final CreditWalletJpaRepository wallets;
    @NonNull
    private final CreditLedgerJpaRepository ledgers;

    /** 지갑 행을 만든다. 가입 트랜잭션 안에서 호출한다. */
    public void createAccount(@NonNull CreditAccount account) {
        wallets.save(new CreditWalletEntity(account.owner().value(), account.freeRemaining(), account.paidRemaining()));
    }

    /** 지갑을 version과 함께 읽는다. 이어서 {@link #saveAccount}에 그대로 넘기면 읽은 시점의 version으로 낙관적 락을 검사한다. */
    public @NonNull Optional<CreditAccount> findAccount(UserId owner) {
        return wallets.findById(owner.value()).map(wallet -> toAccount(owner, wallet));
    }

    /**
     * 읽어 온 지갑(version 포함)의 잔여를 저장하고 바로 flush한다. 읽은 뒤 다른 요청이 지갑을 바꿔 version이 다르면
     * ObjectOptimisticLockingFailureException이다.
     */
    public void saveAccount(@NonNull CreditAccount account) {
        wallets.saveAndFlush(new CreditWalletEntity(
                account.owner().value(), account.freeRemaining(), account.paidRemaining(), account.version()));
    }

    /** 원장에 한 줄을 남긴다. 종류와 참조 이름이 DB 값(문자열)으로 저장된다. refType·refId는 없을 수 있다. */
    public void appendLedger(UserId owner, @NonNull LedgerType type, int amount, LedgerRefType refType, Long refId) {
        ledgers.save(new CreditLedgerEntity(
                owner.value(), type.name(), amount, refType == null ? null : refType.name(), refId, Instant.now()));
    }

    /** 같은 종류·참조의 원장이 이미 있는지 본다. 복구의 중복 방지에 쓴다. */
    public boolean hasLedger(UserId owner, @NonNull LedgerType type, @NonNull LedgerRefType refType, long refId) {
        return ledgers.existsByUserIdAndTypeAndRefTypeAndRefId(owner.value(), type.name(), refType.name(), refId);
    }

    public @NonNull List<LedgerEntry> ledger(UserId owner) {
        return ledgers.findByUserIdOrderByCreatedAtDescIdDesc(owner.value()).stream()
                .map(entry -> toLedgerEntry(owner, entry))
                .toList();
    }

    private CreditAccount toAccount(UserId owner, CreditWalletEntity wallet) {
        return new CreditAccount(owner, wallet.getFreeRemaining(), wallet.getPaidRemaining(), wallet.getVersion());
    }

    private LedgerEntry toLedgerEntry(UserId owner, CreditLedgerEntity entry) {
        String refId = entry.getRefId() == null ? "" : String.valueOf(entry.getRefId());
        return new LedgerEntry(owner, entry.getType(), entry.getAmount(), refId, entry.getCreatedAt().toString());
    }
}
