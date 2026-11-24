package ac.mju.contractcheck.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V1__init.sql의 {@code credit_ledger}. 가입 지급처럼 참조 대상이 없으면 ref_type·ref_id는 비운다. */
@Entity
@Table(name = "credit_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class CreditLedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 10)
    private String type;

    private int amount;

    @Column(length = 20)
    private String refType;

    private Long refId;

    @Column(length = 10)
    private String creditSource;

    private Instant createdAt;

    CreditLedgerEntity(Long userId, String type, int amount, String refType, Long refId, String creditSource, Instant now) {
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.refType = refType;
        this.refId = refId;
        this.creditSource = creditSource;
        this.createdAt = now;
    }
}
