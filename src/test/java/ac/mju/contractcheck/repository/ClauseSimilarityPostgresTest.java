package ac.mju.contractcheck.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ac.mju.contractcheck.domain.ClauseEmbedding;
import ac.mju.contractcheck.domain.ClauseSimilarity;
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
 * TC-CMP-011 두 계약 조항의 코사인 유사도 표를 pgvector({@code <=>}) 한 번의 SQL로 만드는지 실제 Postgres에서 확인한다.
 * 임시 스키마에 Flyway를 적용하고 {@code ./gradlew postgresTest}로만 돈다. 운영 스키마(public)는 건드리지 않는다.
 */
@Tag("postgres")
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
class ClauseSimilarityPostgresTest {

    private static final String SCHEMA = "clause_similarity_pg_test";
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
    ClauseEmbeddingRepository embeddings;

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("TC-CMP-011 임베딩이 있는 조항끼리만 모든 조합의 유사도(1 − 코사인 거리)를 돌려준다")
    void returnsSimilarityForEveryPairWithEmbeddings() {
        long user = insert("users (role) VALUES ('USER')");
        long before = insert("contracts (user_id, title) VALUES (" + user + ", '이전')");
        long after = insert("contracts (user_id, title) VALUES (" + user + ", '이후')");
        long beforeClause = insert("clauses (contract_id, clause_no, text) VALUES (" + before + ", 1, '보증금')");
        long afterSame = insert("clauses (contract_id, clause_no, text) VALUES (" + after + ", 1, '보증금')");
        long afterOrthogonal = insert("clauses (contract_id, clause_no, text) VALUES (" + after + ", 2, '반려동물')");
        insert("clauses (contract_id, clause_no, text) VALUES (" + after + ", 3, '임베딩 없음')");
        embeddings.saveAll(List.of(
                new ClauseEmbedding(beforeClause, unit(0)),
                new ClauseEmbedding(afterSame, unit(0)),
                new ClauseEmbedding(afterOrthogonal, unit(1))));

        List<ClauseSimilarity> similarities = embeddings.findSimilarities(before, after);

        assertThat(similarities).hasSize(2);
        assertThat(similarities).filteredOn(item -> item.afterClauseId() == afterSame).singleElement()
                .satisfies(item -> {
                    assertThat(item.beforeClauseId()).isEqualTo(beforeClause);
                    assertThat(item.similarity()).isCloseTo(1.0, within(1e-6));
                });
        assertThat(similarities).filteredOn(item -> item.afterClauseId() == afterOrthogonal).singleElement()
                .satisfies(item -> assertThat(item.similarity()).isCloseTo(0.0, within(1e-6)));
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
