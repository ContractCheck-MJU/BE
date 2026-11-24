package ac.mju.contractcheck.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V1__init.sql의 {@code analyses}. 소유자는 contract_id → contracts.user_id로 따라간다. */
@Entity
@Table(name = "analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long contractId;

    @Column(length = 50)
    private String modelVersion;

    @Column(precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(length = 20)
    private String status;

    private Instant createdAt;

    /** id가 null이면 새 행, 있으면 그 행을 도메인 값으로 갱신(merge)한다. */
    AnalysisEntity(Long id, Long contractId, String modelVersion, BigDecimal overallScore, String summary, String status, Instant createdAt) {
        this.id = id;
        this.contractId = contractId;
        this.modelVersion = modelVersion;
        this.overallScore = overallScore;
        this.summary = summary;
        this.status = status;
        this.createdAt = createdAt;
    }
}
