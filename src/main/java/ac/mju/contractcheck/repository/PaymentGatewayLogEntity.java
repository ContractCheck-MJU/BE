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

/** V6__payment_gateway_logs.sql의 {@code payment_gateway_logs}. 쌓기만 하고 고치지 않는다. */
@Entity
@Table(name = "payment_gateway_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PaymentGatewayLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(name = "http_status")
    private Integer httpStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    private String response;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    PaymentGatewayLogEntity(Long paymentId, String action, String outcome, Integer httpStatus, String response, Instant createdAt) {
        this.paymentId = paymentId;
        this.action = action;
        this.outcome = outcome;
        this.httpStatus = httpStatus;
        this.response = response;
        this.createdAt = createdAt;
    }
}
