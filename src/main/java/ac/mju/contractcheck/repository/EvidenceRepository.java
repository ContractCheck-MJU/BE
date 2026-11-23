package ac.mju.contractcheck.repository;

import ac.mju.contractcheck.domain.EvidenceHit;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 조항 임베딩으로 근거(법령·판례 청크)를 찾는다. pgvector native SQL이며 {@code ORDER BY embedding <=> ?}로
 * HNSW(vector_cosine_ops) 인덱스를 쓴다. Spring AI VectorStore 테이블을 따로 두지 않고 legal_chunks를 그대로 쓴다.
 * 공정위 시정 사례(kftc_cases)는 적재 데이터가 생기면 같은 방식으로 추가한다.
 *
 * <p>pgvector 전용 SQL이라 H2 테스트에서는 돌지 않는다. 실제 Postgres 확인은 {@code ./gradlew inferenceTest}다.
 */
@Repository
@RequiredArgsConstructor
public class EvidenceRepository {

    @NonNull
    private final JdbcClient jdbc;

    /** 코사인 거리가 가까운 순으로 limit개를 돌려준다. similarity = 1 - 거리. 임베딩이 없는 청크는 제외한다. */
    public @NonNull List<EvidenceHit> findSimilar(@NonNull float[] embedding, int limit) {
        String vector = toVectorLiteral(embedding);
        return jdbc.sql("""
                        SELECT d.id AS document_id, c.id AS chunk_id, d.doc_type, d.title, d.ref, c.text,
                               1 - (c.embedding <=> CAST(:vector AS vector)) AS similarity
                          FROM legal_chunks c
                          JOIN legal_documents d ON d.id = c.document_id
                         WHERE c.embedding IS NOT NULL
                         ORDER BY c.embedding <=> CAST(:vector AS vector)
                         LIMIT :limit
                        """)
                .param("vector", vector)
                .param("limit", limit)
                .query((rs, rowNumber) -> new EvidenceHit(
                        rs.getLong("document_id"),
                        rs.getLong("chunk_id"),
                        rs.getString("doc_type"),
                        rs.getString("title"),
                        rs.getString("ref") == null ? "" : rs.getString("ref"),
                        rs.getString("text") == null ? "" : rs.getString("text"),
                        rs.getDouble("similarity")))
                .list();
    }

    static String toVectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(embedding[index]);
        }
        return literal.append(']').toString();
    }
}
