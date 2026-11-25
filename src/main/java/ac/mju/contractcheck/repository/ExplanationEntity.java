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

/** V1__init.sql의 {@code explanations}. 조항이 지워지면 함께 지워진다(V2 ON DELETE CASCADE). */
@Entity
@Table(name = "explanations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ExplanationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long clauseId;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(columnDefinition = "TEXT")
    private String suggestion;

    @Column(length = 50)
    private String llmModel;

    @Column(length = 20)
    private String promptVersion;

    private Instant createdAt;

    ExplanationEntity(
            Long clauseId, String text, String suggestion, String llmModel, String promptVersion, Instant createdAt) {
        this.clauseId = clauseId;
        this.text = text;
        this.suggestion = suggestion;
        this.llmModel = llmModel;
        this.promptVersion = promptVersion;
        this.createdAt = createdAt;
    }
}
