package ac.mju.contractcheck.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ac.mju.contractcheck.domain.CreditAccount;
import ac.mju.contractcheck.domain.LedgerEntry;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.global.error.CreditInsufficientException;
import ac.mju.contractcheck.service.CreditService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * FR-CRD-02 이용권 차감. 지갑 잔액은 패키지 비공개 JPA 저장소로 직접 만든다(유료 이용권을 주는 경로는 결제 FR에서 생긴다).
 * 테스트마다 서로 다른 회원 식별자를 써서 데이터를 공유하지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class CreditConsumptionTest {

    private static final AtomicLong USER_IDS = new AtomicLong(9_000_000_000L);

    @Autowired
    CreditService credits;

    @Autowired
    CreditWalletJpaRepository wallets;

    @Test
    @DisplayName("TC-CRD-002 무료가 있으면 무료부터 차감하고 USE 원장 1건을 남긴다")
    void freeIsUsedFirst() {
        UserId owner = walletWith(1, 3);

        credits.use(owner, 101L);

        CreditAccount account = credits.me(owner);
        assertThat(account.freeRemaining()).isZero();
        assertThat(account.paidRemaining()).isEqualTo(3);
        List<LedgerEntry> ledger = credits.ledger(owner);
        assertThat(ledger).hasSize(1);
        assertThat(ledger.get(0).type()).isEqualTo("USE");
        assertThat(ledger.get(0).amount()).isEqualTo(1);
        assertThat(ledger.get(0).refId()).isEqualTo("101");
    }

    @Test
    @DisplayName("TC-CRD-003 무료를 다 쓰면 유료를 차감한다")
    void paidIsUsedAfterFree() {
        UserId owner = walletWith(0, 3);

        credits.use(owner, 102L);

        CreditAccount account = credits.me(owner);
        assertThat(account.freeRemaining()).isZero();
        assertThat(account.paidRemaining()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-CRD-004 이용권이 없으면 CREDIT_INSUFFICIENT를 일으키고 잔여·원장은 그대로 유지된다")
    void insufficientCreditsIsRejected() {
        UserId owner = walletWith(0, 0);

        assertThatThrownBy(() -> credits.use(owner, 103L))
                .isInstanceOf(CreditInsufficientException.class)
                .extracting(ex -> ((CreditInsufficientException) ex).code())
                .isEqualTo("CREDIT_INSUFFICIENT");

        assertThat(credits.me(owner).freeRemaining()).isZero();
        assertThat(credits.me(owner).paidRemaining()).isZero();
        assertThat(credits.ledger(owner)).isEmpty();
    }

    @Test
    @DisplayName("TC-CRD-005 잔여 1회에 동시 요청 2건이면 항상 1건만 성공하고 잔여는 음수가 되지 않는다")
    void concurrentUseNeverGoesNegative() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 30; round++) {
                UserId owner = walletWith(1, 0);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Boolean>> results = new ArrayList<>();
                for (long analysisId = 1; analysisId <= 2; analysisId++) {
                    long id = analysisId;
                    Callable<Boolean> task = () -> {
                        ready.countDown();
                        go.await();
                        try {
                            credits.use(owner, id);
                            return true;
                        } catch (CreditInsufficientException exception) {
                            return false;
                        }
                    };
                    results.add(executor.submit(task));
                }
                ready.await();
                go.countDown();

                long succeeded = 0;
                for (Future<Boolean> result : results) {
                    if (result.get()) {
                        succeeded++;
                    }
                }
                assertThat(succeeded).as("round %d", round).isEqualTo(1);
                assertThat(credits.me(owner).freeRemaining()).isZero();
                assertThat(credits.ledger(owner)).hasSize(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("TC-CRD-008 무료→유료 차감 후 스택 역순으로 복구하면 유료·무료가 원래 잔여로 돌아오고 같은 analysisId는 중복 복구되지 않는다")
    void restoreFollowsStackOrderAndIsIdempotent() {
        UserId owner = walletWith(1, 2);
        credits.use(owner, 201L); // FREE 차감 -> (0, 2)
        credits.use(owner, 202L); // PAID 차감 -> (0, 1)

        credits.restore(owner, 202L); // PAID 복구 -> (0, 2)
        credits.restore(owner, 202L); // 중복 복구 무시 -> (0, 2)
        assertThat(credits.me(owner).freeRemaining()).isZero();
        assertThat(credits.me(owner).paidRemaining()).isEqualTo(2);

        credits.restore(owner, 201L); // FREE 복구 -> (1, 2)
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(1);
        assertThat(credits.me(owner).paidRemaining()).isEqualTo(2);
        assertThat(credits.ledger(owner)).filteredOn(entry -> "RESTORE".equals(entry.type())).hasSize(2);
    }

    @Test
    @DisplayName("TC-CRD-008 같은 analysisId에 동시 복구 요청 2건이 들어와도 정확히 1회만 복구된다")
    void concurrentRestoreOnlyRestoresOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 20; round++) {
                UserId owner = walletWith(0, 1);
                credits.use(owner, 300L);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<?>> results = new ArrayList<>();
                for (int worker = 0; worker < 2; worker++) {
                    results.add(executor.submit(() -> {
                        ready.countDown();
                        try {
                            go.await();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                        credits.restore(owner, 300L);
                    }));
                }
                ready.await();
                go.countDown();
                for (Future<?> result : results) {
                    result.get();
                }

                assertThat(credits.me(owner).paidRemaining()).as("round %d", round).isEqualTo(1);
                assertThat(credits.ledger(owner)).filteredOn(entry -> "RESTORE".equals(entry.type())).hasSize(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private UserId walletWith(int free, int paid) {
        long userId = USER_IDS.incrementAndGet();
        wallets.save(new CreditWalletEntity(userId, free, paid));
        return new UserId(userId);
    }
}
