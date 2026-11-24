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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * V1__init.sql의 {@code payments}. order_id·payment_key는 유일하므로 승인 전 payment_key는 빈 문자열이 아니라 null로 저장한다.
 * raw(JSONB)는 토스 승인 응답 원문이다. 응답에 카드 정보가 들어 있을 수 있어 로그에는 남기지 않는다.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long productId;

    @Column(length = 64, unique = true)
    private String orderId;

    @Column(length = 200, unique = true)
    private String paymentKey;

    private int amount;

    @Column(length = 20)
    private String status;

    @Column(length = 30)
    private String method;

    private Instant approvedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String raw;

    PaymentEntity(Long id, Long userId, Long productId, String orderId, String paymentKey, int amount, String status,
            String method, Instant approvedAt, String raw) {
        this.id = id;
        this.userId = userId;
        this.productId = productId;
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.status = status;
        this.method = method;
        this.approvedAt = approvedAt;
        this.raw = raw;
    }
}
