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

/** V1__init.sql의 {@code explanation_citations}. 설명이 지워지면 함께 지워진다(V2 ON DELETE CASCADE). */
@Entity
@Table(name = "explanation_citations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ExplanationCitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long explanationId;

    @Column(length = 20)
    private String sourceType;

    private Long sourceId;

    @Column(precision = 5, scale = 4)
    private BigDecimal score;

    ExplanationCitationEntity(Long explanationId, String sourceType, Long sourceId, BigDecimal score) {
        this.explanationId = explanationId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.score = score;
    }
}
