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

/** V1__init.sql의 {@code comparisons}. 두 계약 중 하나가 삭제되면 행도 함께 지워진다(V2 ON DELETE CASCADE). */
@Entity
@Table(name = "comparisons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ComparisonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long beforeContractId;

    private Long afterContractId;

    private Instant createdAt;

    ComparisonEntity(Long id, Long userId, Long beforeContractId, Long afterContractId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.beforeContractId = beforeContractId;
        this.afterContractId = afterContractId;
        this.createdAt = createdAt;
    }
}
