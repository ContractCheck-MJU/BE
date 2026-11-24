package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.client.InferenceException;
import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.domain.AnalysisStatus;
import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.ClauseDraft;
import ac.mju.contractcheck.domain.ClauseRisk;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.RiskAssessment;
import ac.mju.contractcheck.domain.RiskLevel;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.repository.ClauseRepository;
import ac.mju.contractcheck.repository.ContractRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 실제 Postgres의 임시 스키마에 Flyway를 적용하고, 앱을 {@code ddl-auto=validate}로 띄워 분석 엔티티가 스키마와 맞는지,
 * pgvector에 조항 임베딩이 저장되는지, 분석 성공·실패 흐름이 실제 DB에서 이어지는지 확인한다.
 * {@code ./gradlew postgresTest}로만 돌고 운영 스키마(public)는 건드리지 않는다. 추론 서버 호출만 가짜다.
 */
@Tag("postgres")
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
class AnalysisPostgresTest {

    private static final String SCHEMA = "analysis_pg_test";
    private static final String URL = env("DB_URL", "jdbc:postgresql://localhost:5432/contractcheck");
    private static final String USER = env("DB_USERNAME", "contractcheck");
    private static final String PASSWORD = env("DB_PASSWORD", "");

    @BeforeAll
    static void migrate() {
        dropSchema();
        // public을 검색 경로에 함께 둬야 V1의 vector 타입을 찾는다. 기본 스키마(테이블이 만들어지는 곳)는 첫 번째다.
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

    @Autowired AnalysisService analyses;
    @Autowired CreditService credits;
    @Autowired AuthService auth;
    @Autowired ContractRepository contracts;
    @Autowired ClauseRepository clauseRepository;
    @Autowired JdbcClient jdbc;
    @MockitoBean ClauseRiskService riskService;
    @MockitoBean(name = "analysisExecutor") ThreadPoolTaskExecutor executor;

    private UserId owner;
    private Contract contract;
    private List<Clause> clauses;

    @BeforeEach
    void setUp() {
        doAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return null;
        }).when(executor).execute(any());
        String providerUserId = UUID.randomUUID().toString();
        owner = auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id();
        contract = contracts.save(new Contract(0, owner, "계약", "MONTHLY", "contracts/" + owner + "/k", "SPLIT", "", Instant.now(), null));
        clauses = clauseRepository.saveAll(contract.id(), owner, List.of(new ClauseDraft(1, "제1조", "면책 조항"), new ClauseDraft(2, "제2조", "정상 조항")));
    }

    private static float[] vector(float seed) {
        float[] values = new float[1024];
        java.util.Arrays.fill(values, seed);
        return values;
    }

    private ClauseRisk risk(Clause clause, RiskLevel level, float seed) {
        return new ClauseRisk(clause.id(), level == RiskLevel.SAFE ? null : "면책", 0.9123,
                new RiskAssessment(level, false, false), "mock-0.1", vector(seed), List.of());
    }

    @Test
    @DisplayName("분석이 끝나면 조항 임베딩(1024차원)이 pgvector에 저장되고 결과·점수가 실제 DB에 남는다")
    void successStoresEmbeddingsAndResults() {
        when(riskService.assess(any())).thenReturn(List.of(risk(clauses.get(0), RiskLevel.HIGH, 0.1f), risk(clauses.get(1), RiskLevel.SAFE, 0.2f)));

        Analysis accepted = analyses.start(owner, contract.id());

        assertThat(analyses.get(owner, accepted.id()).status()).isEqualTo(AnalysisStatus.ANALYZED);
        Integer dims = jdbc.sql("select vector_dims(embedding) from clauses where id = :id").param("id", clauses.get(0).id()).query(Integer.class).single();
        assertThat(dims).isEqualTo(1024);
        Long saved = jdbc.sql("select count(*) from clauses where contract_id = :id and embedding is not null").param("id", contract.id()).query(Long.class).single();
        assertThat(saved).isEqualTo(2);
        assertThat(analyses.results(owner, accepted.id())).hasSize(2);
        assertThat(analyses.results(owner, accepted.id()).get(0).probability()).isEqualTo(0.9123);
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-CRD-007 추론 서버가 실패하면 FAILED가 되고 이용권이 복구되며 임베딩은 저장되지 않는다")
    void failureRestoresCreditAndStoresNoEmbedding() {
        when(riskService.assess(any())).thenThrow(new InferenceException("분석 서버 점검 중"));

        Analysis accepted = analyses.start(owner, contract.id());

        assertThat(analyses.get(owner, accepted.id()).status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(2);
        Long saved = jdbc.sql("select count(*) from clauses where contract_id = :id and embedding is not null").param("id", contract.id()).query(Long.class).single();
        assertThat(saved).isZero();
    }

    @Test
    @DisplayName("계약을 지우면 분석과 조항별 결과가 cascade로 함께 지워진다")
    void deletingContractRemovesAnalysis() {
        when(riskService.assess(any())).thenReturn(List.of(risk(clauses.get(0), RiskLevel.HIGH, 0.1f), risk(clauses.get(1), RiskLevel.SAFE, 0.2f)));
        Analysis accepted = analyses.start(owner, contract.id());

        jdbc.sql("delete from contracts where id = :id").param("id", contract.id()).update();

        Long results = jdbc.sql("select count(*) from risk_results where analysis_id = :id").param("id", accepted.id()).query(Long.class).single();
        Long rows = jdbc.sql("select count(*) from analyses where id = :id").param("id", accepted.id()).query(Long.class).single();
        assertThat(results).isZero();
        assertThat(rows).isZero();
    }

    private static void dropSchema() {
        JdbcClient.create(new DriverManagerDataSource(URL, USER, PASSWORD)).sql("drop schema if exists " + SCHEMA + " cascade").update();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
