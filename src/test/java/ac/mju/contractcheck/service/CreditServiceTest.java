package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.CreditPolicy;
import ac.mju.contractcheck.domain.enums.CreditSource;
import ac.mju.contractcheck.domain.LedgerEntry;
import ac.mju.contractcheck.domain.enums.LedgerRefType;
import ac.mju.contractcheck.domain.enums.LedgerType;
import ac.mju.contractcheck.domain.PageSlice;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.ApiException;
import ac.mju.contractcheck.global.error.CreditInsufficientException;
import ac.mju.contractcheck.repository.CreditRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이용권 업무 판단(가입 무료 정책, 차감 순서, 재시도)은 service가 쥔다. 저장소는 읽고 쓰기만 하는 가짜(mock)로 두고
 * Spring·DB 없이 service가 어떤 순서로 저장소를 부르는지 확인한다.
 */
class CreditServiceTest {

    private static final UserId OWNER = new UserId(7);

    private final CreditRepository credits = mock(CreditRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private CreditService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new CreditService(credits, new TransactionTemplate(transactionManager));
    }

    @Test
    @DisplayName("TC-CRD-001 가입 지급: 정책(무료 2회)대로 지갑을 만들고 GRANT 원장을 남긴다")
    void grantSignupCreatesWalletAndGrantLedger() {
        service.grantSignup(OWNER);

        InOrder order = inOrder(credits);
        order.verify(credits).createAccount(new CreditAccount(OWNER, CreditPolicy.SIGNUP_FREE_CREDITS, 0));
        order.verify(credits).appendLedger(OWNER, LedgerType.GRANT, CreditPolicy.SIGNUP_FREE_CREDITS, null, null, CreditSource.FREE);
    }

    @Test
    @DisplayName("TC-CRD-002 차감: 무료부터 1회 줄인 잔여를 저장하고 USE 원장을 분석 ID·출처(FREE)와 함께 남긴다")
    void useDeductsFreeFirstAndRecordsLedger() {
        when(credits.findAccount(OWNER)).thenReturn(Optional.of(new CreditAccount(OWNER, 2, 3)));

        service.use(OWNER, 42L);

        InOrder order = inOrder(credits);
        order.verify(credits).saveAccount(new CreditAccount(OWNER, 1, 3));
        order.verify(credits).appendLedger(OWNER, LedgerType.USE, 1, LedgerRefType.ANALYSIS, 42L, CreditSource.FREE);
    }

    @Test
    @DisplayName("TC-CRD-003 무료가 없으면 유료를 줄이고 USE 원장에 출처(PAID)를 남긴다")
    void useDeductsPaidWhenFreeIsEmpty() {
        when(credits.findAccount(OWNER)).thenReturn(Optional.of(new CreditAccount(OWNER, 0, 3)));

        service.use(OWNER, 1L);

        verify(credits).saveAccount(new CreditAccount(OWNER, 0, 2));
        verify(credits).appendLedger(OWNER, LedgerType.USE, 1, LedgerRefType.ANALYSIS, 1L, CreditSource.PAID);
    }

