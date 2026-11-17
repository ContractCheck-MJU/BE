package ac.mju.contractcheck.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V1__init.sql의 {@code credit_wallets}. version으로 낙관적 락을 건다(FR-CRD-02). */
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
}
