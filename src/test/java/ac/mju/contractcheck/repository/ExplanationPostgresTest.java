package ac.mju.contractcheck.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ac.mju.contractcheck.domain.ClauseEmbedding;
import ac.mju.contractcheck.domain.EvidenceHit;
import ac.mju.contractcheck.domain.Explanation;
import ac.mju.contractcheck.domain.ExplanationCitation;
import ac.mju.contractcheck.domain.UserId;
import java.time.Instant;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * FR-EXP-02 저장된 조항 임베딩으로 근거를 찾는 pgvector SQL과, 설명·인용 엔티티가 Flyway 스키마와 맞는지(ddl-auto=validate)
 * 실제 Postgres에서 확인한다. 임시 스키마에 Flyway를 적용하고 {@code ./gradlew postgresTest}로만 돈다.
 */
@Tag("postgres")
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
class ExplanationPostgresTest {

    private static final String SCHEMA = "explanation_pg_test";
    private static final String URL = env("DB_URL", "jdbc:postgresql://localhost:5432/contractcheck");
    private static final String USER = env("DB_USERNAME", "contractcheck");
    private static final String PASSWORD = env("DB_PASSWORD", "");

    @BeforeAll
    static void migrate() {
        dropSchema();
        Flyway.configure().dataSource(URL, USER, PASSWORD).schemas(SCHEMA, "public").load().migrate();
    }

    @AfterAll
    static void cleanUp() {
        dropSchema();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL + (URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA + ",public");
    }

    @Autowired
    EvidenceRepository evidence;
    @Autowired
    ClauseEmbeddingRepository embeddings;
    @Autowired
    ExplanationRepository explanations;
    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("FR-EXP-02 조항에 저장된 임베딩과 가까운 법령 청크 순으로 근거를 찾고, 임베딩이 없는 조항은 빈 목록이다")
    void findsEvidenceByStoredClauseEmbedding() {
        long user = insert("users (role) VALUES ('USER')");
        long contract = insert("contracts (user_id, title) VALUES (" + user + ", '계약')");
        long clause = insert("clauses (contract_id, clause_no, text) VALUES (" + contract + ", 1, '보증금')");
        long withoutEmbedding = insert("clauses (contract_id, clause_no, text) VALUES (" + contract + ", 2, '임베딩 없음')");
        embeddings.saveAll(List.of(new ClauseEmbedding(clause, unit(0))));
        long law = insert("legal_documents (doc_type, source_id, title, ref) VALUES ('LAW', 'l1', '주택임대차보호법', '제3조')");
        long precedent = insert("legal_documents (doc_type, source_id, title) VALUES ('PRECEDENT', 'p1', '판례')");
        long near = chunk(law, "보증금 반환", unit(0));
        long far = chunk(precedent, "무관한 판례", unit(1));

        List<EvidenceHit> hits = evidence.findSimilarToClause(clause, 5);

        assertThat(hits).extracting(EvidenceHit::chunkId).containsExactly(near, far);
        assertThat(hits.getFirst().similarity()).isCloseTo(1.0, within(1e-6));
        assertThat(hits.getFirst().docType()).isEqualTo("LAW");
        assertThat(hits.getFirst().ref()).isEqualTo("제3조");
        assertThat(hits.get(1).similarity()).isCloseTo(0.0, within(1e-6));
        assertThat(evidence.findSimilarToClause(withoutEmbedding, 5)).isEmpty();
    }

    @Test
    @DisplayName("FR-EXP-02 설명과 인용을 Flyway 스키마에 저장하고 소유자만 다시 읽는다")
    void savesExplanationOnMigratedSchema() {
        long user = insert("users (role) VALUES ('USER')");
        long contract = insert("contracts (user_id, title) VALUES (" + user + ", '계약')");
        long clause = insert("clauses (contract_id, clause_no, text) VALUES (" + contract + ", 1, '보증금')");
        UserId owner = new UserId(user);

        Explanation saved = explanations.save(new Explanation(0, clause, owner, "설명", "제안", "claude-sonnet-5-5",
                "exp-v1", Instant.now(), List.of(new ExplanationCitation("LAW", 1L, 0.87654))));

        assertThat(explanations.findOwned(saved.id(), owner).orElseThrow().citations())
                .containsExactly(new ExplanationCitation("LAW", 1L, 0.8765));
        assertThat(explanations.findOwned(saved.id(), new UserId(user + 1))).isEmpty();
        assertThat(explanations.countByClause(clause)).isEqualTo(1);
    }

    private long chunk(long documentId, String text, float[] embedding) {
        return jdbc.sql("INSERT INTO legal_chunks (document_id, chunk_no, text, embedding) "
                        + "VALUES (:document, 1, :text, CAST(:vector AS vector)) RETURNING id")
                .param("document", documentId)
                .param("text", text)
                .param("vector", EvidenceRepository.toVectorLiteral(embedding))
                .query(Long.class)
                .single();
    }

    /** 1024차원에서 index 자리만 1인 단위 벡터. 서로 다른 index끼리는 코사인 유사도 0이다. */
    private static float[] unit(int index) {
        float[] vector = new float[1024];
        vector[index] = 1f;
        return vector;
    }

    private long insert(String intoClause) {
        return jdbc.sql("INSERT INTO " + intoClause + " RETURNING id").query(Long.class).single();
    }

    private static void dropSchema() {
        JdbcClient.create(new DriverManagerDataSource(URL, USER, PASSWORD)).sql("drop schema if exists " + SCHEMA + " cascade").update();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
