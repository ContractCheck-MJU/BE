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

    /** 저장된 잔여를 바꾼다. 어떻게 차감하는지는 도메인(CreditAccount)이 정하고, 이 클래스는 값만 옮겨 담는다. */
    void changeRemaining(int freeRemaining, int paidRemaining) {
        this.freeRemaining = freeRemaining;
        this.paidRemaining = paidRemaining;
    }

    CreditWalletEntity(Long userId, int freeRemaining, int paidRemaining) {
        this.userId = userId;
        this.freeRemaining = freeRemaining;
        this.paidRemaining = paidRemaining;
    }
}
