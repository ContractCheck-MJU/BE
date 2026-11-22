package ac.mju.contractcheck.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V1__init.sql의 {@code clauses}. embedding(VECTOR 1024)은 임베딩 연동(FR-ANL-01)에서 매핑한다. */
@Entity
@Table(name = "clauses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ClauseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long contractId;

    private Integer clauseNo;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String text;

    ClauseEntity(Long contractId, int clauseNo, String title, String text) {
        this.contractId = contractId;
        this.clauseNo = clauseNo;
        this.title = title;
        this.text = text;
    }

    void edit(int clauseNo, String title, String text) {
        this.clauseNo = clauseNo;
        this.title = title;
        this.text = text;
    }
}
