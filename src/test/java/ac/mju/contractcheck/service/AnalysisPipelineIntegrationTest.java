package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.client.InferenceException;
import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.domain.enums.AnalysisStatus;
import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.ClauseDraft;
import ac.mju.contractcheck.domain.ClauseRisk;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.enums.ContractState;
import ac.mju.contractcheck.domain.LedgerEntry;
import ac.mju.contractcheck.domain.enums.LedgerType;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.RiskAssessment;
import ac.mju.contractcheck.domain.enums.RiskLevel;
import ac.mju.contractcheck.domain.RiskResult;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.global.error.CreditInsufficientException;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.repository.ClauseEmbeddingRepository;
import ac.mju.contractcheck.repository.ClauseRepository;
import ac.mju.contractcheck.repository.ContractRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 분석 접수부터 결과 조회·실패 복구까지를 실제 서비스·저장소(H2)로 확인한다.
 * 추론 서버 호출(ClauseRiskService)과 pgvector 쓰기(ClauseEmbeddingRepository)만 가짜이고,
 * 비동기 실행기는 호출한 스레드에서 바로 실행하도록 바꿔 결과가 결정적이다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnalysisPipelineIntegrationTest {

    @Autowired AnalysisService analyses;
    @Autowired CreditService credits;
    @Autowired AuthService auth;
    @Autowired ContractRepository contracts;
    @Autowired ClauseRepository clauseRepository;
    @Autowired JdbcClient jdbc;
    @MockitoBean ClauseRiskService riskService;
    @MockitoBean ClauseEmbeddingRepository embeddings;
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
        contract = contracts.save(new Contract(0, owner, "계약", "MONTHLY", "contracts/" + owner + "/k", ContractState.SPLIT, "", Instant.now(), null));
        clauses = clauseRepository.saveAll(contract.id(), owner, List.of(
                new ClauseDraft(1, "제1조", "임대인은 어떠한 책임도 지지 않는다."), new ClauseDraft(2, "제2조", "정상 조항")));
    }

    private ClauseRisk risk(Clause clause, RiskLevel level) {
        return new ClauseRisk(clause.id(), level == RiskLevel.SAFE ? null : "면책", 0.9,
                new RiskAssessment(level, false, false), "mock-0.1", new float[] {0.1f}, List.of());
    }

    @Test
    @DisplayName("계약 상태는 도메인에서 ContractState지만 DB에는 이름 그대로의 문자열로 저장되고 같은 값으로 다시 읽힌다")
    void contractStateIsStoredByName() {
        String stored = jdbc.sql("select status from contracts where id = :id").param("id", contract.id()).query(String.class).single();

        assertThat(stored).isEqualTo("SPLIT");
        assertThat(contracts.findOwned(contract.id(), owner).orElseThrow().status()).isEqualTo(ContractState.SPLIT);
    }

    @Test
    @DisplayName("TC-CRD-002·TC-ANL-001 분석을 접수하면 무료가 1회 줄고 USE 원장이 남으며 결과가 저장되어 조회된다")
    void successfulAnalysisDeductsOnceAndStoresResults() {
        when(riskService.assess(any())).thenReturn(List.of(risk(clauses.get(0), RiskLevel.HIGH), risk(clauses.get(1), RiskLevel.SAFE)));

        Analysis accepted = analyses.start(owner, contract.id());

        assertThat(credits.me(owner).freeRemaining()).isEqualTo(1);
        List<LedgerEntry> ledger = credits.ledger(owner, 0, 100).items();
        assertThat(ledger).extracting(LedgerEntry::type).containsExactlyInAnyOrder(LedgerType.GRANT, LedgerType.USE);
        Analysis done = analyses.get(owner, accepted.id());
        assertThat(done.status()).isEqualTo(AnalysisStatus.ANALYZED);
        assertThat(done.overallScore()).isEqualTo(50.0);
        assertThat(done.summary()).isEqualTo("위험 1건, 주의 0건, 정상 1건");
        assertThat(done.modelVersion()).isEqualTo("mock-0.1");
        assertThat(analyses.results(owner, accepted.id())).containsExactly(
                new RiskResult(clauses.get(0).id(), true, RiskLevel.HIGH, "면책", 0.9),
                new RiskResult(clauses.get(1).id(), false, RiskLevel.SAFE, null, 0.9));
        verify(embeddings).saveAll(any());
    }

    @Test
    @DisplayName("TC-CRD-007·TC-ANL-016 추론 서버가 실패하면 FAILED가 되고 이용권이 복구되며 USE와 RESTORE가 같은 분석을 가리킨다")
    void failedAnalysisRestoresCredit() {
        when(riskService.assess(any())).thenThrow(new InferenceException("분석 서버 점검 중"));

        Analysis accepted = analyses.start(owner, contract.id());

        assertThat(analyses.get(owner, accepted.id()).status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analyses.results(owner, accepted.id())).isEmpty();
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(2);
        List<LedgerEntry> ledger = credits.ledger(owner, 0, 100).items();
        assertThat(ledger).filteredOn(entry -> entry.type() == LedgerType.USE).singleElement()
                .satisfies(entry -> assertThat(entry.refId()).isEqualTo(String.valueOf(accepted.id())));
        assertThat(ledger).filteredOn(entry -> entry.type() == LedgerType.RESTORE).singleElement()
                .satisfies(entry -> assertThat(entry.refId()).isEqualTo(String.valueOf(accepted.id())));
    }

    @Test
    @DisplayName("TC-CRD-008 같은 분석을 두 번 복구해도 RESTORE는 1건이고 잔여는 한 번만 늘어난다")
    void restoreIsIdempotent() {
        when(riskService.assess(any())).thenThrow(new InferenceException("장애"));
        Analysis accepted = analyses.start(owner, contract.id());

        credits.restore(owner, accepted.id());
        credits.restore(owner, accepted.id());

        assertThat(credits.ledger(owner, 0, 100).items()).filteredOn(entry -> entry.type() == LedgerType.RESTORE).hasSize(1);
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-CRD-006 판정 중인 분석이 있으면 같은 계약의 두 번째 요청은 409이고 차감은 1회뿐이다")
    void secondRequestWhileAnalyzingIsRejected() {
        doNothing().when(executor).execute(any()); // 판정이 끝나지 않은 상태로 둔다.

        analyses.start(owner, contract.id());

        assertThatThrownBy(() -> analyses.start(owner, contract.id()))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo("ANALYSIS_IN_PROGRESS"));
        assertThat(credits.ledger(owner, 0, 100).items()).filteredOn(entry -> entry.type() == LedgerType.USE).hasSize(1);
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-CRD-004 이용권이 없으면 402이고 분석 행이 생기지 않으며 차감 기록도 없다")
    void noCreditCreatesNothing() {
        credits.use(owner, 9001L);
        credits.use(owner, 9002L);

        assertThatThrownBy(() -> analyses.start(owner, contract.id())).isInstanceOf(CreditInsufficientException.class);

        assertThat(credits.ledger(owner, 0, 100).items()).filteredOn(entry -> entry.type() == LedgerType.USE).hasSize(2);
        Long rows = jdbc.sql("select count(*) from analyses where contract_id = :id").param("id", contract.id()).query(Long.class).single();
        assertThat(rows).isZero();
    }

    @Test
    @DisplayName("TC-ANL-019 남의 분석 결과는 404 ANALYSIS_NOT_FOUND로 존재를 숨긴다")
    void otherUsersAnalysisIsHidden() {
        when(riskService.assess(any())).thenReturn(List.of(risk(clauses.get(0), RiskLevel.SAFE), risk(clauses.get(1), RiskLevel.SAFE)));
        Analysis accepted = analyses.start(owner, contract.id());
        String providerUserId = UUID.randomUUID().toString();
        UserId other = auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "다른회원")).id();

        assertThatThrownBy(() -> analyses.get(other, accepted.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> analyses.results(other, accepted.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("조항 분할이 끝나지 않은 계약은 409 CONTRACT_NOT_READY이고 이용권은 그대로다")
    void unsplitContractIsRejected() {
        Contract uploaded = contracts.save(new Contract(0, owner, "계약2", "JEONSE", "contracts/" + owner + "/k2", ContractState.UPLOADED, "", Instant.now(), null));

        assertThatThrownBy(() -> analyses.start(owner, uploaded.id()))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo("CONTRACT_NOT_READY"));
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(2);
    }
}
