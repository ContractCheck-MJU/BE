package ac.mju.contractcheck.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V1__init.sql의 {@code credit_wallets} 매핑. version으로 낙관적 락을 건다(FR-CRD-02). 업무 규칙은 두지 않는다. */
@Entity
@Table(name = "credit_wallets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class CreditWalletEntity {

    @Id
    private Long userId;

    private int freeRemaining;

    private int paidRemaining;

    @Version
    private Long version;

    CreditWalletEntity(Long userId, int freeRemaining, int paidRemaining) {
        this.userId = userId;
        this.freeRemaining = freeRemaining;
        this.paidRemaining = paidRemaining;
    }

    /** 읽어 둔 지갑을 도메인 값으로 다시 만든다. version이 저장된 값과 다르면 저장할 때 낙관적 락 충돌이다. */
    CreditWalletEntity(Long userId, int freeRemaining, int paidRemaining, Long version) {
        this(userId, freeRemaining, paidRemaining);
        this.version = version;
    }
}
