package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.client.PaymentGatewayClient;
import ac.mju.contractcheck.client.PaymentGatewayException;
import ac.mju.contractcheck.client.PaymentGatewayTimeoutException;
import ac.mju.contractcheck.client.PaymentRejectedException;
import ac.mju.contractcheck.domain.GatewayPayment;
import ac.mju.contractcheck.domain.LedgerEntry;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.Payment;
import ac.mju.contractcheck.domain.Product;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.domain.enums.CreditSource;
import ac.mju.contractcheck.domain.enums.LedgerRefType;
import ac.mju.contractcheck.domain.enums.LedgerType;
import ac.mju.contractcheck.domain.enums.PaymentStatus;
import ac.mju.contractcheck.global.error.ApiException;
import ac.mju.contractcheck.global.error.ConflictException;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.repository.PaymentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * FR-PAY-02 결제 승인을 실제 서비스·저장소(H2)로 확인한다. 토스 호출(PaymentGatewayClient)만 가짜다.
 * 금액 검증·소유자 확인·중복 승인 차단은 토스를 부르기 전에 끝나야 하고, 지급은 승인 성공 때 한 번만 일어나야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentConfirmTest {

    @Autowired PaymentService payments;
    @Autowired PaymentRepository paymentRepository;
    @Autowired CreditService credits;
    @Autowired AuthService auth;
    @Autowired JdbcClient jdbc;
    @MockitoBean PaymentGatewayClient gateway;

    /** payments.payment_key는 유일하고 테스트끼리 DB를 공유하므로 테스트마다 새 값을 쓴다. */
    private final String paymentKey = "pay_key_" + UUID.randomUUID();
    private UserId owner;
    private Payment order;

    @BeforeEach
    void setUp() {
        owner = signUp();
        Product product = paymentRepository.saveProduct(new Product(0, "분석 5회", 5, 4900, true));
        order = payments.order(owner, product.id());
    }

    private UserId signUp() {
        String providerUserId = UUID.randomUUID().toString();
        return auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id();
    }

    private GatewayPayment approved(Payment order, String status, int totalAmount) {
        return new GatewayPayment(paymentKey, order.orderId(), status, "카드", totalAmount,
                Instant.parse("2026-11-25T01:00:05Z"), "{\"status\":\"" + status + "\",\"card\":{\"number\":\"4330****\"}}");
    }

    private long grantCount() {
        return credits.ledger(owner, 0, 100).items().stream()
                .filter(entry -> entry.type() == LedgerType.GRANT && entry.refType() == LedgerRefType.PAYMENT).count();
    }

    @Test
    @DisplayName("TC-PAY-003 승인에 성공하면 DONE이 되고 유료 이용권이 상품 횟수만큼 늘며 GRANT 원장이 1건 남는다")
    void confirmGrantsPaidCredits() {
        when(gateway.confirm(paymentKey, order.orderId(), 4900)).thenReturn(approved(order, "DONE", 4900));

        Payment done = payments.confirm(owner, paymentKey, order.orderId(), 4900);

        assertThat(done.id()).isEqualTo(order.id());
        assertThat(done.status()).isEqualTo(PaymentStatus.DONE);
        assertThat(done.creditsAdded()).isEqualTo(5);
        assertThat(done.paymentKey()).isEqualTo(paymentKey);
        assertThat(done.method()).isEqualTo("카드");
        assertThat(done.approvedAt()).isEqualTo(Instant.parse("2026-11-25T01:00:05Z"));
        assertThat(credits.me(owner).paidRemaining()).isEqualTo(5);
        assertThat(credits.me(owner).freeRemaining()).isEqualTo(2);
        List<LedgerEntry> grants = credits.ledger(owner, 0, 100).items().stream()
                .filter(entry -> entry.refType() == LedgerRefType.PAYMENT).toList();
        assertThat(grants).singleElement().satisfies(entry -> {
            assertThat(entry.type()).isEqualTo(LedgerType.GRANT);
            assertThat(entry.amount()).isEqualTo(5);
            assertThat(entry.refId()).isEqualTo(String.valueOf(order.id()));
        });
        assertThat(jdbc.sql("select credit_source from credit_ledger where user_id = :id and ref_type = 'PAYMENT'")
                .param("id", owner.value()).query(String.class).single()).isEqualTo(CreditSource.PAID.name());
        assertThat(jdbc.sql("select raw from payments where id = :id").param("id", order.id()).query(String.class).single())
                .as("토스 응답 원문을 저장한다").contains("DONE");
    }

    @Test
    @DisplayName("TC-PAY-006 같은 결제로 이용권 지급을 두 번 불러도 GRANT는 1건이고 잔여는 한 번만 늘어난다")
    void grantPaidOnceIsIdempotent() {
        credits.grantPaidOnce(owner, order.id(), 5);
        credits.grantPaidOnce(owner, order.id(), 5);

        assertThat(credits.me(owner).paidRemaining()).isEqualTo(5);
        assertThat(grantCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-PAY-004 금액이 주문과 다르면 400 AMOUNT_MISMATCH이고 토스를 부르지 않으며 지급하지 않는다")
    void amountMismatchIsRejectedBeforeGateway() {
        assertThatThrownBy(() -> payments.confirm(owner, paymentKey, order.orderId(), 100))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(400);
                    assertThat(exception.code()).isEqualTo("AMOUNT_MISMATCH");
                });

        verify(gateway, never()).confirm(anyString(), anyString(), anyInt());
        assertThat(credits.me(owner).paidRemaining()).isZero();
        assertThat(paymentRepository.findOwned(order.id(), owner).orElseThrow().status()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("TC-PAY-005 타인의 주문이나 없는 주문은 404 ORDER_NOT_FOUND이고 토스를 부르지 않는다")
    void otherUsersOrderIsNotFound() {
        UserId other = signUp();

        assertThatThrownBy(() -> payments.confirm(other, paymentKey, order.orderId(), 4900))
                .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("ORDER_NOT_FOUND");
        assertThatThrownBy(() -> payments.confirm(owner, paymentKey, "no-such-order", 4900))
                .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("ORDER_NOT_FOUND");

        verify(gateway, never()).confirm(anyString(), anyString(), anyInt());
        assertThat(credits.me(other).paidRemaining()).isZero();
    }

    @Test
    @DisplayName("TC-PAY-006 이미 승인된 주문을 다시 승인하면 409 ALREADY_CONFIRMED이고 이용권은 한 번만 지급된다")
    void secondConfirmIsConflict() {
        when(gateway.confirm(paymentKey, order.orderId(), 4900)).thenReturn(approved(order, "DONE", 4900));
        payments.confirm(owner, paymentKey, order.orderId(), 4900);

        assertThatThrownBy(() -> payments.confirm(owner, paymentKey, order.orderId(), 4900))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("ALREADY_CONFIRMED");

        verify(gateway, times(1)).confirm(anyString(), anyString(), anyInt());
        assertThat(credits.me(owner).paidRemaining()).isEqualTo(5);
        assertThat(grantCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-PAY-006 같은 주문을 동시에 승인하면 한 요청만 토스를 부르고 나머지는 409이며 지급은 1회뿐이다")
    void concurrentConfirmsGrantOnce() throws Exception {
        CountDownLatch gatewayEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(gateway.confirm(anyString(), anyString(), anyInt())).thenAnswer(call -> {
            gatewayEntered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return approved(order, "DONE", 4900);
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Payment> first = pool.submit(() -> payments.confirm(owner, paymentKey, order.orderId(), 4900));
            assertThat(gatewayEntered.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Payment> second = pool.submit(() -> payments.confirm(owner, paymentKey, order.orderId(), 4900));

            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(ConflictException.class);
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo(PaymentStatus.DONE);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        verify(gateway, times(1)).confirm(anyString(), anyString(), anyInt());
        assertThat(credits.me(owner).paidRemaining()).isEqualTo(5);
        assertThat(grantCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-PAY-02 토스가 승인을 거절하면 422가 나가고 주문은 FAILED이며 지급하지 않는다")
    void rejectionFailsTheOrder() {
        when(gateway.confirm(anyString(), anyString(), anyInt())).thenThrow(new PaymentRejectedException("REJECT_CARD_COMPANY", "거절"));

        assertThatThrownBy(() -> payments.confirm(owner, paymentKey, order.orderId(), 4900)).isInstanceOf(PaymentRejectedException.class);

        assertThat(paymentRepository.findOwned(order.id(), owner).orElseThrow().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(credits.me(owner).paidRemaining()).isZero();
        assertThat(grantCount()).isZero();
    }

    @Test
    @DisplayName("FR-PAY-02 토스 장애·시간 초과로 결과를 알 수 없으면 주문은 READY로 돌아가 다시 승인할 수 있고 지급하지 않는다")
    void gatewayFailureKeepsOrderRetryable() {
        when(gateway.confirm(anyString(), anyString(), anyInt()))
                .thenThrow(new PaymentGatewayTimeoutException("시간 초과", null))
                .thenThrow(new PaymentGatewayException("연결 실패"))
                .thenReturn(approved(order, "DONE", 4900));

        assertThatThrownBy(() -> payments.confirm(owner, paymentKey, order.orderId(), 4900)).isInstanceOf(PaymentGatewayTimeoutException.class);
        assertThatThrownBy(() -> payments.confirm(owner, paymentKey, order.orderId(), 4900)).isInstanceOf(PaymentGatewayException.class);
        assertThat(paymentRepository.findOwned(order.id(), owner).orElseThrow().status()).isEqualTo(PaymentStatus.READY);
        assertThat(credits.me(owner).paidRemaining()).isZero();

        assertThat(payments.confirm(owner, paymentKey, order.orderId(), 4900).status()).isEqualTo(PaymentStatus.DONE);
        assertThat(credits.me(owner).paidRemaining()).isEqualTo(5);
    }

    @Test
    @DisplayName("FR-PAY-02 토스 응답이 DONE이 아니거나 금액이 다르면 지급하지 않는다")
    void unexpectedGatewayResultIsNotGranted() {
        when(gateway.confirm(anyString(), anyString(), anyInt()))
                .thenReturn(approved(order, "WAITING_FOR_DEPOSIT", 4900))
                .thenReturn(approved(order, "DONE", 100));

        assertThatThrownBy(() -> payments.confirm(owner, paymentKey, order.orderId(), 4900)).isInstanceOf(PaymentGatewayException.class);
        assertThat(paymentRepository.findOwned(order.id(), owner).orElseThrow().status()).isEqualTo(PaymentStatus.READY);
        assertThatThrownBy(() -> payments.confirm(owner, paymentKey, order.orderId(), 4900)).isInstanceOf(PaymentGatewayException.class);
        assertThat(paymentRepository.findOwned(order.id(), owner).orElseThrow().status()).isEqualTo(PaymentStatus.FAILED);

        assertThat(credits.me(owner).paidRemaining()).isZero();
        assertThat(grantCount()).isZero();
    }

    @Test
    @DisplayName("FR-PAY-02 paymentKey·orderId가 비었거나 금액이 0 이하면 400 VALIDATION_ERROR이고 토스를 부르지 않는다")
    void invalidInputIsRejected() {
        for (Object[] input : new Object[][] {{"", order.orderId(), 4900}, {paymentKey, " ", 4900}, {null, order.orderId(), 4900}, {paymentKey, order.orderId(), 0}}) {
            assertThatThrownBy(() -> payments.confirm(owner, (String) input[0], (String) input[1], (int) input[2]))
                    .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.code()).isEqualTo("VALIDATION_ERROR"));
        }

        verify(gateway, never()).confirm(any(), any(), eq(0));
        verify(gateway, never()).confirm(anyString(), anyString(), anyInt());
    }
}
