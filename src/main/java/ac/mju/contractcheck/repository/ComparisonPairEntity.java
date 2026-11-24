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

/**
 * V1__init.sql의 {@code comparison_pairs}. ADDED는 before_clause_id가, DELETED는 after_clause_id가 비어 있고 둘 다 similarity가 없다.
 * 비교가 지워지면 함께 지워진다(V2 ON DELETE CASCADE).
 */
@Entity
@Table(name = "comparison_pairs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ComparisonPairEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long comparisonId;

    private Long beforeClauseId;

    private Long afterClauseId;

    @Column(precision = 5, scale = 4)
    private BigDecimal similarity;

    @Column(length = 10)
    private String changeType;

    private Integer riskDelta;

    ComparisonPairEntity(
            Long comparisonId,
            Long beforeClauseId,
            Long afterClauseId,
            BigDecimal similarity,
            String changeType,
            Integer riskDelta) {
        this.comparisonId = comparisonId;
        this.beforeClauseId = beforeClauseId;
        this.afterClauseId = afterClauseId;
        this.similarity = similarity;
        this.changeType = changeType;
        this.riskDelta = riskDelta;
    }
}
