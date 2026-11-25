package ac.mju.contractcheck.domain;

import ac.mju.contractcheck.domain.enums.CreditSource;
import java.util.Optional;
import lombok.NonNull;

/**
 * 회원의 이용권 잔여. {@code version}은 저장소가 낙관적 락에 쓰는 값으로, 읽은 값을 그대로 운반해 저장할 때 충돌을 검사한다(새 지갑은 null). 불변이며, 잔여가 음수인 계정은 만들 수 없다(정합성).
 * 차감 규칙(무료 우선 → 유료)과 복구 규칙(차감 출처별 +1)은 여기에 둔다. DB·동시성(낙관적 락)은 이 클래스가 모르고 저장소가 다룬다.
 */
public record   CreditAccount(UserId owner, int freeRemaining, int paidRemaining, Long version) {

    /** 아직 저장되지 않은 새 지갑(version 없음). */
    public CreditAccount(UserId owner, int freeRemaining, int paidRemaining) {
        this(owner, freeRemaining, paidRemaining, null);
    }

    public CreditAccount {
        if (freeRemaining < 0 || paidRemaining < 0) {
            throw new IllegalArgumentException("이용권 잔여는 음수일 수 없습니다.");
        }
    }

    /** 무료가 있으면 무료부터, 없으면 유료에서 1회 차감한 새 계정과 차감 출처를 돌려준다. 둘 다 없으면 비어 있다. */
    public Optional<CreditDeduction> deductOne() {
        if (freeRemaining > 0) {
            return Optional.of(new CreditDeduction(
                    new CreditAccount(owner, freeRemaining - 1, paidRemaining, version), CreditSource.FREE));
        }
        if (paidRemaining > 0) {
            return Optional.of(new CreditDeduction(
                    new CreditAccount(owner, freeRemaining, paidRemaining - 1, version), CreditSource.PAID));
        }
        return Optional.empty();
    }

    /** 결제로 산 유료 이용권을 {@code count}회 더한 새 계정을 돌려준다. 횟수는 1 이상이어야 한다. */
    public CreditAccount grantPaid(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("지급 횟수는 1 이상이어야 합니다: " + count);
        }
        return new CreditAccount(owner, freeRemaining, paidRemaining + count, version);
    }

    /** 지정한 출처(FREE·PAID)의 잔여를 1회 복구한 새 계정을 돌려준다. */
    public CreditAccount restoreOne(@NonNull CreditSource source) {
        return switch (source) {
            case FREE -> new CreditAccount(owner, freeRemaining + 1, paidRemaining, version);
            case PAID -> new CreditAccount(owner, freeRemaining, paidRemaining + 1, version);
        };
    }
}
