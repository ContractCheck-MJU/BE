package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.ClauseEmbedding;
import ac.mju.contractcheck.domain.ClauseSimilarity;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code clauses.embedding}(pgvector)을 쓴다. VECTOR 타입은 JPA 엔티티에 매핑하지 않고 네이티브 SQL로만 다룬다.
 * H2에는 vector 타입이 없어 이 클래스는 실제 PostgreSQL에서만 동작하며 {@code postgresTest}로 확인한다.
 */
@Repository
@RequiredArgsConstructor
public class ClauseEmbeddingRepository {

    @NonNull
    private final JdbcClient jdbc;

    /** 조항 id에 임베딩을 저장한다. 없는 조항 id는 건너뛴다(갱신 행이 0). */
    public void saveAll(@NonNull List<ClauseEmbedding> embeddings) {
        for (ClauseEmbedding item : embeddings) {
            jdbc.sql("UPDATE clauses SET embedding = CAST(:vector AS vector) WHERE id = :id")
                    .param("vector", EvidenceRepository.toVectorLiteral(item.embedding()))
                    .param("id", item.clauseId())
                    .update();
        }
    }

    /**
     * FR-CMP-01 두 계약의 조항끼리 모든 조합의 코사인 유사도(1 − {@code <=>})를 SQL 한 번으로 구한다.
     * 임베딩이 없는 조항은 빠진다. 소유자 확인은 호출하는 service가 먼저 한다.
     */
    public @NonNull List<ClauseSimilarity> findSimilarities(long beforeContractId, long afterContractId) {
        return jdbc.sql("""
                        SELECT b.id AS before_clause_id, a.id AS after_clause_id,
                               1 - (b.embedding <=> a.embedding) AS similarity
                          FROM clauses b
                          CROSS JOIN clauses a
                         WHERE b.contract_id = :before
                           AND a.contract_id = :after
                           AND b.embedding IS NOT NULL
                           AND a.embedding IS NOT NULL
                        """)
                .param("before", beforeContractId)
                .param("after", afterContractId)
                .query((rs, rowNumber) -> new ClauseSimilarity(
                        rs.getLong("before_clause_id"), rs.getLong("after_clause_id"), rs.getDouble("similarity")))
                .list();
    }
}
