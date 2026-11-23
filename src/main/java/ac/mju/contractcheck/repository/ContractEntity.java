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

/** V1__init.sql의 {@code contracts}. ocr_raw(JSONB)는 OCR 구현(FR-UPL-02)에서 매핑한다. */
@Entity
@Table(name = "contracts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ContractEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(length = 200)
    private String title;

    @Column(length = 20)
    private String contractType;

    @Column(name = "s3_key", length = 500)
    private String s3Key;

    @Column(length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String maskedText;

    @Column(length = 200)
    private String errorMessage;

    private Instant createdAt;

    /** id가 null이면 새 행, 있으면 그 행을 도메인 값으로 갱신(merge)한다. ocr_raw처럼 매핑하지 않은 컬럼은 건드리지 않는다. */
    ContractEntity(Long id, Long userId, String title, String contractType, String s3Key, String status,
            String maskedText, String errorMessage, Instant createdAt) {
        this.id = id;
        this.maskedText = maskedText;
        this.errorMessage = errorMessage;
        this.userId = userId;
        this.title = title;
        this.contractType = contractType;
        this.s3Key = s3Key;
        this.status = status;
        this.createdAt = createdAt;
    }
}
