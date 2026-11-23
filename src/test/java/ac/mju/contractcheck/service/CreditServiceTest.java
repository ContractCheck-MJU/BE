package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.CreditPolicy;
import ac.mju.contractcheck.domain.LedgerRefType;
import ac.mju.contractcheck.domain.LedgerType;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.CreditInsufficientException;
import ac.mju.contractcheck.repository.CreditRepository;
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
        order.verify(credits).appendLedger(OWNER, LedgerType.GRANT, CreditPolicy.SIGNUP_FREE_CREDITS, null, null);
    }

    @Test
    @DisplayName("TC-CRD-002 차감: 무료부터 1회 줄인 잔여를 저장하고 USE 원장을 분석 ID와 함께 남긴다")
    void useDeductsFreeFirstAndRecordsLedger() {
        when(credits.findAccount(OWNER)).thenReturn(Optional.of(new CreditAccount(OWNER, 2, 3)));

        service.use(OWNER, 42L);

        InOrder order = inOrder(credits);
        order.verify(credits).saveAccount(new CreditAccount(OWNER, 1, 3));
        order.verify(credits).appendLedger(OWNER, LedgerType.USE, 1, LedgerRefType.ANALYSIS, 42L);
    }

    @Test
    @DisplayName("TC-CRD-003 무료가 없으면 유료를 줄인다")
    void useDeductsPaidWhenFreeIsEmpty() {
        when(credits.findAccount(OWNER)).thenReturn(Optional.of(new CreditAccount(OWNER, 0, 3)));

        service.use(OWNER, 1L);

        verify(credits).saveAccount(new CreditAccount(OWNER, 0, 2));
    }

    @Test
    @DisplayName("TC-CRD-004 이용권이 없으면 CREDIT_INSUFFICIENT이고 아무것도 저장하지 않는다")
    void insufficientSavesNothing() {
        when(credits.findAccount(OWNER)).thenReturn(Optional.of(new CreditAccount(OWNER, 0, 0)));

        assertThatThrownBy(() -> service.use(OWNER, 1L)).isInstanceOf(CreditInsufficientException.class);

        verify(credits, never()).saveAccount(any());
        verify(credits, never()).appendLedger(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
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
        verify(credits, times(1)).appendLedger(OWNER, LedgerType.USE, 1, LedgerRefType.ANALYSIS, 9L);
    }

    @Test
    @DisplayName("충돌이 계속되면 5번까지만 시도하고 IllegalStateException이다")
    void givesUpAfterMaxAttempts() {
        when(credits.findAccount(OWNER)).thenReturn(Optional.of(new CreditAccount(OWNER, 1, 0)));
        doThrow(new ObjectOptimisticLockingFailureException(CreditAccount.class, 7L)).when(credits).saveAccount(any());

        assertThatThrownBy(() -> service.use(OWNER, 1L)).isInstanceOf(IllegalStateException.class);

        verify(credits, times(5)).saveAccount(any());
        assertThat(true).isTrue();
    }
}
