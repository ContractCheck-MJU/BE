package ac.mju.contractcheck.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V1__init.sql의 {@code risk_results}. similar_case_ids(BIGINT[])는 공정위 사례 검색이 생기면 매핑한다. */
@Entity
@Table(name = "risk_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class RiskResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long analysisId;

    private Long clauseId;

    @Column(name = "is_risky")
    private boolean risky;

    @Column(length = 10)
    private String riskLevel;

    @Column(length = 50)
    private String riskType;

    @Column(precision = 5, scale = 4)
    private BigDecimal probability;

    RiskResultEntity(Long analysisId, Long clauseId, boolean risky, String riskLevel, String riskType, BigDecimal probability) {
        this.analysisId = analysisId;
        this.clauseId = clauseId;
        this.risky = risky;
        this.riskLevel = riskLevel;
        this.riskType = riskType;
        this.probability = probability;
    }
}
