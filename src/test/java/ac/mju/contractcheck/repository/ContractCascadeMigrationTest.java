package ac.mju.contractcheck.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 실제 Postgres에 임시 스키마를 만들어 Flyway 마이그레이션을 처음부터 적용하고, 계약 삭제가 하위 데이터에 미치는 영향을 확인한다.
 * H2 테스트는 엔티티에서 스키마를 만들어 FK·cascade가 없으므로 이 규칙은 여기서만 검증된다.
 * {@code ./gradlew postgresTest}로만 돌고(docker compose의 postgres 필요), 운영 스키마(public)는 건드리지 않는다.
 */
@Tag("postgres")
class ContractCascadeMigrationTest {

    private static final String SCHEMA = "migration_test";

    private static JdbcClient jdbc;

    @BeforeAll
    static void migrate() {
        String url = env("DB_URL", "jdbc:postgresql://localhost:5432/contractcheck");
        String user = env("DB_USERNAME", "contractcheck");
        String password = env("DB_PASSWORD", "");
        dropSchema(url, user, password);
        // public을 검색 경로에 함께 둬야 V1의 vector 타입을 찾는다. 기본 스키마(테이블이 만들어지는 곳)는 첫 번째다.
        Flyway.configure().dataSource(url, user, password).schemas(SCHEMA, "public").load().migrate();
        String scoped = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA + ",public";
        jdbc = JdbcClient.create(new DriverManagerDataSource(scoped, user, password));
    }

    /** 테스트끼리 데이터를 공유하지 않도록 매번 모두 비운다(users를 가리키는 모든 테이블이 함께 비워진다). */
    @BeforeEach
    void emptyTables() {
        jdbc.sql("truncate table users restart identity cascade").update();
    }

    @AfterAll
    static void cleanUp() {
        dropSchema(env("DB_URL", "jdbc:postgresql://localhost:5432/contractcheck"), env("DB_USERNAME", "contractcheck"), env("DB_PASSWORD", ""));
    }

    @Test
    @DisplayName("계약을 지우면 조항·분석·위험 결과·설명·인용·비교(전/후)·비교 쌍이 함께 지워지고 다른 계약과 회원은 그대로다")
    void deletingContractRemovesAllDescendants() {
        long user = insert("users", "(email, role) values ('a@test', 'USER')");
        long mine = contractWithEverything(user);
        long other = contractWithEverything(user);

        jdbc.sql("delete from contracts where id = ?").param(mine).update();

        // 각 하위 테이블에는 다른 계약 한 세트만 남는다(세트당 조항 2개, 나머지 1개씩).
        assertThat(count("contracts")).isEqualTo(1);
        assertThat(count("clauses")).isEqualTo(2);
        assertThat(count("clauses where contract_id = " + other)).isEqualTo(2);
        for (String table : new String[] {"analyses", "risk_results", "explanations", "explanation_citations", "comparisons", "comparison_pairs"}) {
            assertThat(count(table)).as(table).isEqualTo(1);
        }
        assertThat(count("users")).as("회원은 지워지지 않는다").isEqualTo(1);
    }

    @Test
    @DisplayName("계약을 지워도 일정은 남고 계약 연결만 끊긴다")
    void milestonesSurviveWithoutContract() {
        long user = insert("users", "(email, role) values ('b@test', 'USER')");
        long contract = insert("contracts", "(user_id, title) values (" + user + ", 't')");
        long milestone = insert("milestones", "(user_id, contract_id, title) values (" + user + ", " + contract + ", '만기')");

        jdbc.sql("delete from contracts where id = ?").param(contract).update();

        assertThat(count("milestones where id = " + milestone)).as("일정 행은 남는다").isEqualTo(1);
        assertThat(count("milestones where id = " + milestone + " and contract_id is null")).as("계약 연결만 끊긴다").isEqualTo(1);
    }

    @Test
    @DisplayName("회원 삭제는 여전히 막힌다(탈퇴는 원본 파일 삭제 순서가 있어 서비스가 조율한다)")
    void userDeletionIsStillRestricted() {
        long user = insert("users", "(email, role) values ('c@test', 'USER')");
        insert("contracts", "(user_id, title) values (" + user + ", 't')");

        assertThatThrownBy(() -> jdbc.sql("delete from users where id = ?").param(user).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 계약 하나에 조항 2개와 분석·위험 결과·설명·인용·비교(전후 모두 이 계약)·비교 쌍을 한 세트씩 만든다. */
    private long contractWithEverything(long user) {
        long contract = insert("contracts", "(user_id, title) values (" + user + ", 't')");
        long clause1 = insert("clauses", "(contract_id, clause_no, text) values (" + contract + ", 1, '가')");
        long clause2 = insert("clauses", "(contract_id, clause_no, text) values (" + contract + ", 2, '나')");
        long analysis = insert("analyses", "(contract_id, status) values (" + contract + ", 'DONE')");
        insert("risk_results", "(analysis_id, clause_id, risk_level) values (" + analysis + ", " + clause1 + ", 'HIGH')");
        long explanation = insert("explanations", "(clause_id, text) values (" + clause2 + ", '설명')");
        insert("explanation_citations", "(explanation_id, source_type) values (" + explanation + ", 'LAW')");
        long comparison = insert("comparisons", "(user_id, before_contract_id, after_contract_id) values (" + user + ", " + contract + ", " + contract + ")");
        insert("comparison_pairs", "(comparison_id, change_type) values (" + comparison + ", 'SAME')");
        return contract;
    }

    private long insert(String table, String columnsAndValues) {
        return jdbc.sql("insert into " + table + " " + columnsAndValues + " returning id").query(Long.class).single();
    }

    private int count(String fromClause) {
        return jdbc.sql("select count(*) from " + fromClause).query(Integer.class).single();
    }

    private static void dropSchema(String url, String user, String password) {
        JdbcClient.create(new DriverManagerDataSource(url, user, password)).sql("drop schema if exists " + SCHEMA + " cascade").update();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