    @Test
    @DisplayName("TC-CRD-004 이용권이 없으면 CREDIT_INSUFFICIENT이고 아무것도 저장하지 않는다")
    void insufficientSavesNothing() {
        when(credits.findAccount(OWNER)).thenReturn(Optional.of(new CreditAccount(OWNER, 0, 0)));

        assertThatThrownBy(() -> service.use(OWNER, 1L)).isInstanceOf(CreditInsufficientException.class);

        verify(credits, never()).saveAccount(any());
        verify(credits, never()).appendLedger(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("지갑이 없는 회원은 저장소가 아니라 service가 서버 오류(IllegalStateException)로 판단한다")
    void missingWalletIsDecidedByService() {
        when(credits.findAccount(OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.me(OWNER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.use(OWNER, 1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TC-CRD-005 낙관적 락 충돌이면 새 트랜잭션에서 지갑을 다시 읽어 재시도한다")
    void conflictIsRetriedInNewTransaction() {
        when(credits.findAccount(OWNER)).thenReturn(Optional.of(new CreditAccount(OWNER, 1, 0)));
        doThrow(new ObjectOptimisticLockingFailureException(CreditAccount.class, 7L))
                .doNothing()
                .when(credits).saveAccount(any());

        service.use(OWNER, 9L);

        verify(credits, times(2)).findAccount(OWNER);
        verify(transactionManager, times(2)).getTransaction(any());
        verify(credits, times(1)).appendLedger(OWNER, LedgerType.USE, 1, LedgerRefType.ANALYSIS, 9L, CreditSource.FREE);
    }

    @Test
    @DisplayName("충돌이 계속되면 5번까지만 시도하고 IllegalStateException이다")
    void givesUpAfterMaxAttempts() {
        when(credits.findAccount(OWNER)).thenReturn(Optional.of(new CreditAccount(OWNER, 1, 0)));
        doThrow(new ObjectOptimisticLockingFailureException(CreditAccount.class, 7L)).when(credits).saveAccount(any());

        assertThatThrownBy(() -> service.use(OWNER, 1L)).isInstanceOf(IllegalStateException.class);

        verify(credits, times(5)).saveAccount(any());
    }

    @Test
    @DisplayName("TC-CRD-008 복구: 해당 분석이 차감했던 출처(PAID·FREE)에 1회를 되돌리고 RESTORE 원장을 남긴다")
    void restoreReturnsCreditToOriginalSource() {
        when(credits.hasLedger(OWNER, LedgerType.RESTORE, LedgerRefType.ANALYSIS, 42L)).thenReturn(false);
        when(credits.hasLedger(OWNER, LedgerType.USE, LedgerRefType.ANALYSIS, 42L)).thenReturn(true);
        when(credits.findLedgerSource(OWNER, LedgerType.USE, LedgerRefType.ANALYSIS, 42L))
                .thenReturn(Optional.of(CreditSource.PAID));
        when(credits.findAccount(OWNER)).thenReturn(Optional.of(new CreditAccount(OWNER, 0, 2)));

        service.restore(OWNER, 42L);

        InOrder order = inOrder(credits);
        order.verify(credits).saveAccount(new CreditAccount(OWNER, 0, 3));
        order.verify(credits).appendLedger(OWNER, LedgerType.RESTORE, 1, LedgerRefType.ANALYSIS, 42L, CreditSource.PAID);
    }

    @Test
    @DisplayName("TC-CRD-008 복구: 같은 analysisId로 이미 복구했으면 중복 복구하지 않는다")
    void restoreIsIdempotentPerAnalysis() {
        when(credits.findAccount(OWNER)).thenReturn(Optional.of(new CreditAccount(OWNER, 1, 0)));
        when(credits.hasLedger(OWNER, LedgerType.RESTORE, LedgerRefType.ANALYSIS, 42L)).thenReturn(true);

        service.restore(OWNER, 42L);

        verify(credits, never()).saveAccount(any());
        verify(credits, never()).appendLedger(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-CRD-008 복구: 차감(USE) 이력이 없는 분석은 복구하지 않는다")
    void restoreWithoutPriorUseDoesNothing() {
        when(credits.findAccount(OWNER)).thenReturn(Optional.of(new CreditAccount(OWNER, 1, 0)));
        when(credits.hasLedger(OWNER, LedgerType.RESTORE, LedgerRefType.ANALYSIS, 42L)).thenReturn(false);
        when(credits.hasLedger(OWNER, LedgerType.USE, LedgerRefType.ANALYSIS, 42L)).thenReturn(false);

        service.restore(OWNER, 42L);

        verify(credits, never()).saveAccount(any());
        verify(credits, never()).appendLedger(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("FR-CRD-02 복구: 출처가 비어 있는 레거시 USE 원장은 스택 역순 기본값 PAID로 복구한다")
    void restoreFallsBackToPaidWhenLegacySourceIsMissing() {
        when(credits.hasLedger(OWNER, LedgerType.RESTORE, LedgerRefType.ANALYSIS, 50L)).thenReturn(false);
        when(credits.hasLedger(OWNER, LedgerType.USE, LedgerRefType.ANALYSIS, 50L)).thenReturn(true);
        when(credits.findLedgerSource(OWNER, LedgerType.USE, LedgerRefType.ANALYSIS, 50L)).thenReturn(Optional.empty());
        when(credits.findAccount(OWNER)).thenReturn(Optional.of(new CreditAccount(OWNER, 0, 1)));

        service.restore(OWNER, 50L);

        verify(credits).saveAccount(new CreditAccount(OWNER, 0, 2));
        verify(credits).appendLedger(OWNER, LedgerType.RESTORE, 1, LedgerRefType.ANALYSIS, 50L, CreditSource.PAID);
    }

    @Test
    @DisplayName("TC-CRD-008 복구 중 낙관적 락 충돌이 나면 새 트랜잭션에서 다시 확인해 재시도한다")
    void restoreRetriesOnOptimisticLockConflict() {
        when(credits.hasLedger(OWNER, LedgerType.RESTORE, LedgerRefType.ANALYSIS, 60L)).thenReturn(false);
        when(credits.hasLedger(OWNER, LedgerType.USE, LedgerRefType.ANALYSIS, 60L)).thenReturn(true);
        when(credits.findLedgerSource(OWNER, LedgerType.USE, LedgerRefType.ANALYSIS, 60L))
                .thenReturn(Optional.of(CreditSource.FREE));
        when(credits.findAccount(OWNER)).thenReturn(Optional.of(new CreditAccount(OWNER, 1, 0)));
        doThrow(new ObjectOptimisticLockingFailureException(CreditAccount.class, 7L))
                .doNothing()
                .when(credits).saveAccount(any());

        service.restore(OWNER, 60L);

        verify(credits, times(2)).findAccount(OWNER);
        verify(transactionManager, times(2)).getTransaction(any());
        verify(credits, times(1)).appendLedger(OWNER, LedgerType.RESTORE, 1, LedgerRefType.ANALYSIS, 60L, CreditSource.FREE);
    }

    @Test
    @DisplayName("FR-CRD-02 원장 조회는 page·size를 저장소에 그대로 전달해 결과를 돌려준다")
    void ledgerDelegatesPagingToRepository() {
        PageSlice<LedgerEntry> expected = new PageSlice<>(
                List.of(new LedgerEntry(OWNER, LedgerType.USE, 1, LedgerRefType.ANALYSIS, "42", "2026-11-24T00:00:00Z")), 5);
        when(credits.findLedgerPage(OWNER, 1, 10)).thenReturn(expected);

        PageSlice<LedgerEntry> actual = service.ledger(OWNER, 1, 10);

        assertThat(actual).isEqualTo(expected);
        verify(credits).findLedgerPage(OWNER, 1, 10);
    }

    @Test
    @DisplayName("FR-CRD-02 원장 조회에서 page가 음수이거나 size가 1 미만이면 400 VALIDATION_ERROR이고 저장소를 부르지 않는다")
    void ledgerRejectsInvalidPageOrSize() {
        for (int[] invalid : new int[][] {{-1, 20}, {0, 0}, {0, -5}}) {
            assertThatThrownBy(() -> service.ledger(OWNER, invalid[0], invalid[1]))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        assertThat(((ApiException) ex).status()).isEqualTo(400);
                        assertThat(((ApiException) ex).code()).isEqualTo("VALIDATION_ERROR");
                    });
        }
        verify(credits, never()).findLedgerPage(any(), anyInt(), anyInt());
    }
}
