package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ac.mju.contractcheck.domain.Analysis;
import ac.mju.contractcheck.domain.AnalysisSummary;
import ac.mju.contractcheck.domain.AnalysisStatus;
import ac.mju.contractcheck.domain.ClauseDraft;
import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.domain.ContractState;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.repository.AnalysisRepository;
import ac.mju.contractcheck.repository.ClauseRepository;
import ac.mju.contractcheck.repository.ContractRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 서버가 비동기 작업 도중 죽어 멈춘 분석·계약을 정리하는 동작을 실제 서비스·저장소(H2)로 확인한다.
 * 실행기를 아무것도 하지 않게 바꿔 접수된 분석이 ANALYZING에 멈춘 상태를 그대로 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
class StuckJobRecoveryTest {

    @Autowired AnalysisService analyses;
    @Autowired ContractService contractService;
    @Autowired CreditService credits;
    @Autowired AuthService auth;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired ContractRepository contracts;
    @Autowired ClauseRepository clauseRepository;
    @MockitoBean(name = "analysisExecutor") ThreadPoolTaskExecutor executor;

    private UserId owner;
    private Contract contract;

    @BeforeEach
    void setUp() {
        doNothing().when(executor).execute(any());
        String providerUserId = UUID.randomUUID().toString();
        owner = auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id();
        contract = save(ContractState.SPLIT, Instant.now());
        clauseRepository.saveAll(contract.id(), owner, List.of(new ClauseDraft(1, "제1조", "조항")));
    }

    private Contract save(ContractState status, Instant createdAt) {
        return contracts.save(new Contract(0, owner, "계약", "MONTHLY", "contracts/" + owner + "/" + UUID.randomUUID(), status, "", createdAt, null));
    }

    private long restoreCount() {
        return credits.ledger(owner, 0, 100).items().stream().filter(entry -> entry.type().equals("RESTORE")).count();
    }

    @Test
    @DisplayName("TC-CRD-007 서버가 죽어 ANALYZING에 멈춘 분석은 FAILED가 되고 이용권이 복구되며 같은 계약을 다시 분석할 수 있다")
    void stuckAnalysisIsFailedAndCreditRestored() {
        Analysis stuck = analyses.start(owner, contract.id());
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(1);

        // 테스트끼리 H2를 공유해 다른 테스트가 남긴 멈춘 분석도 함께 정리되므로, 건수는 내 분석 기준으로만 확인한다.
        assertThat(analyses.failStuck(Instant.now().plusSeconds(1))).isPositive();
        assertThat(analyses.get(owner, stuck.id()).status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(2);
        assertThat(restoreCount()).isEqualTo(1);
        assertThat(analyses.start(owner, contract.id()).status()).isEqualTo(AnalysisStatus.ANALYZING);
    }

    @Test
    @DisplayName("기준 시각보다 새로 접수한 분석은 아직 처리 중일 수 있어 건드리지 않는다")
    void freshAnalysisIsLeftAlone() {
        Analysis fresh = analyses.start(owner, contract.id());

        int failed = analyses.failStuck(Instant.now().minus(Duration.ofMinutes(15)));

        assertThat(failed).isZero();
        assertThat(analyses.get(owner, fresh.id()).status()).isEqualTo(AnalysisStatus.ANALYZING);
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(1);
        assertThat(restoreCount()).isZero();
    }

    @Test
    @DisplayName("TC-CRD-008 정리를 두 번 돌려도 이용권은 한 번만 복구된다")
    void recoveryTwiceRestoresOnce() {
        analyses.start(owner, contract.id());

        analyses.failStuck(Instant.now().plusSeconds(1));
        assertThat(restoreCount()).isEqualTo(1);
        analyses.failStuck(Instant.now().plusSeconds(1));

        assertThat(restoreCount()).isEqualTo(1);
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 끝난 분석은 정리 대상이 아니다")
    void finishedAnalysisIsNotTouched() {
        Analysis accepted = analyses.start(owner, contract.id());
        // 다른 스레드가 정리 직전에 끝낸 상황: 판정이 끝난 분석(ANALYZED)으로 바꿔 둔다.
        analysisRepository.save(accepted.analyzed(new AnalysisSummary(100, "위험 0건, 주의 0건, 정상 1건"), "mock"));

        analyses.failStuck(Instant.now().plusSeconds(1));

        assertThat(analyses.get(owner, accepted.id()).status()).isEqualTo(AnalysisStatus.ANALYZED);
        assertThat(restoreCount()).isZero();
    }

    @Test
    @DisplayName("TC-UPL-007 서버가 죽어 UPLOADED·OCR에 멈춘 계약은 FAILED와 사유가 남고, 끝난 계약과 새 계약은 그대로다")
    void stuckContractsAreFailedWithReason() {
        Instant old = Instant.now().minus(Duration.ofHours(1));
        Contract uploaded = save(ContractState.UPLOADED, old);
        Contract ocr = save(ContractState.OCR, old);
        Contract split = save(ContractState.SPLIT, old);
        Contract failedAlready = contracts.save(save(ContractState.FAILED, old).withStatus(ContractState.FAILED, "원래 사유"));
        Contract fresh = save(ContractState.OCR, Instant.now());

        int failed = contractService.failStuck(Instant.now().minus(Duration.ofMinutes(15)));

        assertThat(failed).isGreaterThanOrEqualTo(2);
        assertThat(contractService.get(owner, uploaded.id()).status()).isEqualTo(ContractState.FAILED);
        assertThat(contractService.get(owner, uploaded.id()).errorMessage()).isEqualTo(ContractService.MESSAGE_INTERRUPTED);
        assertThat(contractService.get(owner, ocr.id()).status()).isEqualTo(ContractState.FAILED);
        assertThat(contractService.get(owner, split.id()).status()).isEqualTo(ContractState.SPLIT);
        assertThat(contractService.get(owner, failedAlready.id()).errorMessage()).isEqualTo("원래 사유");
        assertThat(contractService.get(owner, fresh.id()).status()).isEqualTo(ContractState.OCR);
    }

    @Test
    @DisplayName("분석 정리가 예외로 실패해도 계약서 정리는 계속한다")
    void contractCleanupContinuesWhenAnalysisCleanupFails() {
        AnalysisService analysisService = mock(AnalysisService.class);
        ContractService contractServiceMock = mock(ContractService.class);
        doThrow(new IllegalStateException("db down")).when(analysisService).failStuck(any());
        StuckJobRecovery recovery = new StuckJobRecovery(analysisService, contractServiceMock, Duration.ofMinutes(15));

        Instant now = Instant.parse("2026-11-24T10:00:00Z");
        recovery.recover(now);

        verify(contractServiceMock).failStuck(Instant.parse("2026-11-24T09:45:00Z"));
    }
}
