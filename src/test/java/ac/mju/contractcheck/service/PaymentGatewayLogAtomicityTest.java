package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.client.PaymentGatewayClient;
import ac.mju.contractcheck.client.PaymentRejectedException;
import ac.mju.contractcheck.domain.GatewayPayment;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.Payment;
import ac.mju.contractcheck.domain.Product;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.domain.enums.PaymentStatus;
import ac.mju.contractcheck.repository.PaymentRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 대행사 호출 이력 INSERT는 상태 전이·이용권 지급과 한 트랜잭션이다. 이력이 실패하면 나머지도 반영되지 않아야 한다. */
@SpringBootTest
@ActiveProfiles("test")
class PaymentGatewayLogAtomicityTest {

    @Autowired PaymentService payments;
    @Autowired CreditService credits;
    @Autowired AuthService auth;
    @MockitoSpyBean PaymentRepository paymentRepository;
    @MockitoBean PaymentGatewayClient gateway;

    private final String paymentKey = "pay_key_" + UUID.randomUUID();
    private UserId owner;
    private Payment order;

    @BeforeEach
    void setUp() {
        String providerUserId = UUID.randomUUID().toString();
        owner = auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id();
        order = payments.order(owner, paymentRepository.saveProduct(new Product(0, "분석 5회", 5, 4900, true)).id());
        doThrow(new IllegalStateException("이력 저장 실패"))
                .when(paymentRepository).appendGatewayLog(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("FR-PAY-02 승인 성공 이력 저장이 실패하면 DONE 저장과 이용권 지급도 함께 롤백된다")
    void successIsRolledBackWithLog() {
        when(gateway.confirm(paymentKey, order.orderId(), 4900)).thenReturn(new GatewayPayment(
                paymentKey, order.orderId(), "DONE", "카드", 4900, Instant.parse("2026-11-25T01:00:05Z"), "{\"status\":\"DONE\"}"));

        assertThatThrownBy(() -> payments.confirm(owner, paymentKey, order.orderId(), 4900)).hasMessageContaining("이력 저장 실패");

        assertThat(paymentRepository.findOwned(order.id(), owner).orElseThrow().status()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(credits.me(owner).paidRemaining()).isZero();
    }

    @Test
    @DisplayName("FR-PAY-02 거절 이력 저장이 실패하면 FAILED 전이도 함께 롤백된다")
    void rejectionIsRolledBackWithLog() {
        when(gateway.confirm(paymentKey, order.orderId(), 4900)).thenThrow(new PaymentRejectedException("REJECT_CARD_COMPANY", "거절"));

        assertThatThrownBy(() -> payments.confirm(owner, paymentKey, order.orderId(), 4900)).hasMessageContaining("이력 저장 실패");

        assertThat(paymentRepository.findOwned(order.id(), owner).orElseThrow().status()).isEqualTo(PaymentStatus.IN_PROGRESS);
    }
}
