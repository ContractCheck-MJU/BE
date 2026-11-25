package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.client.PaymentGatewayClient;
import ac.mju.contractcheck.domain.GatewayPayment;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.Payment;
import ac.mju.contractcheck.domain.Product;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.domain.enums.PaymentStatus;
import ac.mju.contractcheck.repository.PaymentRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 웹훅은 서명 검증(FR-PAY-03) 전까지 결제 상태를 바꾸지 않는다. 인증 없는 경로라 본문만 믿고 DB를 고치면 안 된다. */
@SpringBootTest
@ActiveProfiles("test")
class PaymentWebhookTest {

    @Autowired PaymentService payments;
    @Autowired PaymentRepository paymentRepository;
    @Autowired AuthService auth;
    @MockitoBean PaymentGatewayClient gateway;

    @Test
    @DisplayName("FR-PAY-03 검증 없는 웹훅은 승인된 결제의 상태를 바꾸지 못한다")
    void webhookDoesNotChangeStatusBeforeVerification() {
        String providerUserId = UUID.randomUUID().toString();
        UserId owner = auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id();
        Payment order = payments.order(owner, paymentRepository.saveProduct(new Product(0, "분석 5회", 5, 4900, true)).id());
        String paymentKey = "pay_key_" + UUID.randomUUID();
        when(gateway.confirm(paymentKey, order.orderId(), 4900)).thenReturn(new GatewayPayment(
                paymentKey, order.orderId(), "DONE", "카드", 4900, Instant.parse("2026-11-25T01:00:05Z"), "{\"status\":\"DONE\"}"));
        payments.confirm(owner, paymentKey, order.orderId(), 4900);

        payments.webhook(paymentKey, "CANCELED");

        assertThat(paymentRepository.findOwned(order.id(), owner).orElseThrow().status()).isEqualTo(PaymentStatus.DONE);
    }
}
