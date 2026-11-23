package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;

import ac.mju.contractcheck.client.HttpInferenceClient;
import ac.mju.contractcheck.client.InferenceEmbeddingModel;
import ac.mju.contractcheck.config.InferenceConfig;
import ac.mju.contractcheck.config.InferenceProperties;
import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.ClauseRisk;
import ac.mju.contractcheck.domain.RiskLevel;
import ac.mju.contractcheck.domain.RiskLevelResolver;
import ac.mju.contractcheck.domain.RiskPolicy;
import ac.mju.contractcheck.repository.EvidenceRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 실제 추론 서버(FastAPI)와 로컬 Postgres(pgvector)로 위험 판정 전체 경로를 확인한다. 실제 외부 서비스를 쓰므로
 * {@code ./gradlew test}와 CI에서는 제외되고 {@code ./gradlew inferenceTest}로만 돈다.
 *
 * <p>준비: DATA/risk-classification에서 {@code python -m uvicorn api.mock_inference:app --port 8000}으로 모의 서버를 띄우고
 * (분류는 키워드 규칙의 가짜 값, 임베딩은 실제 BGE-M3), docker compose의 postgres가 떠 있어야 하며
 * {@code scripts/ingest.sh}로 법령·판례가 적재돼 있어야 한다. 접속 정보는 환경변수(.env)다.
 */
@Tag("inference")
class ClauseRiskInferenceIntegrationTest {

    private static ClauseRiskService service;

    @BeforeAll
    static void connect() {
        InferenceProperties properties = new InferenceProperties(
                env("INFERENCE_BASE_URL", "http://localhost:8000"), env("INFERENCE_API_KEY", "dev-internal-key"),
                Duration.ofSeconds(60), 1, Duration.ZERO, 64);
        HttpInferenceClient client = new HttpInferenceClient(properties, new InferenceConfig().inferenceRestClient(properties));
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                env("DB_URL", "jdbc:postgresql://localhost:5432/contractcheck"), env("DB_USERNAME", "contractcheck"), env("DB_PASSWORD", ""));
        EvidenceRetriever retriever = new EvidenceRetriever(new InferenceEmbeddingModel(client), new EvidenceRepository(JdbcClient.create(dataSource)));
        service = new ClauseRiskService(client, new InferenceEmbeddingModel(client), retriever, new RiskLevelResolver(new RiskPolicy(0.6, 0.5)));
    }

    @Test
    @DisplayName("TC-ANL-001·006·007·013·015 실제 서버: 위험 조항은 근거와 함께 HIGH, 정상 조항은 SAFE로 판정된다")
    void assessesContractClausesEndToEnd() {
        Map<Long, String> texts = Map.of(
                1L, "임차인은 계약 만료 시 계약갱신요구권을 행사하지 않기로 하며, 이에 대해 이의를 제기하지 않는다.",
                6L, "임차인이 월세를 1회라도 연체하면 임대인은 최고 없이 즉시 계약을 해지할 수 있다.",
                7L, "건물 하자·누수로 인해 임차인에게 발생한 손해에 대해 임대인은 어떠한 책임도 지지 않는다.",
                13L, "임대인은 계약 존속 중 주택을 사용·수익에 필요한 상태로 유지하여야 하고, 임차인은 임대인의 동의 없이 주택의 구조를 변경하지 못한다.",
                15L, "임차인은 임대인의 사전 동의 없이 반려동물을 사육하지 않는다.");
        List<Clause> clauses = texts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new Clause(e.getKey(), 1L, "1", e.getKey().intValue(), "제" + e.getKey() + "조", e.getValue(), "")).toList();

        Map<Long, ClauseRisk> risks = service.assess(clauses).stream().collect(Collectors.toMap(ClauseRisk::clauseId, Function.identity()));

        for (long id : List.of(1L, 6L, 7L)) {
            ClauseRisk risk = risks.get(id);
            assertThat(risk.assessment().level()).as("조항 %d", id).isEqualTo(RiskLevel.HIGH);
            assertThat(risk.evidence()).as("조항 %d 근거", id).isNotEmpty();
            assertThat(risk.evidence().get(0).similarity()).isGreaterThanOrEqualTo(0.5);
            assertThat(risk.embedding()).hasSize(1024);
        }
        assertThat(risks.get(6L).evidence().get(0).title()).contains("민법 제640조");
        assertThat(risks.get(13L).assessment().level()).isEqualTo(RiskLevel.SAFE);
        assertThat(risks.get(15L).assessment().level()).isEqualTo(RiskLevel.SAFE);
        assertThat(risks.get(13L).evidence()).isEmpty();
    }

    @Test
    @DisplayName("TC-ANL-017 실제 서버: 위험 확률 0.52 조항은 CAUTION + lowConfidence다")
    void lowConfidenceClauseIsCaution() {
        Clause clause = new Clause(12L, 1L, "1", 12, "제12조", "전구·수도꼭지 패킹 등 간단한 소모품 교체는 임차인이 부담한다.", "");

        ClauseRisk risk = service.assess(List.of(clause)).get(0);

        assertThat(risk.assessment().level()).isEqualTo(RiskLevel.CAUTION);
        assertThat(risk.assessment().lowConfidence()).isTrue();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
